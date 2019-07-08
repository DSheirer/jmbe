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

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public Path createLibrary()
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
                return moveLibrary(tempDirectory);
            }
            else
            {
                mLog.warn("Unable to download source code.  JMBE library creation failed");
            }
        }

        return null;
    }

    private Path moveLibrary(Path directory)
    {
        Path libDirectory = directory.resolve("codec").resolve("build").resolve("libs");

        File[] matchingFiles = libDirectory.toFile().listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.matches("jmbe.*\\.jar") || name.matches("codec.*\\.jar");
            }
        });

        if(matchingFiles != null && matchingFiles.length == 1)
        {
            Path currentDirectory = Paths.get(".");
            Path library = matchingFiles[0].toPath();

            mLog.info("Attempting to move [" + library.toString() + "] to [" + currentDirectory.toString() + "]");

            try
            {
                Files.move(library, currentDirectory);
                return library;
            }
            catch(Exception e)
            {
                mLog.error("Error moving compiled library", e);
            }
        }

        return null;
    }

    private void runGradle(Path directory)
    {
        Path gradleWrapper = directory.resolve("gradlew");
        Path errorLog = directory.resolve("build_output.log");

        mLog.info("Testing file system creation");

        try
        {
            FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap());
        }
        catch(IOException ioe)
        {
            mLog.error("Error creating file system", ioe);
        }

        try
        {
            new ProcessBuilder().command("echo $JAVA_HOME").start();

        }
        catch(Exception e)
        {
            mLog.error("Error echoing", e);
        }

        mLog.info("Executing gradle wrapper [" + gradleWrapper.toString());

        List<String> commandList = new ArrayList<>();
        commandList.add(gradleWrapper.toString());
//        commandList.add("wrapper");
        commandList.add("jar");
        commandList.add("--stacktrace");
        commandList.add("--debug");
        commandList.add("--scan");
        commandList.add("--no-daemon");

        try
        {
            Process process = new ProcessBuilder()
                .command(commandList)
                .directory(directory.toFile())
                .redirectError(errorLog.toFile())
                .start();
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
        Path library = creator.createLibrary();

        if(library != null)
        {
            mLog.info("Compiled JMBE Library is located here [" + library.toString() + "]");
        }
    }
}
