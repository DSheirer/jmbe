# jmbe
Java Multi-Band Excitation audio decoder library

  Provides java audio system plug-in component to support decoding MBE encoded 
  audio frames.  

  Includes a javax.sound.sampled.spi.FormatConversionProvider compatible plug-in
  to support error detection and correction, decoding and converting IMBE 144-bit, 
  20 millisecond audio frames to 8 kHz 16-bit Mono PCM encoded audio.

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
	
	4.  Checkout a copy of the jmbe library.
	
	5.  In the build folder (jmbe/build) execute the command 'ant'.  This will
	compile and build the library and place the library in the (jmbe/library)
	folder.
	
	6.  Place the compiled library (jmbe-x.x.x.jar) on the classpath of your
	java program so that it can be discovered by the java AudioSystem on 
	startup.  If you are adding the jmbe library to an existing program, simply
	copy the libaray to the same folder as the program you are using. 
	
Third-Party libraries

	jmbe uses two third-party libraries that are not included in the default
	library output product in order to avoid conflicts when these same libraries
	are used in your program.
	
	If you want to include these third party libraries in the compiled jbme
	library so that you don't have to include them yourself, you can use the 
	ant build target:  ant library-complete
	
	Libraries used by jbme:
		
	Piotr Wendykier's JTransforms libary: https://github.com/wendykierp/JTransforms
	Simple Logging Facade for Java: http://www.slf4j.org/
	
