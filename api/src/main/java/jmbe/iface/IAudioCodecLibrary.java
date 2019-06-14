package jmbe.iface;

public interface IAudioCodecLibrary
{
	String getVersion();
	
	int getMajorVersion();
	
	int getMinorVersion();

	int getBuildVersion();
	
	boolean supports( String codec );
	
	IAudioCodec getAudioConverter(String codec);
}
