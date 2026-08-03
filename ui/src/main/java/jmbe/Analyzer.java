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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jtransforms.fft.FloatFFT_1D;
import thumbdv.util.Utils;

public class Analyzer
{
    /**
     * Calculates the spectrum of each list of audio samples at 160-sample frames and writes the results to CSV.
     * @param jmbe pcm samples
     * @param jmbeEnhanced pcm samples
     * @param ambe pcm samples
     * @param fundamentals frequencies
     * @param harmonics counts
     */
    public static AnalysisResults process(List<byte[]> jmbe, List<byte[]> jmbeEnhanced, List<byte[]> ambe, List<Float> fundamentals, List<Integer> harmonics)
    {
        float[] bufferJmbe = new float[240];
        float[] bufferJmbeEnhanced = new float[240];
        float[] bufferAmbe = new float[240];

        List<double[]> jmbeResults = new ArrayList<>();
        List<double[]> jmbeEnhancedResults = new ArrayList<>();
        List<double[]> ambeResults = new ArrayList<>();
        List<float[]> jmbePcmResults = new ArrayList<>();
        List<float[]> ambePcmResults = new ArrayList<>();

        for(int i = 0; i < jmbe.size(); i++)
        {
            //Move previous samples to the left in the buffer
            System.arraycopy(bufferJmbe, 160, bufferJmbe, 0, 80);
            System.arraycopy(bufferJmbeEnhanced, 160, bufferJmbeEnhanced, 0, 80);
            System.arraycopy(bufferAmbe, 160, bufferAmbe, 0, 80);

            //Convert from byte array to float samples
            float[] pcmJmbe = Utils.convertFromSigned16BitSamples(jmbe.get(i));
            float[] pcmJmbeEnhanced = Utils.convertFromSigned16BitSamples(jmbeEnhanced.get(i));
            float[] pcmAmbe = Utils.convertFromSigned16BitSamples(ambe.get(i));
            jmbePcmResults.add(pcmJmbe);
            ambePcmResults.add(pcmAmbe);

            //Copy new samples into buffer
            System.arraycopy(pcmJmbe, 0, bufferJmbe, 80, 160);
            System.arraycopy(pcmJmbeEnhanced, 0, bufferJmbeEnhanced, 80, 160);
            System.arraycopy(pcmAmbe, 0, bufferAmbe, 80, 160);

            double[] goertzalJmbe = getGoertzalDbs(Arrays.copyOf(bufferJmbe, 160), fundamentals.get(i), harmonics.get(i));
            double[] goertzalJmbeEnhanced = getGoertzalDbs(Arrays.copyOf(bufferJmbeEnhanced, 160), fundamentals.get(i), harmonics.get(i));
            double[] goertzalAmbe = getGoertzalDbs(Arrays.copyOf(bufferAmbe, 160), fundamentals.get(i), harmonics.get(i));

            jmbeResults.add(goertzalJmbe);
            jmbeEnhancedResults.add(goertzalJmbeEnhanced);
            ambeResults.add(goertzalAmbe);
        }

        return new AnalysisResults(jmbeResults, jmbeEnhancedResults, ambeResults, jmbePcmResults, ambePcmResults);
    }

