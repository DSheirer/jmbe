/*
 * ******************************************************************************
 * Copyright (C) 2015-2020 Dennis Sheirer
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

import io.github.dsheirer.jmbe.creator.github.GitHub;
import io.github.dsheirer.jmbe.creator.github.Release;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Creator utility that downloads the JMBE source code, compiles it, and generates the JMBE library jar.
 */
public class Creator
{
    private final static Logger mLog = LoggerFactory.getLogger(GitHub.class);

    private final static String GITHUB_JMBE_RELEASES_URL = "https://api.github.com/repos/dsheirer/jmbe/releases";

    /**
     * Exit code to indicate that the process completed successfully
     */
    public static final int EXIT_CODE_SUCCESS = 0;

    /**
     * Exit code to indicate an unknown error
     */
    public static final int EXIT_CODE_UNKNOWN_ERROR = 1;
    /**
     * Exit code to indicate an error when reading or writing files to the local storage system
     */
    public static final int EXIT_CODE_IO_ERROR = 2;

    /**
     * Exit code to indicate an error when attempting to download network resources
     */
    public static final int EXIT_CODE_NETWORK_ERROR = 3;

    /**
     * Exit code to indicate that the library path argument is required
     */
    public static final int EXIT_CODE_LIBRARY_PATH_REQUIRED = 4;

    /**
     * Creates an instance
     */
    public Creator()
    {
    }

    /**
     * Process a downloaded source code file
     *
     * @param downloadFile containing a GitHub JMBE release artifact
     * @throws IOException if there is an error
     */
    public static void process(Path downloadFile) throws IOException
    {
        Path downloadDirectory = downloadFile.getParent();

        List<Path> toCompile = new ArrayList<>();

        System.out.println("Unzipping: Source Code");
        try(ZipFile zf = new ZipFile(downloadFile.toFile()))
        {
            Enumeration<? extends ZipEntry> zipEntries = zf.entries();

            zipEntries.asIterator().forEachRemaining(entry ->
            {
                try
                {
                    if(entry.isDirectory())
                    {
                        Path directoryToCreate = downloadDirectory.resolve(entry.getName());

                        if(!Files.exists(directoryToCreate))
                        {
                            Files.createDirectory(directoryToCreate);
                        }
                    }
                    else
                    {
                        Path fileToCreate = downloadDirectory.resolve(entry.getName());
                        Files.copy(zf.getInputStream(entry), fileToCreate, StandardCopyOption.REPLACE_EXISTING);

                        if(isCompilable(entry))
                        {
                            toCompile.add(fileToCreate);
                        }
                    }
                }
                catch(IOException ioe)
                {
                    System.out.println("Failed: I/O Error While Unzipping Source Code Files");
                    ioe.printStackTrace();
                    System.exit(EXIT_CODE_IO_ERROR);
                }
            });
        }
        catch(Exception e)
        {
            System.out.println("Failed: Error unzipping source code file - " + e.getLocalizedMessage());
        }

        if(!toCompile.isEmpty())
        {
            compile(toCompile, getOptions(downloadDirectory));
            System.out.println("Deleting: Compiled Interfaces");
            deleteInterfaceClasses(getOutputDirectory(downloadDirectory));
        }
    }

