package jmbe.iface;

public interface ConvertedAudioListener
{
	/**
	 * Converted audio provided by the audio converter
	 */
	public void receive( byte[] convertedAudio );
}
