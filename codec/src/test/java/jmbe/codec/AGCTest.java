package jmbe.codec;

import jmbe.codec.ambe.AMBEModelParameters;
import org.junit.Test;

import static org.junit.Assert.*;

public class AGCTest
{
    private static final int L = 20;
    private static final float F0 = 0.1f;

    private static AMBEModelParameters frame(boolean voiced, float fundamental)
    {
        AMBEModelParameters parameters = new AMBEModelParameters();
        parameters.setL(L);
        parameters.setFundamentalFrequency(fundamental);
        boolean[] decisions = new boolean[L + 1];
        for(int l = 1; l <= L; l++)
        {
            decisions[l] = voiced;
        }
        parameters.setVoicingDecisions(decisions);
        return parameters;
    }

    private static AMBEModelParameters speech()
    {
        return frame(true, F0);
    }

    private static AMBEModelParameters unvoiced()
    {
        return frame(false, F0);
    }

    /**
     * Sine frame with the specified RMS level in dBFS.
     */
    private static float[] tone(double levelDb)
    {
        double amplitude = Short.MAX_VALUE * Math.pow(10.0, levelDb / 20.0) * Math.sqrt(2.0);
        float[] samples = new float[160];
        for(int x = 0; x < samples.length; x++)
        {
            samples[x] = (float)(amplitude * Math.sin(2.0 * Math.PI * 500.0 * x / 8000.0));
        }
        return samples;
    }

    private static double levelDb(float[] samples)
    {
        double sum = 0.0;
        for(float sample : samples)
        {
            sum += sample * sample;
        }
        return 20.0 * Math.log10(Math.sqrt(sum / samples.length) / Short.MAX_VALUE);
    }

    private static float[] process(AGC agc, double levelDb, MBEModelParameters parameters)
    {
        float[] samples = tone(levelDb);
        agc.process(samples, parameters);
        return samples;
    }

    /**
     * Voiced speech at the level, with a short pause after every 10 frames like the gaps between syllables.
     */
    private static void talk(AGC agc, double levelDb, int frames)
    {
        for(int x = 0; x < frames; x++)
        {
            process(agc, x % 13 < 10 ? levelDb : -60.0, x % 13 < 10 ? speech() : unvoiced());
        }
    }

    @Test
    public void noiseBeforeSpeechIsNotAmplified()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 100; x++)
        {
            process(agc, -45.0, unvoiced());
        }
        assertEquals(1.0f, agc.getAppliedGain(), 0.0001f);
        assertFalse(agc.isSpeech());
    }

    @Test
    public void quietSpeechConvergesQuicklyToTarget()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 20; x++)
        {
            process(agc, -60.0, unvoiced());
        }

        float[] output = null;
        for(int x = 0; x < 6; x++)
        {
            output = process(agc, -40.0, speech());
        }

        assertTrue(agc.isSpeech());
        assertEquals(10.0f, agc.getGain(), 0.1f);
        assertEquals(-20.0, levelDb(output), 0.2);
    }

    @Test
    public void gainIsHeldDuringPauses()
    {
        AGC agc = new AGC();
        talk(agc, -40.0, 20);
        float gain = agc.getGain();

        for(int x = 0; x < 100; x++)
        {
            process(agc, -55.0, unvoiced());
        }

        assertEquals(gain, agc.getGain(), 0.0001f);
        assertEquals(gain, agc.getAppliedGain(), 0.0001f);
    }

    @Test
    public void repeatedAndErroredFramesDoNotChangeGain()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 20; x++)
        {
            process(agc, -40.0, speech());
        }
        float gain = agc.getGain();

        for(int x = 0; x < 10; x++)
        {
            AMBEModelParameters repeat = speech();
            repeat.setRepeatCount(1);
            process(agc, -30.0, repeat);

            AMBEModelParameters errored = speech();
            errored.setErrorCountTotal(8);
            process(agc, -30.0, errored);
        }

        assertEquals(gain, agc.getGain(), 0.0001f);
    }

    @Test
    public void voicedFramesWithErraticPitchAreNotSpeech()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 50; x++)
        {
            process(agc, -40.0, frame(true, x % 2 == 0 ? 0.1f : 0.2f));
            assertFalse(agc.isSpeech());
        }
        assertEquals(1.0f, agc.getAppliedGain(), 0.0001f);
    }

    @Test
    public void gainRecoversAfterLoudBurst()
    {
        AGC agc = new AGC();
        talk(agc, -40.0, 49);
        float gain = agc.getGain();
        assertEquals(10.0f, gain, 0.1f);

        process(agc, -10.0, speech());
        assertTrue(agc.getGain() < gain);

        talk(agc, -40.0, 300);
        assertEquals(gain, agc.getGain(), gain * 0.05f);
    }

    @Test
    public void peaksAreLimited()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 50; x++)
        {
            process(agc, -45.0, speech());
        }

        float[] output = process(agc, -12.0, speech());
        for(float sample : output)
        {
            assertTrue(Math.abs(sample) <= Short.MAX_VALUE * 0.95f);
        }
    }

    @Test
    public void loudUnvoicedSpeechDoesNotRaiseNoiseFloor()
    {
        AGC agc = new AGC();
        talk(agc, -30.0, 20);
        for(int x = 0; x < 20; x++)
        {
            process(agc, -25.0, unvoiced());
        }
        process(agc, -40.0, speech());
        process(agc, -40.0, speech());
        assertTrue(agc.isSpeech());
        assertTrue(agc.getNoiseFloorDb() <= -55.0f);
    }

    @Test
    public void steadyVoicedNoiseIsNotSpeechOnceNoiseFloorIsKnown()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 150; x++)
        {
            process(agc, -35.0, unvoiced());
        }
        for(int x = 0; x < 50; x++)
        {
            process(agc, -34.0, speech());
            assertFalse(agc.isSpeech());
        }
    }

    @Test
    public void digitalSilenceDoesNotDisableSpeechThreshold()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 120; x++)
        {
            process(agc, -45.0, unvoiced());
        }
        agc.process(new float[160], unvoiced());
        for(int x = 0; x < 10; x++)
        {
            process(agc, -40.0, speech());
            assertFalse(agc.isSpeech());
        }
    }

    @Test
    public void quietOnsetFollowedByLoudSpeechReducesGainQuickly()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 20; x++)
        {
            process(agc, -65.0, unvoiced());
        }
        for(int x = 0; x < 6; x++)
        {
            process(agc, -45.0, speech());
        }
        assertEquals(17.8f, agc.getGain(), 0.1f);
        for(int x = 0; x < 4; x++)
        {
            process(agc, -20.0, speech());
        }
        assertTrue(agc.getGain() < 2.0f);
    }

    @Test
    public void resetRestoresDefaultGain()
    {
        AGC agc = new AGC();
        for(int x = 0; x < 20; x++)
        {
            process(agc, -40.0, speech());
        }
        agc.reset();
        assertEquals(1.0f, agc.getGain(), 0.0001f);
        assertEquals(1.0f, agc.getAppliedGain(), 0.0001f);
    }
}
