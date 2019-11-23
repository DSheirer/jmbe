Copyright (C) 2015-2019 Dennis Sheirer

# jmbe - Java Multi-Band Excitation library

Audio conversion library for decoding MBE encoded audio frames.
  
Decodes IMBE 144-bit and AMBE 72-bit encoded 20 millisecond audio frames to 8 kHz 16-bit mono PCM encoded audio.

**PATENT NOTICE**

This source code is provided for educational purposes only.  It is a written
description of how certain voice encoding/decoding algorithms could be
implemented.  Executable objects compiled or derived from this package may be
covered by one or more patents.  Readers are strongly advised to check for any
patent restrictions or licensing requirements before compiling or using this
source code.

Note: this patent notice is verbatim from the mbelib library README at (https://github.com/szechyjs/mbelib)

# Preparing to Compile the Library From Source Code

* Install the Java 8 (or higher) Java Development Kit (JDK). Note: this is different from the Java Runtime
Environment (JRE) that most users have installed on their computers.
	
  * **Liberica OpenJDK: (https://bell-sw.com/)**
  * **Oracle: (http://www.oracle.com/technetwork/java/javase/downloads/index.html)**

* Download the source code branch from GitHub:

  * **Version 1.0.0 (current): (https://github.com/DSheirer/jmbe/archive/v1.0.0.zip)**
  * **Version 0.3.4 (previous): (https://github.com/DSheirer/jmbe/archive/v0.3.4.zip)**

# WINDOWS: Compiling the Library from Source Code

* Setup the JAVA_HOME and PATH environment variables

  * (https://www.theserverside.com/tutorial/How-to-install-the-JDK-on-Windows-and-setup-JAVA_HOME)

* Verify that JAVA_HOME points to the Java Development Kit (JDK) version 8 or higher.  At a command prompt type:

  * **> echo %JAVA_HOME%**

This should respond with the directory where you have installed the JDK.

  * **> javac -version**

This should respond with the java version.

* Unzip the source code file with a tool like 7-Zip or using the Windows File Manager (right-click on file)

* Using the command prompt, change to the directory where you downloaded and unzipped the source code (jmbe-master.zip in this example):

  * **> cd C:\Users\Denny\Downloads\jmbe-1.0.0**

* Run the build script

  * **> gradlew.bat build**

* The build script will compile the source code and create the library.  The first time that you run the build script,
it may download some additional files needed for installing the gradle build tool and some java libraries needed for
compiling the jmbe library code.

* The compiled JMBE library will be located in a sub-folder named '\build\libs', for example:

  * **> C:\Users\Denny\Downloads\jmbe-master\build\libs\jmbe-1.0.0.jar**

* Follow the instructions for the application that will use the JMBE library.

Note: for **sdrtrunk** use the menu item **View > Preferences** and then use the **JMBE Audio Library** section to tell sdrtrunk where your compiled JMBE library is located. 

# LINUX: Compiling the Library from Source Code

* Setup the JAVA_HOME and PATH environment variables

(https://askubuntu.com/questions/175514/how-to-set-java-home-for-java)

* Verify that JAVA_HOME points to the Java Development Kit (JDK) version 8 or higher.  Open a terminal and type:

  * **> echo $JAVA_HOME$**

This should respond with the directory where you have installed the JDK.

  * **> javac -version**

This should respond with the java version.

* Unzip the source code file with a tool like 7-Zip or ark using the File Manager (right-click on file)

* In the terminal window, change to the directory where you downloaded and unzipped the source code (jmbe-master.zip in this example):

  * **> denny@denny-desktop:~$ cd Downloads\jmbe-1.0.0**

* Run the build script

  * **> ./gradlew build**

* The build script will compile the source code and create the library.  The first time that you run the build script,
it may download some additional files needed for installing the gradle build tool and some java libraries needed for
compiling the jmbe library code.

* The compiled JMBE library will be located at:

  * **> ~\Downloads\jmbe-master\build\libs\jmbe-1.0.0.jar**

* Follow the instructions for the application that will use the JMBE library.  

Note: for **sdrtrunk** use the menu item **View > Preferences** and then use the **JMBE Audio Library** section to tell sdrtrunk where your compiled JMBE library is located. 
	
# Software Developers - Using the JMBE audio conversion library in your own java program

* Follow the same instructions for downloading the source code above.  Use the following command to build the API:

WINDOWS:
> gradlew.bat api

LINUX:
> ./gradlew api

* Add the API library jar to your project.

* Add the following code to your program
	
		IAudioCodecLibrary audioCodecLibrary = null;
		
		try
		{
                    URLClassLoader childClassLoader = new URLClassLoader(new URL[]{path.toUri().toURL()},
                        this.getClass().getClassLoader());

                    Class classToLoad = Class.forName("jmbe.JMBEAudioLibrary", true, childClassLoader);

                    Object instance = classToLoad.getDeclaredConstructor().newInstance();

                    if(instance instanceof IAudioCodecLibrary)
                    {
                        audioCodecLibrary = (IAudioCodecLibrary)instance;
    		    } 
		catch (Exception e)
		{
		    //error handling
		}
	
* To convert 18-byte IMBE audio frames:

		IAudioCodec audioCodec = library.getAudioConverter("IMBE");
		float[] convertedAudio = audioCodec.getAudio(byte[] imbeFrameData);

* To convert 9-byte AMBE audio and tone frames:

		IAudioCodec audioCodec = library.getAudioConverter("AMBE");
		float[] convertedAudio = audioCodec.getAudio(byte[] ambeFrameData);

* To convert 9-byte AMBE audio frames and tone frames along with tone frame metadata:

		IAudioCodec audioCodec = library.getAudioConverter("AMBE");
		IAudioWithMetadata convertedAudio = audioCodec.getAudioWithMetadata(byte[] ambeFrameData);

