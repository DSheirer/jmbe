#!/bin/bash

clear
echo JMBE Audio Library Builder
echo
echo WEBSITE: https://github.com/DSheirer/jmbe
echo
echo This program downloads the current JMBE audio library source code and 
echo compiles the code to create a jmbe-x.x.x.jar file that can be used with  
echo other programs for decoding MBE audio.
echo
echo STEP 1: Checking for Java Development Kit JDK installation and PATH
echo         environment variable
echo

javac -version

if [ $? -eq 0 ]; then
	echo
	echo STEP 2: Download JMBE source code and compile using Apache ANT
	echo Note: this may take a short time depending on your internet connection
	echo
	./jmbe_builder/ant/bin/ant -buildfile ./jmbe_builder/build.xml library
	echo
	echo Finished downloading and compiling library
	echo

	if [ $? -ne 0 ]; then
		echo STEP 2: FAILED - there was an error during ANT download and compile step.	
		echo
		echo SOLUTION: please post a help request email to the sdrtrunk support group with
		echo the contents of this screen.
		echo
		echo Support Group: https://groups.google.com/forum/#!forum/sdrtrunk
		echo
	fi
else
	echo.
	echo STEP 1: FAILED - the javac compiler is not in your PATH environment variable.
	echo
	echo SOLUTION: edit the bash startup file: ~/ .bashrc
	echo 
	echo Modify the file with:
	echo PATH="$PATH":/usr/local/jdk1.x.x/bin
	echo export PATH
	echo 
	echo save and close the file
	echo verify the path is set by typing: javac -version
	echo 
	echo Attempt to run this builder again
	goto END
fi

read -n1 -r -p "Press any key to continue..." key

echo
