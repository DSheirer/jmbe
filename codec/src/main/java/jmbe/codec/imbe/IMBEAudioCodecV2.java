package jmbe.codec.imbe;

/*******************************************************************************
 * jmbe - Java MBE Library
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
 ******************************************************************************/

import java.util.List;
import jmbe.iface.IAudioCodecV2;

public class IMBEAudioCodecV2 extends IMBEAudioCodec implements IAudioCodecV2
{
    public IMBEAudioCodecV2()
    {
    }

    @Override
    public void setFeature(String feature, Object value) throws IllegalArgumentException
    {
        if(FEATURE_NOISE_GENERATOR_GAIN.equals(feature))
        {
            if(value instanceof Float gain && 0.0 <= gain && gain <= 2.0)
            {
                mSynthesizer.setComfortNoiseGeneratorGain(gain);
            }
            else
            {
                throw new IllegalArgumentException("Valid values in range: 0.0f to 2.0f.  Unsupported value: " + value);
            }
        }
        else if(FEATURE_AUTOMATIC_GAIN_CONTROL.equals(feature))
        {
            if(value instanceof Boolean enabled)
            {
                mSynthesizer.setAGC(enabled);
            }
            else
            {
                throw new IllegalArgumentException("Valid value is boolean true (enabled) or false (disabled).  Unsupported value: " + value);
            }
        }
        else
        {
            throw new IllegalArgumentException("Unsupported feature: " + feature);
        }
    }

    @Override
    public List<String> getFeatures()
    {
        return List.of(FEATURE_NOISE_GENERATOR_GAIN, FEATURE_AUTOMATIC_GAIN_CONTROL);
    }
}
