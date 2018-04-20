package jmbe.iface;

import javax.sound.sampled.AudioFormat;

public interface AudioConversionLibrary
{
	public String getVersion();
	
	public int getMajorVersion();
	
	public int getMinorVersion();
	
	public int getBuildVersion();
	
	public boolean supports( String codec );
	
	public AudioConverter getAudioConverter( String codec, AudioFormat output );
}
