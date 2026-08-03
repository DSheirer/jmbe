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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Reader for MBE call sequence recordings
 */
public class MBECallSequenceReader
{
    private static final Logger LOG = LoggerFactory.getLogger(MBECallSequenceReader.class);

    /**
     * Loads the MBE Sequence from the file path
     * @param path for the sequence file.
     * @return loaded sequence or null.
     */
    public static MBECallSequence load(Path path)
    {
        try
        {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(path.toFile(), MBECallSequence.class);        }
        catch (IOException e)
        {
            LOG.error("Error loading file [" + path + "]", e);
        }

        return null;
    }
}