    /**
     * Creates JAR metadata directory and manifest file
     *
     * @param outputDirectory for writing files
     * @param version string for the library
     * @throws IOException if there is an error
     */
    public static void createJarMetadata(Path outputDirectory, String version) throws IOException
    {
        Path metaDirectory = outputDirectory.resolve("META-INF");
        if(!Files.exists(metaDirectory))
        {
            Files.createDirectory(metaDirectory);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Manifest-Version: 1.0\r\n");
        sb.append("Implementation-Title: jmbe\r\n");
        sb.append("Version: ").append(version).append("\r\n");
        sb.append("Site: https://github.com/DSheirer/jmbe\r\n");

        Path manifest = metaDirectory.resolve("MANIFEST.MF");
        Files.writeString(manifest, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Discovers the LICENSE file from the source code tree and copies it to the output directory
     *
     * @param downloadDirectory where source code exists
     * @throws IOException if there is an error
     */
    public static void copyLicenseFile(Path downloadDirectory) throws IOException
    {
        String fileName = "LICENSE";
        Path license = getFile(downloadDirectory, fileName);

        if(license != null)
        {
            Path toLicense = getOutputDirectory(downloadDirectory).resolve(fileName);
            Files.copy(license, toLicense, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Recursively finds the specified filename in the specified directory
     *
     * @param downloadDirectory to search
     * @param fileName to discover
     * @return discovered file path or null
     */
    public static Path getFile(Path downloadDirectory, String fileName)
    {
        try
        {
            DirectoryStream<Path> stream = Files.newDirectoryStream(downloadDirectory);
            Iterator<Path> it = stream.iterator();
            while(it.hasNext())
            {
                Path path = it.next();

                if(Files.isDirectory(path) && !path.equals(getOutputDirectory(downloadDirectory)))
                {
                    Path subPath = getFile(path, fileName);

                    if(subPath != null && subPath.endsWith(fileName))
                    {
                        return subPath;
                    }
                }
                else if(path.endsWith(fileName))
                {
                    return path;
                }
            }
        }
        catch(IOException ioe)
        {
            System.out.println("Error while searching for [" + fileName + "]");
            System.exit(EXIT_CODE_IO_ERROR);
        }

        return null;
    }

    /**
     * Output directory for storing compiled classes and JAR artifacts
     *
     * @param downloadDirectory where source code was downloaded
     * @return output directory
     */
    public static Path getOutputDirectory(Path downloadDirectory)
    {
        return downloadDirectory.resolve("output");
    }

    /**
     * Creates compiler options for classpath and output directory
     *
     * @param downloadDirectory where source code is located
     * @return options
     */
    public static List<String> getOptions(Path downloadDirectory)
    {
        Path libs = downloadDirectory.resolve("libs");
        Path output = getOutputDirectory(downloadDirectory);
        String classpath = getLibraryClassPath();
        List<String> options = new ArrayList<>();
        options.add("-cp");
        options.add(classpath);
        options.add("-d");
        options.add(output.toString());
        return options;
    }

    /**
     * Deletes the compiled interface classes from the output directory
     *
     * @param output
     * @throws IOException
     */
    public static void deleteInterfaceClasses(Path output) throws IOException
    {
        Path iface = output.resolve("jmbe").resolve("iface");

        if(Files.exists(iface) && Files.isDirectory(iface))
        {
            DirectoryStream<Path> stream = Files.newDirectoryStream(iface);
            stream.forEach(file -> {
                try
                {
                    Files.delete(file);
                }
                catch(IOException ioe)
                {
                    System.out.println("Error deleteing interface class file: " + file.toString());
                    ioe.printStackTrace();
                    System.exit(EXIT_CODE_IO_ERROR);
                }
            });
            Files.delete(iface);
        }
    }

    /**
     * Indicates if the zip entry is a compilable file for the codec or interface java classes
     *
     * @param zipEntry to inspect
     * @return true if the file is part of the interfaces or codec package and is a java file.
     */
    public static boolean isCompilable(ZipEntry zipEntry)
    {
        String name = zipEntry.getName();
        return name.endsWith(".java") && (name.contains("iface") || name.contains("codec"));
    }

    /**
     * Creates a command line classpath of the dependent jar libraries
     *
     * @return concatenated string suitable for -d command line option
     */
    public static String getLibraryClassPath()
    {
        URL currentURL = Creator.class.getProtectionDomain().getCodeSource().getLocation();
        System.out.println("Current URL:" + currentURL.toString());
        Path currentPath = null;

        try
        {
            currentPath = new File(currentURL.toURI()).toPath();
        }
        catch(Exception e)
        {
            mLog.error("Error discovering current execution path to lookup compile dependencies", e);
            currentPath = null;
        }

        if(currentPath != null && Files.exists(currentPath))
        {
            System.out.println("Discovering: Current Location [" + currentPath.toString() + "]");
            Path parent = currentPath.getParent();
            System.out.println("Discovering: Compile Dependencies [" + parent.toString() + "]");
            StringJoiner joiner = new StringJoiner(String.valueOf(File.pathSeparatorChar));

            try
            {
                DirectoryStream<Path> stream = Files.newDirectoryStream(parent);
                stream.forEach(path -> {
                    if(!Files.isDirectory(path) && path.toString().endsWith("jar"))
                    {
                        joiner.add(path.toString());
                    }
                });
            }
            catch(IOException ioe)
            {
                System.out.println("Failed: Error creating classpath for compile-time libraries - " +
                    ioe.getLocalizedMessage());
                ioe.printStackTrace();
                System.exit(EXIT_CODE_IO_ERROR);
            }

            return joiner.toString();
        }

        return "";
    }

    /**
     * Compiles the list of java source code files using the specified compile time options
     *
     * @param paths of source code files
     * @param options for compilation (classpath & output directory)
     */
    public static void compile(List<Path> paths, List<String> options)
    {
        System.out.println("Compiling: Source Code");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(paths);
        compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
    }

    /**
     * Creates a jar from the specified source directory, storing the jar at the specified output file name
     *
     * @param source directory containing compiled classes and other jar artifacts
     * @param output library file path and name
     * @throws IOException if there is an error
     */
    public static void createJar(Path source, Path output) throws IOException
    {
        if(!Files.exists(output.getParent()))
        {
            Files.createDirectory(output.getParent());
        }

        ZipUtility zipUtility = new ZipUtility();
        List<File> files = new ArrayList<>();
        DirectoryStream<Path> stream = Files.newDirectoryStream(source);
        stream.forEach(path -> {
            files.add(path.toFile());
        });
        zipUtility.zip(files, output.toString());
    }

    /**
     * Creates a jar name for the specified release version
     *
     * @param version of the GitHub JMBE release
     * @return JMBE library jar name
     */
    public static String getJarName(String version)
    {
        version = version.replace("v", "");
        return "jmbe-" + version + ".jar";
    }

    /**
     * Creates a JMBE library jar file from the lastest available source code.
     *
     * @param libraryPath for the final library
     * @return an exit code to indicate success or failure
     */
    public static int createLibrary(String libraryPath)
    {
        try
        {
            System.out.println("Starting: JMBE Library Creator");
            Path temporaryDirectory = Files.createTempDirectory("jmbe-creator");
            System.out.println("Created: Temporary Directory [" + temporaryDirectory.toString() + "]");
            Release latest = GitHub.getLatestRelease(GITHUB_JMBE_RELEASES_URL);

            if(latest != null)
            {
                Path library = null;

                if(libraryPath == null)
                {
                    library = Path.of(System.getProperty("user.dir")).getParent().resolve(getJarName(latest.getVersion().toString()));
                    System.out.println("Generated: Library Path [" + library.toString() + "]");
                }
                else
                {
                    library = Path.of(libraryPath);
                    System.out.println("Specified: Library Path [" + library.toString() + "]");
                }

                System.out.println("Downloading: Source Code [Version " + latest.getVersion().toString() + "]");
                Path download = GitHub.downloadReleaseSourceCode(latest, temporaryDirectory);

                if(download != null)
                {
                    process(download);
                    System.out.println("Creating: JAR Metadata");
                    createJarMetadata(getOutputDirectory(temporaryDirectory), latest.getVersion().toString());
                    System.out.println("Creating: JAR License File");
                    copyLicenseFile(temporaryDirectory);
                    Path sourceFiles = getOutputDirectory(temporaryDirectory);
                    Path zip = temporaryDirectory.resolve(getJarName(latest.getVersion().toString()));
                    System.out.println("Creating: JMBE Library [" + library.toString() + "]");
                    createJar(sourceFiles, library);
                    System.out.println("Deleting: Temporary Directory [" + temporaryDirectory.toString() + "]");

                    try
                    {
                        FileUtils.deleteDirectory(temporaryDirectory.toFile());
                    }
                    catch(IOException ioe)
                    {
                        System.out.println("Delete: Temporary Directory Failed [" + temporaryDirectory.toString() + "]");
                    }

                    System.out.println("----------------------------------------------------------------------");
                    System.out.println("Success: JMBE Library Created At: " + library.toString());
                    System.out.println("----------------------------------------------------------------------\n");
                    return EXIT_CODE_SUCCESS;
                }
                else
                {
                    System.out.println("Failed: Couldn't download source code from GitHub.  Exiting.");
                    return EXIT_CODE_NETWORK_ERROR;
                }
            }
            else
            {
                System.out.println("Failed: Unable to determine the latest JMBE release version from GitHub.");
                return EXIT_CODE_NETWORK_ERROR;
            }
        }
        catch(IOException ioe)
        {
            System.out.println("Failed: Unknown I/O Error " + ioe.getLocalizedMessage());
            ioe.printStackTrace();
            return EXIT_CODE_IO_ERROR;
        }
        catch(Exception e)
        {
            System.out.println("Failed: Unknown (General) Error " + e.getLocalizedMessage());
            e.printStackTrace();
            return EXIT_CODE_UNKNOWN_ERROR;
        }
    }

    public static void main(String[] args)
    {
        int status;

        if(args.length == 0)
        {
            status = createLibrary(null);
        }
        else if(args.length == 1)
        {
            status = createLibrary(args[0]);
        }
        else
        {
            status = EXIT_CODE_LIBRARY_PATH_REQUIRED;
            System.out.println("Usage: Creator (optional full path and library name)");
        }

        System.exit(status);
    }
}
