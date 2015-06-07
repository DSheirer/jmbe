Copyright (C) 2015 Dennis Sheirer

jmbe - Java Multi-Band Excitation library 

  Audio conversion library for decoding MBE encoded audio frames.  
  
  Currently supports conversion of IMBE 144-bit/20 millisecond audio frames to
  48 kHz 16-bit mono PCM encoded audio.

PATENT NOTICE

 This source code is provided for educational purposes only.  It is a written 
 description of how certain voice encoding/decoding algorithms could be 
 implemented.  Executable objects compiled or derived from this package may be 
 covered by one or more patents.  Readers are strongly advised to check for any 
 patent restrictions or licensing requirements before compiling or using this 
 source code.

 Note: this patent notice is verbatim from the mbelib library README at
 https://github.com/szechyjs/mbelib

Compiling and using the Library

	1. Install the Java 7 (or higher) Java Development Kit (JDK). Note: this is
	different from the Java Runtime Environment (JRE) that most users have 
	installed on their computers.
	
	http://www.oracle.com/technetwork/java/javase/downloads/index.html
	
	2. Install Apache Ant 
	
	http://ant.apache.org/manual/install.html
	
	3.  Ensure that ANT_HOME and JAVA_HOME environment variables are defined
	for your operating system as described in ant installation manual.  Ensure
	that the ant.bat program is added to your path so that you can execute the 
	program in any directory.
	
	4.  Checkout/clone a copy of the jmbe library.
	
	5.  In the build folder (jmbe/build) execute the command 'ant'.  This will
	compile and build all products and place them in the jmbe/library folder.
	
	6.  Place the compiled library (jmbe-x.x.x.jar) on the classpath of your
	java program or in the same directory as your java program, so that can be 
	discovered at runtime. 
	
Scripted downloading and compiling the library for end-users

	1.  Run the ant target 'create_builder' to create a zip file for end users 
	that will clone the jmbe git repository locally, compile the code and 
	generate the library jar file, using windows or linux scripts.
	
Third-Party libraries

	JMBE uses a third-party logging library and the JTransforms FFT library. 
	These libraries are not included in the default output products in order to 
	avoid conflicts when the same libraries are used in your program.
	
	If you want to include these libraries in the compiled jmbe library so that
	you don't have to include it yourself, you can use the ant build target:  
	ant create-library-with-third-party-libs
	
	Libraries used by jbme:
		
	Simple Logging Facade for Java: http://www.slf4j.org/
	
	JTransforms FFT: https://sites.google.com/site/piotrwendykier/software/jtransforms

Using the JMBE audio conversion library in your own java program

	1. Run the ant task 'create-interface' to generate the generic audio converter 
	interfaces library and place the library on your class path.
	
	2. Add the following code to your program
	
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
	
	3. To convert 18-byte IMBE audio frames, use the following code:

		if( converter != null )
		{
			byte[] convertedAudio = converter.convert( unconvertedAudio );
		}	
		
		