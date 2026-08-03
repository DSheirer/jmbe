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

package jmbe.iface;

import java.util.List;

/**
 * Audio converter interface.  Defines methods for a stand-alone converter that
 * can convert byte data from one audio format into byte data of another.
 */
public interface IAudioCodecV2 extends IAudioCodec
{
    /**
     * White noise generator gain feature.  Value argument is float in range: 0.0 to 2.0. 1.0 = default. 0.0 = disabled.
     */
    String FEATURE_NOISE_GENERATOR_GAIN = "feature_noise_generator_gain";

    /**
     * Tone generator gain feature.  Value argument is float in range: 0.0 to 2.0. 1.0 = default. 0.0 = disabled.
     */
    String FEATURE_TONE_GENERATOR_GAIN = "feature_tone_generator_gain";

    /**
     * Automatic Gain Control (AGC).  Value argument is boolean.  True = enabled. False = disabled.
     */
    String FEATURE_AUTOMATIC_GAIN_CONTROL = "feature_automatic_gain_control";

    /**
     * Sets a feature value for the audio converter.
     *
     * @param feature key value
     * @param value to set for the feature
     * @throws IllegalArgumentException if the feature is not supported by the audio converter or if the value argument
     * is not appropriate for the feature.
     */
    void setFeature(String feature, Object value) throws IllegalArgumentException;

    /**
     * Provides a list of features supported by the audio converter
     * @return
     */
    List<String> getFeatures();

    /**
     * Indicates whether the audio converter supports the specified feature
     *
     * @param feature to test
     * @return true if the feature is supported, false otherwise
     */
    default boolean supportsFeature(String feature)
    {
        return feature != null && getFeatures().contains(feature);
    }
}
