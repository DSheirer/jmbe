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

import jmbe.codec.ambe.AMBEAudioCodec;
import jmbe.codec.ambe.AMBEAudioCodecV3;
import jmbe.codec.imbe.IMBEAudioCodec;
import jmbe.codec.imbe.IMBEAudioCodecV3;
import jmbe.iface.IAudioCodecLibraryV3;
import jmbe.iface.IAudioCodecV3;

/**
 * JMBE audio library version 3 providing IAudioCodecV3 implementations that can synthesize audio from frames that
 * the host application has already error corrected.
 *
 * Note: JMBEAudioLibrary and JMBEAudioLibraryV2 must not reference the version 3 classes so that the library still
 * loads in host applications built against an older api.
 */
public class JMBEAudioLibraryV3 extends JMBEAudioLibraryV2 implements IAudioCodecLibraryV3
{
    @Override
    public IAudioCodecV3 getAudioConverterV3(String codec)
    {
        switch (codec)
        {
            case IMBEAudioCodec.CODEC_NAME:
                return new IMBEAudioCodecV3();
            case AMBEAudioCodec.CODEC_NAME:
                return new AMBEAudioCodecV3();
            default:
                throw new IllegalArgumentException("Unsupported CODEC:" + (codec != null ? codec : "(null)"));
        }
    }
}
