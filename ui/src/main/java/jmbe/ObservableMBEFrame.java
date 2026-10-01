/*
 * ******************************************************************************
 * Copyright (C) 2015-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * *****************************************************************************
 */

package jmbe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.Arrays;
import jmbe.codec.MBEModelParameters;

/**
 * Observable wrapper for voice frame.
 */
public class ObservableMBEFrame
{
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
    private final VoiceFrame mVoiceFrame;
    private final MBEModelParameters mModelParameters;
    private final double mTimeOffset;
    private float[] mJmbeAudioPcm = new float[0];
    private float[] mAmbeAudioPcm = new float[0];
    private double[] mJmbeAmplitudes = new double[0];
    private double[] mJmbeEnhancedAmplitudes = new double[0];
    private double[] mAmbeAmplitudes = new double[0];

    /**
     * Constructs an instance
     *
     * @param voiceFrame to wrap
     * @param modelParameters for the frame
     * @param timeOffset in seconds from zero
     */
    public ObservableMBEFrame(VoiceFrame voiceFrame, MBEModelParameters modelParameters, double timeOffset)
    {
        mVoiceFrame = voiceFrame;
        mModelParameters = modelParameters;
        mTimeOffset = timeOffset;
    }

    private String getAMBEWeightsDescription()
    {
        StringBuilder sb = new StringBuilder();
        if(mAmbeAmplitudes.length > 0 && mJmbeAmplitudes.length > 0)
        {
            double y = getAmbeAmplitudes()[0] / getJmbeAmplitudes()[0];
            sb.append("Power Adjustment Y: ").append(DECIMAL_FORMAT.format(y)).append("\n");

            for(int i = 1; i < mAmbeAmplitudes.length; i++)
            {
                double unscaledAmplitude = mAmbeAmplitudes[i] / y;
                double weight = unscaledAmplitude / mJmbeAmplitudes[i];
                sb.append(i).append(": ").append(DECIMAL_FORMAT.format(weight)).append("\n");
            }
        }

        return sb.toString();
    }

