package jmbe.codec.ambe;

import jmbe.codec.ambe.tone.Tone;
import jmbe.codec.ambe.tone.ToneParameters;
import jmbe.iface.IAudioCodecV2;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyToneLevelsTest
{
    private static final ToneParameters SINGLE = new ToneParameters(Tone.T32, 64);

    private static double rms(float[] samples)
    {
        double sum = 0;
        for(float sample : samples)
        {
            sum += sample * sample;
        }
        return samples.length == 0 ? 0 : Math.sqrt(sum / samples.length);
    }

    @Test
    public void calibratedLevelsRemainDefault()
    {
        AMBESynthesizer synth = new AMBESynthesizer();
        double expectedPeak = Math.pow(10.0, -44.33 / 20.0);
        assertEquals(expectedPeak / Math.sqrt(2), rms(synth.getTone(SINGLE)), 0.00001);
        synth.setLegacyToneLevels(true);
        synth.setLegacyToneLevels(false);
        assertEquals(expectedPeak / Math.sqrt(2), rms(synth.getTone(SINGLE)), 0.00001);
    }

    @Test
    public void legacyLevelsSupportAttenuationAndMute()
    {
        AMBESynthesizer synth = new AMBESynthesizer();
        synth.setLegacyToneLevels(true);
        double full = rms(synth.getTone(SINGLE));
        assertEquals((64.0 / 127.0) / Math.sqrt(2), full, 0.00004);
        synth.setToneGain(0.5f);
        assertEquals(full * 0.5, rms(synth.getTone(SINGLE)), 0.00001);
        synth.setToneGain(0.0f);
        assertEquals(0, rms(synth.getTone(SINGLE)), 0);
    }

    @Test
    public void legacyDualTonesStayWithinFullScaleAtUnityGain()
    {
        AMBESynthesizer synth = new AMBESynthesizer();
        synth.setLegacyToneLevels(true);
        for(Tone tone : Tone.values())
        {
            if(tone != Tone.INVALID && tone.hasFrequency2())
            {
                float[] samples = synth.getTone(new ToneParameters(tone, 127));
                assertEquals(160, samples.length);
                for(float sample : samples)
                {
                    assertTrue(Float.isFinite(sample));
                    assertTrue(Math.abs(sample) <= 1.000001f);
                }
            }
        }
    }

    @Test
    public void callResetRetainsSelectedModeAndGain()
    {
        AMBESynthesizer synth = new AMBESynthesizer();
        synth.setLegacyToneLevels(true);
        synth.setToneGain(0.5f);
        synth.reset();
        assertEquals((64.0 / 127.0) * 0.5 / Math.sqrt(2),
            rms(synth.getTone(SINGLE)), 0.00001);
    }

    @Test
    public void codecAdvertisesFeatureAndValidatesItsType()
    {
        AMBEAudioCodecV2 codec = new AMBEAudioCodecV2();
        assertTrue(codec.supportsFeature(IAudioCodecV2.FEATURE_LEGACY_TONE_LEVELS));
        codec.setFeature(IAudioCodecV2.FEATURE_LEGACY_TONE_LEVELS, true);
        codec.setFeature(IAudioCodecV2.FEATURE_LEGACY_TONE_LEVELS, false);
        try
        {
            codec.setFeature(IAudioCodecV2.FEATURE_LEGACY_TONE_LEVELS, 1.0f);
            fail("Expected rejection of a non-Boolean value");
        }
        catch(IllegalArgumentException expected)
        {
            //Feature requires a Boolean.
        }
    }
}