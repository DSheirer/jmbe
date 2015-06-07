package jmbe.iface;

import javax.sound.sampled.AudioFormat;

/**
 * Audio converter interface.  Defines methods for a stand-alone converter that
 * can convert byte data from one audio format into byte data of another.
 */
public interface AudioConverter
{
	/**
	 * Name of the CODEC for this audio converter
	 */
	public String getCodecName();

	/**
	 * Converts frameData into the audio format specified by the 
	 * getConvertedAudioFormat() method */
	public byte[] convert( byte[] frameData );

	/**
	 * Output (converted) audio format provided by this audio converter.
	 */
	public AudioFormat getConvertedAudioFormat();
}
