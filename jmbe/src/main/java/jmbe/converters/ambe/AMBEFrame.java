package jmbe.converters.ambe;

import jmbe.binary.BinaryFrame;
import jmbe.converters.FrameType;
import jmbe.edac.Golay23;
import jmbe.edac.Golay24;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AMBE 3600 (2450 bits data and 1150 bps FEC) frame decoder
 */
public class AMBEFrame
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBEFrame.class);

    private static final int[] VECTOR_C0 = {0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64, 68, 1, 5,
        9, 13, 17, 21};
    private static final int[] VECTOR_C1 = {25, 29, 33, 37, 41, 45, 49, 53, 57, 61, 65, 69, 2, 6, 10, 14, 18, 22, 26,
        30, 34, 38, 42};
    private static final int[] VECTOR_C2 = {46, 50, 54, 58, 62, 66, 70, 3, 7, 11, 15};
    private static final int[] VECTOR_C3 = {19, 23, 27, 31, 35, 39, 43, 47, 51, 55, 59, 63, 67, 71};

    private static final int[] VECTOR_U0 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[] VECTOR_U0_B0_HIGH = {0, 1, 2, 3};
    private static final int[] VECTOR_U0_B1_HIGH = {4, 5, 6, 7};
    private static final int[] VECTOR_U0_B2_HIGH = {8, 9, 10, 11};
    private static final int[] VECTOR_U1_B3_HIGH = {0, 1, 2, 3, 4, 5, 6, 7};
    private static final int[] VECTOR_U1_B4_HIGH = {8, 9, 10, 11};
    private static final int[] VECTOR_U2_B5_HIGH = {0, 1, 2, 3};
    private static final int[] VECTOR_U2_B6_HIGH = {4, 5, 6};
    private static final int[] VECTOR_U2_B7_HIGH = {7, 8, 9};
    private static final int[] VECTOR_U2_B8_HIGH = {10};
    private static final int[] VECTOR_U3_B1_LOW = {0};
    private static final int[] VECTOR_U3_B2_LOW = {1};
    private static final int[] VECTOR_U3_B0_LOW = {2, 3, 4};
    private static final int[] VECTOR_U3_B3_LOW = {5};
    private static final int[] VECTOR_U3_B4_LOW = {6, 7, 8};
    private static final int[] VECTOR_U3_B5_LOW = {9};
    private static final int[] VECTOR_U3_B6_LOW = {10};
    private static final int[] VECTOR_U3_B7_LOW = {11};
    private static final int[] VECTOR_U3_B8_LOW = {12, 13};
    private static final int[] VECTOR_U0_AD = {6, 7, 8, 9, 10, 11};
    private static final int[] VECTOR_U1_ID = {0, 1, 2, 3, 4, 5, 6, 7};

    private BinaryFrame mFrame;
    private AMBEModelParameters mModelParameters;
    private Tone mTone;
    private int mToneAmplitude;

    /**
     * Constructs an AMBE voice frame
     * @param frame containing frame bits
     * @param previousFrame model parameters
     */
    public AMBEFrame(byte[] frame, AMBEModelParameters previousFrame)
    {
        mFrame = BinaryFrame.fromBytes(frame);

        BinaryFrame vectorC0 = getVector(VECTOR_C0);
        BinaryFrame vectorC1 = getVector(VECTOR_C1);
        BinaryFrame vectorC2 = getVector(VECTOR_C2);
        BinaryFrame vectorC3 = getVector(VECTOR_C3);

        //Error check C0 and descramble and error check C1
        int errorsC0 = Golay24.checkAndCorrect(vectorC0, 0);
        int u0 = vectorC0.getInt(VECTOR_U0);
        BinaryFrame modulationVector = getModulationVector(u0);
        vectorC1.xor(modulationVector);
        int errorsC1 = Golay23.checkAndCorrect(vectorC1, 0);

        int b0 = (vectorC0.getInt(VECTOR_U0_B0_HIGH) << 3) + vectorC3.getInt(VECTOR_U3_B0_LOW);
        AMBEFundamentalFrequency fundamentalFrequency = AMBEFundamentalFrequency.fromValue(b0);

        if(fundamentalFrequency.getFrameType() == FrameType.TONE)
        {
            mTone = Tone.fromValue(vectorC0.getInt(VECTOR_U1_ID));
            mToneAmplitude = vectorC1.getInt(VECTOR_U0_AD);
        }
        else
        {
            int b1 = (vectorC0.getInt(VECTOR_U0_B1_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B1_LOW);
            int b2 = (vectorC0.getInt(VECTOR_U0_B2_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B2_LOW);
            int b3 = (vectorC1.getInt(VECTOR_U1_B3_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B3_LOW);
            int b4 = (vectorC1.getInt(VECTOR_U1_B4_HIGH) << 3) + vectorC3.getInt(VECTOR_U3_B4_LOW);
            int b5 = (vectorC2.getInt(VECTOR_U2_B5_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B5_LOW);
            int b6 = (vectorC2.getInt(VECTOR_U2_B6_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B6_LOW);
            int b7 = (vectorC2.getInt(VECTOR_U2_B7_HIGH) << 1) + vectorC3.getInt(VECTOR_U3_B7_LOW);
            int b8 = (vectorC2.getInt(VECTOR_U2_B8_HIGH) << 2) + vectorC3.getInt(VECTOR_U3_B8_LOW);

            mModelParameters = new AMBEModelParameters(fundamentalFrequency, b1, b2, b3, b4, b5, b6, b7, b8,
                errorsC0, errorsC1, previousFrame);

            mLog.warn("Errors C0 [" + errorsC0 +
                "] C1 [" + errorsC1 +
                "] B0 [" + b0 +
                "] B1 [" + b1 +
                "] B2 [" + b2 +
                "] B3 [" + b3 +
                "] B4 [" + b4 +
                "] B5 [" + b5 +
                "] B6 [" + b6 +
                "] B7 [" + b7 +
                "] B8 [" + b8 + "]");
        }
    }

    /**
     * Extracts a bit vector from the underlying voice frame byte array
     * @param indexes to extract for the vector
     * @return binary frame vector
     */
    private BinaryFrame getVector(int[] indexes)
    {
        BinaryFrame vector = new BinaryFrame(indexes.length);

        int pointer = 0;

        for(int bit : indexes)
        {
            if(mFrame.get(bit))
            {
                vector.set(pointer);
            }

            pointer++;
        }

        return vector;
    }

    /**
     * Generates the bit modulation vector that is xor'd to vector C1 using U0 as the seed.
     * @param seed for the modulation vector
     * @return modulation vector bit sequence to descramble vector C1
     */
    public static BinaryFrame getModulationVector(int seed)
    {
        BinaryFrame modulationVector = new BinaryFrame(23);

        //alg 52
        int prX = 16 * seed;

        for(int x = 0; x < 23; x++)
        {
            //alg 53 - simplified [... - 65536 * floor((173 * pr(n-1) + 13849) / 65536)] to modulus operation
            prX = (173 * prX + 13849) % 65536;

            //alg 54 - values 32768 and above are a 1 and below is a 0 (default)
            if(prX >= 32768)
            {
                modulationVector.set(x);
            }
        }

        return modulationVector;
    }


    public static void main(String[] args)
    {
        System.out.println("Starting ...");

        AMBEModelParameters defaultParameters = new AMBEModelParameters();
//        String frame = "B9E881526173002A6B";  //Repeat x 2
//        String frame = "BEDDEA821EFD660C08";   //Repeat x lots
//        String frame = "36D246811293001597";   //Repeat x 5
//        String frame = "9D86E611567AF8F672";
        String frame = "954BE6500310B00777";
//        String frame = "F6948E13324A0F4AB7";

        byte[] data = new byte[frame.length() / 2];
        for(int x = 0; x < frame.length(); x += 2)
        {
            data[x / 2] = (byte)(0xFF & Integer.parseInt(frame.substring(x, x + 2), 16));
        }

        AMBEFrame ambeFrame = new AMBEFrame(data, defaultParameters);

        System.out.println("Finished");
    }
}
