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
import jmbe.codec.imbe.IMBEAudioCodec;
import jmbe.codec.imbe.IMBEAudioCodecV2;
import jmbe.iface.IAudioCodecLibraryV2;
import jmbe.iface.IAudioCodecV2;

/**
 * JMBE audio library version 2 providing IAudioCodecV2 implementations that expose support for setting codec features.
 */
public class JMBEAudioLibraryV2 extends JMBEAudioLibrary implements IAudioCodecLibraryV2
{

    @Override
    public IAudioCodecV2 getAudioConverterV2(String codec)
    {
        switch (codec)
        {
            case IMBEAudioCodec.CODEC_NAME:
                return new IMBEAudioCodecV2();
            case AMBEAudioCodec.CODEC_NAME:
//                return new AMBEAudioCodec();
            default:
                throw new IllegalArgumentException("Unsupported CODEC:" + (codec != null ? codec : "(null)"));
        }
    }
}