    /**
     * Calculates the spectrum of each list of audio samples at 160-sample frames and writes the results to CSV.
     * @param jmbe pcm samples
     * @param ambe pcm samples
     * @param out file for writing the results
     */
    public static void process(List<byte[]> jmbe, List<byte[]> ambe, Path out, List<Float> fundamentals, List<Integer> harmonics)
    {
        StringBuilder sb = new StringBuilder();
        FloatFFT_1D fft = new FloatFFT_1D(128);
        float[] bufferJmbe = new float[240];
        float[] bufferAmbe = new float[240];

        for(int i = 0; i < jmbe.size(); i++)
        {
            //Move previous samples to the left in the buffer
            System.arraycopy(bufferJmbe, 160, bufferJmbe, 0, 80);
            System.arraycopy(bufferAmbe, 160, bufferAmbe, 0, 80);

            //Convert from byte array to float samples
            float[] pcmJmbe = Utils.convertFromSigned16BitSamples(jmbe.get(i));
            float[] pcmAmbe = Utils.convertFromSigned16BitSamples(ambe.get(i));

            //Copy new samples into buffer
            System.arraycopy(pcmJmbe, 0, bufferJmbe, 80, 160);
            System.arraycopy(pcmAmbe, 0, bufferAmbe, 80, 160);

            //Snapshot the first 128-samples for FFT
            float[] fftJmbe = Arrays.copyOf(bufferJmbe, 128);
            float[] fftAmbe = Arrays.copyOf(bufferAmbe, 128);

            fft.realForward(fftJmbe);
            fft.realForward(fftAmbe);

            //Convert to DB
            double[] dbJmbe = toDB(fftJmbe);
            double[] dbAmbe = toDB(fftAmbe);

            //Snapshot the second 128-samples for FFT
            fftJmbe = Arrays.copyOfRange(bufferJmbe, 32, 160);
            fftAmbe = Arrays.copyOfRange(bufferAmbe, 32, 160);

            fft.realForward(fftJmbe);
            fft.realForward(fftAmbe);

            double[] dbJmbe2 = toDB(fftJmbe);
            double[] dbAmbe2 = toDB(fftAmbe);

            //Average the results ...
            for(int x = 0; x < dbJmbe.length; x++)
            {
                dbJmbe[x] = (dbJmbe[x] + dbJmbe2[x]) / 2;
                dbAmbe[x] = (dbAmbe[x] + dbAmbe2[x]) / 2;
            }

            sb.append(i + 1);  //Use 1-based indexing to align with the UI display
            sb.append(",jmbe,");
            sb.append(Arrays.toString(dbJmbe).replace("[", "").replace("]", ""));
            sb.append("\n");
            sb.append(i + 1);
            sb.append(",ambe,");
            sb.append(Arrays.toString(dbAmbe).replace("[", "").replace("]", ""));
            sb.append("\n");

            double[] goertzalJmbe = getGoertzalDbs(Arrays.copyOf(bufferJmbe, 160), fundamentals.get(i), harmonics.get(i));
            double[] goertzalAmbe = getGoertzalDbs(Arrays.copyOf(bufferAmbe, 160), fundamentals.get(i), harmonics.get(i));

            sb.append(i + 1);  //Use 1-based indexing to align with the UI display
            sb.append(",jmbeG,");
            sb.append(Arrays.toString(goertzalJmbe).replace("[", "").replace("]", ""));
            sb.append("\n");
            sb.append(i + 1);
            sb.append(",ambeG,");
            sb.append(Arrays.toString(goertzalAmbe).replace("[", "").replace("]", ""));
            sb.append("\n");
        }

        try
        {
            Files.write(out, sb.toString().getBytes());
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Calculates the magnitudes of the frequency bins from the FFT results array
     * @param fft results array
     * @return magnitudes
     */
    public static double[] toDB(float[] fft)
    {
        int half = fft.length / 2;
        double[] db = new double[half + 1];

        db[0] = toDB(fft[0]);
        db[half] = toDB(fft[1]);

        for(int i = 1; i < half; i++)
        {
            double magnitude = Math.sqrt(Math.pow(fft[i * 2], 2) + Math.pow(fft[i * 2 + 1], 2));
            db[i] = toDB(magnitude);
        }

        return db;
    }

    private static double toDB(double magnitude)
    {
        double db = 20 * Math.log10(magnitude / 64.0); //64 is the FFT bin count from the 128 point FFT
        if(!Double.isFinite(db))
        {
            db = 0;
        }

        return db;
    }

    public static double[] getGoertzalDbs(float[] samples, float fundamental, int harmonics)
    {
        double[] dbs = new double[harmonics];

        for(int x = 1; x <= harmonics; x++)
        {
            double frequency = 8000 * ((fundamental * x) / (2 * Math.PI));
            double magnitude = calculateMagnitude(samples, 8000f, frequency);
            dbs[x - 1] = 20 * Math.log10(magnitude / 64);
            if(!Double.isFinite(dbs[x - 1]))
            {
                dbs[x -1] = 0;
            }
        }

        return dbs;
    }

    /**
     * Calculates the magnitude of a specific target frequency in an array of PCM audio samples.
     *
     * @param samples    Array of audio samples (normalized or raw PCM values).
     * @param sampleRate The sampling rate of the audio (e.g., 44100.0f).
     * @param targetFreq The frequency you want to measure (in Hz).
     * @return The magnitude of the target frequency component.
     */
    public static double calculateMagnitude(float[] samples, float sampleRate, double targetFreq)
    {
        int numSamples = samples.length;

        // Normalize target frequency to the bin index
        double k = (0.5 + ((numSamples * targetFreq) / sampleRate));
        double omega = (2.0 * Math.PI * k) / numSamples;
        double sine = Math.sin(omega);
        double cosine = Math.cos(omega);
        double coeff = 2.0 * cosine;

        double s0 = 0.0;
        double s1 = 0.0;
        double s2 = 0.0;

        for (double sample : samples)
        {
            s0 = sample + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }

        // Calculate real and imaginary parts to get magnitude
        double real = s1 - s2 * cosine;
        double imag = s2 * sine;
        return Math.sqrt(real * real + imag * imag);
    }
}
