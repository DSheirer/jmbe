echo off
cls
echo **************************************************************************
echo *******  JMBE Audio Library Builder                                *******
echo *******  WEBSITE: https://github.com/DSheirer/jmbe                 *******
echo **************************************************************************
echo.
echo This program downloads the current JMBE audio library source code and 
echo compiles the code to create a jmbe-x.x.x.jar file that can be used with  
echo other programs for decoding MBE audio.
echo.
echo STEP 1: Checking for Java Development Kit (JDK) installation and PATH
echo         environment variable
echo.

javac -version
IF %ERRORLEVEL% EQU 9009 (
	echo.
	echo STEP 1: FAILED - the JDK bin directory is not in your PATH environment variable.
	echo.
	echo SOLUTION: add the JDK installation to your PATH environment variable. Depending
	echo on which version of the JDK you installed, your path should contain one or  
	echo more entries separated by semi-colons and one of those entries must be the
	echo java installation similar to this:
	echo.
	echo                 c:\Program Files\java\jdk1.8.0_74\bin
	goto END
)

IF %ERRORLEVEL% NEQ 0 (
	echo HERE
	echo.
	echo STEP 1: FAILED - unable to locate the javac.exe java compiler program
	echo.
	echo SOLUTION: install the latest Java Development Kit JDK
	echo.
	echo Note: the Java Runtime Environment JRE is the program that you
	echo use to run compiled java programs.  The Java Development Kit JDK
	echo is the program needed to compile java programs.  You can have both
	echo of these programs installed on your computer.
	goto END
)

echo.
echo STEP 2: Download JMBE source code and compile using Apache ANT
echo Note: this may take a short time depending on your internet connection
echo.
call ./jmbe_builder/ant/bin/ant -buildfile ./jmbe_builder/build.xml library

echo.
echo Finished downloading and compiling library
echo.

if %ERRORLEVEL% EQU 0 (
	goto END
)

if %ERRORLEVEL% NEQ 0 (
	echo STEP 2: FAILED - there was an error during ANT download and compile step.
	echo.
	echo SOLUTION: please post a help request email to the sdrtrunk support group with
	echo the contents of this screen.
	echo.
	echo Support Group: https://groups.google.com/forum/#!forum/sdrtrunk
	echo.
	goto END
)

:END
pause
echo on