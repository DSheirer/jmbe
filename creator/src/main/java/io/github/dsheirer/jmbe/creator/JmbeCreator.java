/*
 * ******************************************************************************
 * Copyright (C) 2015-2019 Dennis Sheirer
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

package io.github.dsheirer.jmbe.creator;

import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for downloading and compiling the JMBE library
 */
public class JmbeCreator
{
    private static final Logger mLog = LoggerFactory.getLogger(JmbeCreator.class);
    private static final String GITHUB_REPOSITORY = "git://github.com/DSheirer/jmbe.git";

    public JmbeCreator()
    {
    }

    public void createLibrary()
    {
        mLog.info("Creating JMBE library ...");

        Path tempDirectory = getTempDirectory();

        mLog.info("Using temporary directory [" + tempDirectory.toAbsolutePath() + "]");

        if(tempDirectory != null)
        {
            if(downloadSourceCode(tempDirectory))
            {
                mLog.info("JMBE source code downloaded to [" + tempDirectory.toAbsolutePath() + "]");

                mLog.info("Running gradle build script");
                runGradle(tempDirectory);
            }
            else
            {
                mLog.warn("Unable to download source code.  JMBE library creation failed");
            }
        }
    }

    private void runGradle(Path directory)
    {
        Path gradleWrapper = directory.resolve("gradlew");

        mLog.info("Executing gradle wrapper [" + gradleWrapper.toString() + "]");

        String[] args = new String[1];
        args[0] = "build";

        try
        {
            Runtime.getRuntime().exec(gradleWrapper.toString(), args, directory.toFile());
        }
        catch(Exception e)
        {
            mLog.error("Error running gradle wrapper", e);
        }
    }

    private Path getTempDirectory()
    {
        try
        {
            return Files.createTempDirectory("jmbe_builder");
        }
        catch(IOException ioe)
        {
            mLog.error("Could not create temporary directory to use in creating JMBE library");
        }

        return null;
    }

    private boolean downloadSourceCode(Path directory)
    {
        mLog.info("Cloning JMBE repository source code from [" + GITHUB_REPOSITORY + "] to [" + directory + "]");

        try
        {
            CloneCommand cloneCommand = Git.cloneRepository();
            cloneCommand.setURI(GITHUB_REPOSITORY);
            cloneCommand.setDirectory(directory.toFile());
            cloneCommand.setBranch("5-ambe-codec-2");
            cloneCommand.call();

            return true;
        }
        catch(Exception e)
        {
            mLog.error("Error downloading JMBE source code from GitHub.com", e);
        }

        return false;
    }

    public static void main(String[] args)
    {
        JmbeCreator creator = new JmbeCreator();
        creator.createLibrary();
    }
}