    public String getDescription()
    {

        StringBuilder sb = new StringBuilder();
        sb.append("Fundamental:").append(mModelParameters.getMBEFundamentalFrequency().getName());
        sb.append(" L Band Count:").append(mModelParameters.getL()).append("\n\n");
        double ambePowerScaleY = getAmbeAmplitudes()[0] / getJmbeAmplitudes()[0];
        sb.append("AMBE Power Adjustment Y: ").append(DECIMAL_FORMAT.format(ambePowerScaleY)).append("\n");

        double[] ambeWeightOriginal = new double[mAmbeAmplitudes.length + 1];
        double[] ambeWeightScaled = new double[mAmbeAmplitudes.length + 1];

        for(int i = 0; i < mAmbeAmplitudes.length; i++)
        {
            double unscaledAmplitude = mAmbeAmplitudes[i] / ambePowerScaleY;
            ambeWeightOriginal[i + 1] = unscaledAmplitude / mJmbeAmplitudes[i];
            ambeWeightScaled[i + 1] = ambeWeightOriginal[i + 1] * ambePowerScaleY;
        }

        float[] frequencies = new float[mAmbeAmplitudes.length + 1];

        for(int i = 0; i < mModelParameters.getVoicingDecisions().length; i++)
        {
            frequencies[i] = (float)(i * (8000 * (mModelParameters.getFundamentalFrequency() / (2 * Math.PI))));
            sb.append(i).append(": ").append(mModelParameters.getVoicingDecisions()[i] ? "V " : "* ");
            sb.append(DECIMAL_FORMAT.format(i * (8000 * (mModelParameters.getFundamentalFrequency() / (2 * Math.PI)))));
            sb.append("\tAmp Orig:").append(DECIMAL_FORMAT.format(mModelParameters.getSpectralAmplitudes()[i]));
            sb.append("\tAmp Enhan:").append(DECIMAL_FORMAT.format(mModelParameters.getEnhancedSpectralAmplitudes()[i]));
            sb.append("\t  WO:").append(DECIMAL_FORMAT.format(mModelParameters.getWeightOriginal()[i]));
            sb.append("\t  WC:").append(DECIMAL_FORMAT.format(mModelParameters.getWeightEnhanced()[i]));
            sb.append("\t WS:").append(DECIMAL_FORMAT.format(mModelParameters.getWeightScaled()[i]));
            sb.append("\t  AWC:").append(DECIMAL_FORMAT.format(ambeWeightOriginal[i]));
            sb.append("\t AWS:").append(DECIMAL_FORMAT.format(ambeWeightScaled[i]));
            sb.append("\n");
        }

        //********************** start

        /* Algorithm #105 and #106 - calculate RM0 and RM1 from amplitudes */
        float[] RM = new float[2];

        float[] spectralAmplitudes = mModelParameters.getSpectralAmplitudes();

        int L = mModelParameters.getL();

        for(int l = 1; l <= L; l++)
        {
            float amplitudesSquared = (float)Math.pow(spectralAmplitudes[l], 2);

            /**
             * Calculates the power spectrum or energy density of each frequency bin.
             */
            RM[0] += amplitudesSquared;

            /**
             * Google says: this calculates the phase-weighted power spectrum of each frequency bin.  This produces the
             * net power contributed by components that are in-phase (0 degrees) versus out-of-phase (180 degrees),
             * relative to a pure cosine reference baseline at the start of the sample window.
             */
            float d = mModelParameters.getFundamentalFrequency();
            float e = mModelParameters.getFundamentalFrequency() * l;
            float c = (float)Math.cos(getFundamentalFrequency() * l);
            float b = amplitudesSquared * (float)Math.cos(getFundamentalFrequency() * l);
            RM[1] += (amplitudesSquared * Math.cos(getFundamentalFrequency() * l));
        }

        float[] W = new float[L + 1];

        float rm0squared = RM[0] * RM[0];
        float rm1squared = RM[1] * RM[1];

        /* Algorithm #107 - calculate enhancement weights (W) */

        for(int l = 1; l <= mModelParameters.getL(); l++)
        {
            float zero96Pi = 0.96f * (float)Math.PI;
            float numerator = 0.96f * (float)Math.PI * (rm0squared + rm1squared - (2.0f * RM[0] * RM[1] * (float)Math.cos(getFundamentalFrequency() * l)));
            float denominator = (getFundamentalFrequency() * RM[0] * (rm0squared - rm1squared));
            float small = numerator / denominator;
            float brackets = (float) Math.pow(small, 0.25);
            float weight = (float)Math.sqrt(spectralAmplitudes[l]) * brackets;
            //Note: The 2003 ICD has "0.96 * PI" and the 2014 version only has "0.96".
            float temp = (zero96Pi * (rm0squared + rm1squared -
                    (2.0f * RM[0] * RM[1] * (float)Math.cos(getFundamentalFrequency() * l)))) /
                    (getFundamentalFrequency() * RM[0] * (rm0squared - rm1squared));
            W[l] = (float)(Math.sqrt(spectralAmplitudes[l]) * Math.pow(temp, 0.25));
        }

        //********************** end

        StringBuilder sb2 = new StringBuilder();

        sb2.append("Band,Voicing,Frequency,SAmp, Enh Samp, WO, WC, WS, AWO, AWS, JMBE Amp (dB), JMBE Enh Amp (dB), AMBE Amp (dB)\n");
        for(int i = 0; i < mModelParameters.getVoicingDecisions().length; i++)
        {
            sb2.append(i).append(",");
            sb2.append(mModelParameters.getVoicingDecisions()[i]).append(",");
            sb2.append(frequencies[i]).append(",");
            sb2.append(mModelParameters.getSpectralAmplitudes()[i]).append(",");
            sb2.append(mModelParameters.getEnhancedSpectralAmplitudes()[i]).append(",");
            sb2.append(mModelParameters.getWeightOriginal()[i]).append(",");
            sb2.append(mModelParameters.getWeightEnhanced()[i]).append(",");
            sb2.append(mModelParameters.getWeightScaled()[i]).append(",");
            sb2.append(ambeWeightOriginal[i]).append(",");
            sb2.append(ambeWeightScaled[i]).append(",");

            if(i != 0)
            {
                sb2.append(mJmbeAmplitudes[i - 1]).append(",");
                sb2.append(mJmbeEnhancedAmplitudes[i - 1]).append(",");
                sb2.append(mAmbeAmplitudes[i - 1]).append("\n");
            }
            else
            {
                sb2.append("0,0,0\n");
            }
        }

        try
        {
            Path csvAnalysis = Paths.get("/run/media/denny/T9/AMBE Research/frame_analysis.csv");
            Files.write(csvAnalysis, sb2.toString().getBytes());
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        sb.append("\n\n");
        sb.append(getAMBEWeightsDescription());

        return sb.toString();
    }

    public double[] getJmbeEnhancedAmplitudes()
    {
        return mJmbeEnhancedAmplitudes;
    }

    public void setJmbeEnhancedAmplitudes(double[] jmbeEnhancedAmplitudes)
    {
        mJmbeEnhancedAmplitudes = jmbeEnhancedAmplitudes;
    }

    public double[] getJmbeAmplitudes()
    {
        return mJmbeAmplitudes;
    }

    public void setJmbeAmplitudes(double[] jmbeAmplitudes)
    {
        mJmbeAmplitudes = jmbeAmplitudes;
    }

    public double[] getAmbeAmplitudes()
    {
        return mAmbeAmplitudes;
    }

    public void setAmbeAmplitudes(double[] ambeAmplitudes)
    {
        mAmbeAmplitudes = ambeAmplitudes;
    }

    /**
     * Indicates if the frame has amplitude values
     */
    public boolean hasAmplitudes()
    {
        return getJmbeAmplitudes().length > 0 && getAmbeAmplitudes().length > 0;
    }

    public float[] getJmbeAudioPcm()
    {
        return mJmbeAudioPcm;
    }

    public void setJmbeAudioPcm(float[] jmbeAudioPcm)
    {
        mJmbeAudioPcm = jmbeAudioPcm;
    }

    public float[] getAmbeAudioPcm()
    {
        return mAmbeAudioPcm;
    }

    public void setAmbeAudioPcm(float[] ambeAudioPcm)
    {
        mAmbeAudioPcm = ambeAudioPcm;
    }

    /**
     * Voiced vs Noise decisions for each harmonic
     */
    public boolean[] getVoicingDecisions()
    {
        return mModelParameters.getVoicingDecisions();
    }

    /**
     * Time offset from the start where the first frame is 0.0 and each subsequent frame is 0.02 (20 milliseconds)
     * higher offset from zero.
     * @return time offset in seconds.
     */
    public double getTimeOffset()
    {
        int offset = (int)Math.round(mTimeOffset * 100);
        return offset / 100.0;
    }

    /**
     * Fundamental frequency for this frame.
     *
     * @return fundamental frequency
     */
    public float getFundamentalFrequency()
    {
        return (mModelParameters.getFundamentalFrequency() * (float)(8000 / (2 * Math.PI)));
    }

    /**
     * Name of the fundamental frequency enumeration entry
     */
    public String getFundamentalName()
    {
        String name = mModelParameters.getMBEFundamentalFrequency().getName();

        if(name.contentEquals("W120"))
        {
            return name + " **ERASURE";
        }

        return name;
    }

    /**
     * Frequency band count
     * @return count
     */
    public int getBandCount()
    {
        return mModelParameters.getL();
    }

    /**
     * Error rate
     * @return error rate
     */
    public float getErrorRate()
    {
        return mModelParameters.getErrorRate();
    }

    /**
     * Total number of detected and corrected errors.
     * @return total error count
     */
    public int getErrorCountTotal()
    {
        return mModelParameters.getErrorCountTotal();
    }

    /**
     * Percentage of harmonics that are voiced versus non-voiced..
     * @return voiced harmonics percentage
     */
    public double getVoicingPercentage()
    {
        int voicedHarmonicCount = 0;

        for(boolean voiced: getVoicingDecisions())
        {
            if(voiced)
            {
                voicedHarmonicCount++;
            }
        }

        return (double)voicedHarmonicCount / ((double)getVoicingDecisions().length - 1);
    }

    /**
     * Number of times this frame has been repeated due to high error rate
     *
     * @return
     */
    public int getRepeatCount()
    {
        return mModelParameters.getRepeatCount();
    }

    /**
     * Indicates if the voice frame is encrypted
     * @return
     */
    public boolean isEncrypted()
    {
        return mVoiceFrame.getAlgorithm() != null;
    }


    /**
     * Voice Frame
     * @return
     */
    public VoiceFrame getVoiceFrame()
    {
        return mVoiceFrame;
    }

    public long getTimestamp()
    {
        return mVoiceFrame.getTimestamp();
    }

    public String getFrame()
    {
        return mVoiceFrame.getFrame();
    }
}
