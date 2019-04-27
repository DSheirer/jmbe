package jmbe.iface;

import javax.sound.sampled.AudioFormat;

public interface IAudioConversionLibrary
{
	String getVersion();
	
	int getMajorVersion();
	
	int getMinorVersion();

	int getBuildVersion();
	
	boolean supports( String codec );
	
	IAudioConverter getAudioConverter(String codec, AudioFormat output );
}
