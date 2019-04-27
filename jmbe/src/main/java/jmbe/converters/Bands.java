package jmbe.converters;

/**
 * K - Frequency Bands
 *
 * The audio spectrum can be split into 3 to 12 frequency bands with
 * each band classified as a voiced or unvoiced band during encoding.  The
 * bit vector b1 conveys the number of bands and the status
 * (voiced/unvoiced) of each band.
 */
public enum Bands
{
    K03,K04,K05,K06,K07,K08,K09,K10,K11,K12;
}
