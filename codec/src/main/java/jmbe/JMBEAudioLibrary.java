package jmbe;

import java.net.JarURLConnection;
import java.net.URLConnection;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jmbe.codec.ambe.AMBEAudioCodec;
import jmbe.codec.ambe.AMBEAudioCodecV2;
import jmbe.codec.imbe.IMBEAudioCodec;
import jmbe.codec.imbe.IMBEAudioCodecV2;
import jmbe.iface.IAudioCodec;
import jmbe.iface.IAudioCodecLibrary;

public class JMBEAudioLibrary implements IAudioCodecLibrary
{
    /**
     * Matches a manifest version value (e.g. 2.1.1, v2.1.1, or 1.0.6a).
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+).*");

    /**
     * Manifest attribute containing the version.  Written by both the codec Gradle jar task (from the version in the
     * root build.gradle) and the Creator application (from the GitHub release).  The Creator compiles the source code
     * directly, without Gradle, so the jar manifest is the only version source common to both build paths.
     */
    private static final String VERSION_ATTRIBUTE = "Version";

    /**
     * Major, minor, and build version values, or all zeros when the version isn't available, for example when running
     * from compiled classes instead of from the library jar.
     */
    private static final int[] VERSION = parseVersion(readManifestVersion());

    /**
     * Reads the version from the manifest of the jar that contains this class.  This locates the manifest from this
     * class's own location because a class loader lookup of META-INF/MANIFEST.MF returns the host application's
     * manifest when this library is loaded by a child class loader.
     *
     * @return version value or null if it isn't available
     */
    private static String readManifestVersion()
    {
        try
        {
            URLConnection connection = JMBEAudioLibrary.class.getResource("JMBEAudioLibrary.class").openConnection();

            if(connection instanceof JarURLConnection jarConnection)
            {
                //Disable caching so that closing the jar file doesn't affect, and isn't held open by, the shared cache
                jarConnection.setUseCaches(false);

                try(JarFile jarFile = jarConnection.getJarFile())
                {
                    Manifest manifest = jarFile.getManifest();

                    if(manifest != null)
                    {
                        return manifest.getMainAttributes().getValue(new Attributes.Name(VERSION_ATTRIBUTE));
                    }
                }
            }
        }
        catch(Exception e)
        {
            //A missing version must not prevent this class from loading - the version is reported as all zeros
        }

        return null;
    }

    /**
     * Parses the version value.
     *
     * @param version value to parse, may be null
     * @return major, minor and build version values, or all zeros if the value can't be parsed
     */
    private static int[] parseVersion(String version)
    {
        if(version != null)
        {
            Matcher matcher = VERSION_PATTERN.matcher(version.trim());

            if(matcher.matches())
            {
                try
                {
                    return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3))};
                }
                catch(NumberFormatException nfe)
                {
                    //The value is too large for an integer - the version is reported as all zeros
                }
            }
        }

        return new int[]{0, 0, 0};
    }

    @Override
    public String getVersion()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("JMBE Audio Conversion Library v");
        sb.append(getMajorVersion());
        sb.append(".");
        sb.append(getMinorVersion());
        sb.append(".");
        sb.append(getBuildVersion());

        return sb.toString();
    }

    @Override
    public int getMajorVersion()
    {
        return VERSION[0];
    }

    @Override
    public int getMinorVersion()
    {
        return VERSION[1];
    }

    @Override
    public int getBuildVersion()
    {
        return VERSION[2];
    }

    @Override
    public boolean supports(String codecName)
    {
        switch (codecName)
        {
            case AMBEAudioCodec.CODEC_NAME:
            case IMBEAudioCodec.CODEC_NAME:
                return true;
            default:
                return false;
        }
    }

    @Override
    public IAudioCodec getAudioConverter(String codec)
    {
        switch (codec)
        {
            //Return the V2 codec for all cases - V2 is backward compatible with V1
            case IMBEAudioCodec.CODEC_NAME:
                return new IMBEAudioCodecV2();
            case AMBEAudioCodec.CODEC_NAME:
                return new AMBEAudioCodecV2();
            default:
                throw new IllegalArgumentException("Unsupported CODEC:" + (codec != null ? codec : "(null)"));
        }
    }
}
