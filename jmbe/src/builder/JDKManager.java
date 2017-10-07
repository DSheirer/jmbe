/*
 * **********************************************************
 * jmbe - Java MBE Library
 * Copyright (C) 2014-2017 Dennis Sheirer
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
 * **********************************************************
 */

package builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class JDKManager
{
    private final static Logger mLog = LoggerFactory.getLogger(JDKManager.class);

    private enum OS
    {
        LINUX, WINDOWS
    }

    private OS mOperatingSystem;
    private Path mJavaHome;

    /**
     * Utility for discovering and/or validating the path to the JDK compiler
     */
    public JDKManager()
    {

        init();
    }

    /**
     * Path for installed Java Development Kit (JDK)
     * @return discovered path or null
     */
    public Path getJavaHome()
    {
        return mJavaHome;
    }

    /**
     * Indicates if a java home is set
     */
    public boolean hasJavaHome()
    {
        return mJavaHome != null;
    }

    /**
     * Sets the java home directory/path
     */
    public void setJavaHome(Path javaHome)
    {
        mJavaHome = javaHome;
    }

    private void init()
    {
        mLog.info("Host CPU Cores: " + Runtime.getRuntime().availableProcessors());
        mLog.info("Host Memory Total: " + Runtime.getRuntime().totalMemory());
        mLog.info("Host Memory Max: " + Runtime.getRuntime().maxMemory());
        mLog.info("Host Memory Free: " + Runtime.getRuntime().freeMemory());

        determineOperatingSystem();
        discoverInstalledJDK();
    }

    /**
     * Determines the Operating System for the host computer
     */
    private void determineOperatingSystem()
    {
        String os = System.getProperty("os.name");

        if(os != null && os.toLowerCase().contains("win"))
        {
            mOperatingSystem = OS.WINDOWS;
        }
        else
        {
            mOperatingSystem = OS.LINUX;
        }

        mLog.info("Host Operating System: " + mOperatingSystem.name());
    }

    /**
     * Discovers the installed Java Development Kit home directory/path
     */
    private void discoverInstalledJDK()
    {
        if(mOperatingSystem == OS.LINUX)
        {
            discoverInstalledLinuxJDK();
        }
        else if(mOperatingSystem == OS.WINDOWS)
        {
            discoverInstalledWindowsJDK();
        }
        else
        {
            mLog.info("Unknown Operating System [" + mOperatingSystem.name() + "] - JDK compiler discovery not supported");
        }
    }

    /**
     * Discovers the installed Linux JDK
     */
    private void discoverInstalledLinuxJDK()
    {
        mLog.info("Linux JDK Compiler discovery in progress");

        String response = getCommandOutput("whereis javac");

        if(response != null && !response.isEmpty())
        {
            mLog.info("Java compiler path discovery response [" + response + "]");

            int pathStartIndex = response.indexOf('/');

            if(pathStartIndex == -1)
            {
                mLog.warn("Could not locate Java JDK compiler - the OS path environment variable is not setup for java compiler");
            }
            else
            {
                String pathResponse = response.substring(pathStartIndex, response.length());

                //Linux whereis can return more than one location if javac is aliased, choose the location
                //that contains 'jdk' in the path
                String[] paths = pathResponse.split(" ");

                mLog.info("Java compiler [javac] may be available at: " + Arrays.toString(paths));

                if(paths.length == 1)
                {
                    try
                    {
                        Path javacPath = Paths.get(paths[0]);
                        Path javaHomePath = getJavaHomeFromCompilerPath(javacPath);
                        mJavaHome = validateJavaHome(javaHomePath);
                    }
                    catch(Exception e)
                    {
                        mLog.error("There was an error while validating the javac compiler at [" + paths[0]);
                    }
                }
                else if(paths.length > 1)
                {
                    for(String path: paths)
                    {
                        try
                        {
                            Path javacPath = Paths.get(path);
                            Path javaHomePath = getJavaHomeFromCompilerPath(javacPath);
                            mJavaHome = validateJavaHome(javaHomePath);

                            if(mJavaHome != null)
                            {
                                break;
                            }
                        }
                        catch(Exception e)
                        {
                            mLog.error("There was an error while validating the javac compiler at [" + path);
                        }
                    }
                }
                else
                {
                    mLog.warn("Could not locate Java JDK compiler - does the user have the Java JDK installed on this computer?");
                }
            }
        }

        if(mJavaHome == null)
        {
            mLog.warn("Could not locate Java JDK compiler - does the user have the Java JDK installed on this computer?");
        }
    }

    /**
     * Discovers the installed Windows JDK
     */
    private void discoverInstalledWindowsJDK()
    {
        mLog.info("Windows JDK Compiler discovery in progress");

        String response = getCommandOutput("where javac");

        if(response != null && !response.isEmpty())
        {
            mLog.info("Java compiler path discovery response: " + response);

        }
        else
        {
            mLog.warn("Could not locate Java JDK compiler - does the user have the Java JDK installed on this computer?");
        }

    }

    private Path getJavaHomeFromCompilerPath(Path javacPath)
    {
        if(javacPath == null)
        {
            mLog.info("Validating - javac path is null");
            return null;
        }

        if(!Files.exists(javacPath))
        {
            mLog.info("Validating - javac path does not exist [" + javacPath.toString() + "]");
            return null;
        }

        if(Files.isSymbolicLink(javacPath))
        {
            mLog.info("Validating - javac path [" + javacPath.toString() + "] is a symbolic link - ignoring");
            return null;
        }

        if(!javacPath.toString().toLowerCase().contains("jdk"))
        {
            mLog.info("Validating - javac path [" + javacPath.toString() + "] does not contain partial phrase 'JDK' in the path - ignoring");
            return null;
        }

        //Java home directory should be two levels up from the javac executable
        return javacPath.getParent().getParent();
    }

    public static Path validateJavaHome(Path javaHomePath)
    {
        if(javaHomePath == null)
        {
            mLog.info("Validating - java home path is null");
            return null;
        }

        if(!Files.exists(javaHomePath))
        {
            mLog.info("Validating - java home path does not exist [" + javaHomePath.toString() + "]");
            return null;
        }

        if(!Files.isDirectory(javaHomePath))
        {
            mLog.info("Validating - java home path is not a directory [" + javaHomePath.toString() + "]");
            return null;
        }

        try
        {
            //Attempt to locate javac program under java home directory
            Path javaCompiler = javaHomePath.resolve("bin").resolve("javac");

            if(Files.exists(javaCompiler))
            {
                Path javaLibraries = javaHomePath.resolve("lib").resolve("tools.jar");

                if(Files.exists(javaLibraries))
                {
                    mLog.info("Java Home Directory [" + javaHomePath.toString() + "]");
                    return javaHomePath;
                }
                else
                {
                    mLog.info("Validating - directory [" + javaHomePath.toString() + "] does not contain 'lib/tools.jar'" +
                        " - this is not a valid java home directory");
                    return null;
                }
            }
            else
            {
                mLog.info("Validating - directory [" + javaHomePath.toString() + "] does not contain 'bin/javac' - this is not " +
                    "a valid java home directory");
                return null;
            }
        }
        catch(Exception ioe)
        {
            mLog.error("Error while validating javac path at [" + javaHomePath.toString() + "]");
        }

        return null;
    }

    /**
     * Utility command to return the output of a CLI command.
     *
     * Code by author tomgeraghty3 discovered on 8 July 2017 at:
     * https://stackoverflow.com/questions/15725601/finding-jdk-path-and-storing-it-as-a-string-in-java
     *
     * @param command to execute
     * @return output of the command
     */
    public static String getCommandOutput(String command)
    {
        String output = null;       //the string to return

        Process process = null;
        BufferedReader reader = null;
        InputStreamReader streamReader = null;
        InputStream stream = null;

        try
        {
            process = Runtime.getRuntime().exec(command);

            //Get stream of the console running the command
            stream = process.getInputStream();
            streamReader = new InputStreamReader(stream);
            reader = new BufferedReader(streamReader);

            String currentLine = null;  //store current line of output from the cmd
            StringBuilder commandOutput = new StringBuilder();  //build up the output from cmd
            while((currentLine = reader.readLine()) != null)
            {
                commandOutput.append(currentLine);
            }

            int returnCode = process.waitFor();

            if(returnCode == 0)
            {
                output = commandOutput.toString();
            }

        }
        catch(IOException e)
        {
            mLog.error("Cannot retrieve output of command [" + command + "]", e);
            output = null;
        }
        catch(InterruptedException e)
        {
            mLog.error("Cannot retrieve output of command [" + command + "]", e);
        }
        finally
        {
            //Close all inputs / readers

            if(stream != null)
            {
                try
                {
                    stream.close();
                }
                catch(IOException e)
                {
                    mLog.error("Cannot close input stream", e);
                }
            }
            if(streamReader != null)
            {
                try
                {
                    streamReader.close();
                }
                catch(IOException e)
                {
                    mLog.error("Cannot close input stream reader", e);
                }
            }
            if(reader != null)
            {
                try
                {
                    streamReader.close();
                }
                catch(IOException e)
                {
                    mLog.error("Cannot close input stream reader", e);
                }
            }
        }
        //Return the output from the command - may be null if an error occured
        return output;
    }
}
