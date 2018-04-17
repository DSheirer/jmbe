Copyright (C) 2015-2018 Dennis Sheirer

# jmbe - Java Multi-Band Excitation library

Audio conversion library for decoding MBE encoded audio frames.
  
Currently supports conversion of IMBE 144-bit/20 millisecond audio frames to 48 kHz 16-bit mono PCM encoded audio.

**PATENT NOTICE**

This source code is provided for educational purposes only.  It is a written
description of how certain voice encoding/decoding algorithms could be
implemented.  Executable objects compiled or derived from this package may be
covered by one or more patents.  Readers are strongly advised to check for any
patent restrictions or licensing requirements before compiling or using this
source code.

Note: this patent notice is verbatim from the mbelib library README at (https://github.com/szechyjs/mbelib)

# Preparing to Compile the Library From Source Code

* Install the Java 8 (or higher) Java Development Kit (JDK). Note: this isdifferent from the Java Runtime
Environment (JRE) that most users have installed on their computers.
	
(http://www.oracle.com/technetwork/java/javase/downloads/index.html)

* Download the latest version of the source code from GitHub from (https://github.com/DSheirer/jmbe/releases)

# WINDOWS: Compiling the Library from Source Code

* Setup the JAVA_HOME and PATH environment variables

(https://www.theserverside.com/tutorial/How-to-install-the-JDK-on-Windows-and-setup-JAVA_HOME)

* Verify that JAVA_HOME points to the Java Development Kit (JDK) version 8 or higher.  At a command prompt type:

> echo %JAVA_HOME%

This should respond with the directory where you have installed the JDK.

> javac -version

This should respond with the java version.

* Unzip the source code file with a tool like 7-Zip or using the Windows File Manager (right-click on file)

* Using the command prompt, change to the directory where you downloaded and unzipped the source code (jmbe-0.3.3a.zip in this example):

> cd C:\Users\Denny\Downloads\jmbe-0.3.3a\jmbe

* Run the build script

> gradlew.bat build

* The build script will compile the source code and create the library.  The first time that you run the build script,
it may download some additional files needed for installing the gradle build tool and some java libraries needed for
compiling the jmbe library code.

* The compiled JMBE library will be located in a sub-folder named '\build\libs', for example:

> C:\Users\Denny\Downloads\jmbe-0.3.4\jmbe\build\libs\jmbe-0.3.3.jar

* Copy the compiled JMBE library jar to the same folder where your application is located and then start the application
like normal.  The JMBE library will be automatically discovered at runtime.

# LINUX: Compiling the Library from Source Code

* Setup the JAVA_HOME and PATH environment variables

(https://askubuntu.com/questions/175514/how-to-set-java-home-for-java)

* Verify that JAVA_HOME points to the Java Development Kit (JDK) version 8 or higher.  Open a terminal and type:

> javac -version

This should respond with the java version.

* Unzip the source code file with a tool like 7-Zip or ark using the File Manager (right-click on file)

* In the terminal window, change to the directory where you downloaded and unzipped the source code (jmbe-0.3.4.zip in this example):

> denny@denny-desktop:~$ cd Downloads\jmbe-0.3.3a\jmbe

* Run the build script

> ./gradlew build

* The build script will compile the source code and create the library.  The first time that you run the build script,
it may download some additional files needed for installing the gradle build tool and some java libraries needed for
compiling the jmbe library code.

* The compiled JMBE library will be located at:

> ~\Downloads\jmbe-0.3.4\jmbe\build\libs\jmbe-0.3.3.jar

* Copy the compiled JMBE library jar to the same folder where your application is located and then start the application
like normal.  The JMBE library will be automatically discovered at runtime.
	
# Software Developers - Using the JMBE audio conversion library in your own java program

* Follow the same instructions for downloading the source code above.  Use the following command to build the API:

WINDOWS:
> gradlew.bat api

LINUX:
> ./gradlew api

* Add the API library jar to your project.

* Add the following code to your program
	
		AudioConversionLibrary library = null;
		
		AudioConverter converter = null;
		
		try
		{
			Class temp = Class.forName( "jmbe.JMBEAudioLibrary" );
			
			library = (AudioConversionLibrary)temp.newInstance();

			converter = library.getAudioConverter( "IMBE", 
					AudioFormats.PCM_SIGNED_48KHZ_16BITS );
			
			mCanConvertAudio = ( converter != null );
		} 
		catch ( ClassNotFoundException e1 )
		{
			mLog.error( "Couldn't find/load JMBE audio conversion library", e1 );
		}
		catch ( InstantiationException e1 )
		{
			mLog.error( "Couldn't instantiate JMBE audio conversion library class" );
		}
		catch ( IllegalAccessException e1 )
		{
			mLog.error( "Couldn't load JMBE audio conversion library due to security restrictions" );
		}
	
* To convert 18-byte IMBE audio frames, use the following code:

		if( converter != null )
		{
			float[] convertedAudio = converter.decode( imbeFrame );
		}
