/*
 * ******************************************************************************
 * Copyright (C) 2015-2019 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * *****************************************************************************
 */

package jmbe.codec.ambe;

import jmbe.codec.MBEModelParameters;
import jmbe.codec.MBESynthesizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AMBESynthesizer extends MBESynthesizer
{
    private final static Logger mLog = LoggerFactory.getLogger(AMBESynthesizer.class);

    private ToneGenerator mToneGenerator = new ToneGenerator();
    private AMBEModelParameters mPreviousFrame = new AMBEModelParameters();

    /**
     * AMBE synthesizer producing 8 kHz 16-bit audio from AMBE audio (voice/tone) frames
     */
    public AMBESynthesizer()
    {
    }

    /**
     * Previous AMBE frame parameters
     *
     * @return parameters
     */
    @Override
    public MBEModelParameters getPreviousFrame()
    {
        return mPreviousFrame;
    }

    /**
     * Generates 160 samples (20 ms) of tone audio
     *
     * @param toneParameters to use in generating the tone frame
     * @return samples
     */
    public float[] getTone(ToneParameters toneParameters)
    {
        return mToneGenerator.generate(toneParameters);
    }

    /**
     * Generates 160 samples (20 ms) of audio from the ambe frame.  Can decode both audio and tone frames and handles
     * frame repeats and white noise generation when error rate exceeds thresholds.
     *
     * @param frame of audio
     * @return decoded audio samples
     */
    public float[] getAudio(AMBEFrame frame)
    {
        float[] audio = null;

        if(frame.isToneFrame())
        {
            if(frame.getToneParameters().isValidTone())
            {
                audio = getTone(frame.getToneParameters());
            }
            else
            {
                mPreviousFrame.setRepeatCount(mPreviousFrame.getRepeatCount());

                if(!mPreviousFrame.isMaxFrameRepeat())
                {
                    audio = getVoice(mPreviousFrame);
                }
                else
                {
                    //Frame muting procedure
                    mPreviousFrame = new AMBEModelParameters();
                    audio = getWhiteNoise();
                }
            }
        }
        else
        {
            AMBEModelParameters parameters = frame.getVoiceParameters(mPreviousFrame);

            if(!parameters.isMaxFrameRepeat())
            {
                if(parameters.isErasureFrame())
                {
                    audio = getWhiteNoise();
                }
                else
                {
                    audio = getVoice(parameters);
                }

                mPreviousFrame = parameters;
            }
            else
            {
                //Frame muting procedure
                mPreviousFrame = new AMBEModelParameters();
                audio = getWhiteNoise();
            }
        }

        if(audio == null)
        {
            audio = new float[SAMPLES_PER_FRAME];
        }

        return audio;
    }

    /**
     * Test harness
     *
     * @param args not used
     */
    public static void main(String[] args)
    {
        String[] frames = {
            "A478E4520114C02767", "FDC5825757247CC3AF", "EEC4A07775103C87AB", "DDE7805756155CE7FB", "CEF6A27577146C87AB",
            "D589C672535418EB6F", "DAC3A9B37061FC1B84", "95CEE214903D52F54C", "D4D3854240B5F5698D", "8692C06227CD730F61",
            "914FC07313B9D60C9B", "E23D853375B5605A45", "B520A63400C308A2F5", "9521854245935FE093", "39A9C57621A5683532",
            "2BAAC61447A75A2754", "A431A42744B478B3F7", "A4108603008319D5C5", "A723A62567C659E781", "F32F863266A1543D73",
            "A612856202F61A83D5", "B631A540478139E3F3", "A307A10625092BC83B", "8E7FC14317E75E8A26", "9D6EE20113A319FA45",
            "CF3C8623158A9ACBEE", "B0378306017C6DCE3A", "098AE57467D17F7251", "7ADAA17325BBCF148A", "4AD9A1136298AD11CD",
            "69FA821744DCBC03DC", "D62AA14331681466CD", "C870E21127E65221E7", "8931A61321CDC3160D", "CE2D8636029F88BBBB",
            "D21DA47363B0770C50", "D12E875524E6456952", "F740A3446C475A3F71", "8205E1262AD4E82701", "1A7479A8E44475EFC9",
            "2A671E26E8B61C3B88", "3F4147A4E0690D6BBA", "5B1606C2E0CFA942EA", "6B0427E2B3CDCA40EB", "2D7245C1B65D4B09AA",
            "196760E5D5925F5157", "5B26233EF586177BF6", "3D4264D7845A4B2BD9", "6D1323B7F322AD7807", "3F0E64E445ED1FA684",
            "2B7C49A745EFB59C67", "7B49E56306B2C1FE69", "6AE09862F093228ECD", "2C543FFA41D9EEAEFE", "2A3759A53DB3F9C2FF",
            "29445F05E25DCEF583", "5F32063E922933006B", "7A1C2B2C32ADDA92F3", "1B6D6F0B11A258E37B", "6C800D988D411C8277",
            "F71FE107B62BF90E33", "A05EA1415D52468697", "6A173CBBA24C86BE05", "2C270B3DFD61DBBDD3", "3A2C7F00CD43B1DB2A",
            "3B0C3F02C6FD617123", "494F7B27C1C4B507CC", "4A7A427A6CBB9728B3", "685A4A501E4CFF812B", "1B2B2D573B025E8495",
            "0EC54C065398684F45", "2E0C2A731FAF1AEB0B", "32020AE84751440422", "32020AE84751440422", "32020AE84751440422",
            "32020AE84751440422", "48AF0A807F178DAB21", "4AD8A22475EDBD77EB", "F761E261238CCFF32C", "A603854331B038D597",
            "F55080071934480A11", "0D3E3CCE6FC0A69163", "39784666CEAD4FEF05", "1C7E4120CA061890FA", "6F7B062E98205E5BF6",
            "193F075E86293FB8DE", "4C0867747A7A259BBE", "6E4F67183C62C0075F", "19385E7524C8B84899", "6CF30DA665C0C6BB34",
            "1DE08142CC74F969FE", "DEF7E1177999FC5CD6", "F946A026D2E79F1A19", "E2C5E1354BA7318F7B", "6D4979CBD3125C430D",
            "38496F7CAF7D202198", "1A3C5E30BD55A7C958", "1F080A51B617566BE9", "48946C44A60A431FAD", "5F976A443C483C93E3",
            "6ED32B2B71AC7DB73D", "5FE02B1C77F85EC55D", "4EA52FDB16DE642097", "0DF7A23137B9828E08", "1FE4C2634D26404605",
            "D761C34054FDCEC65C", "D760C344559DD9B13B", "EAD18677529B3FD953", "C9C3871534CC4BDC55", "CAE2841371E8699F73",
            "D751E06101AA88E409", "D18CC25000B94AA6E1", "6E485989F6547B551A", "5909087A8E43D06713", "7C1C4C1AF6016686C3",
            "1F48087818E38B278F", "6E194C185AC82F0513", "0E19085294334728CB", "6C7C6D567A979ABAA2", "5C284F194BAC2D2410",
            "7F7B6E31C13E846D10", "092C23D1D33796F3F7", "494F46B7D25A03D61F", "082C21B5D60297F1C7", "0BE53864E454F33A06",
            "0FD603A64A8C7A01CC", "48B042A6392BA96EFE", "28C3030A793AC6523E", "7C84434C5BED34282F", "6DB5432E5FEC303F3F",
            "4DA2424CE0046FCABE", "7F686293B2A577BC97", "081B01D108EE8B0213", "7E7B5C57C279A43C73", "4B1965BF9370F419DB",
            "5AA27C622DA569AB6D", "6DD34E967D2D11656E", "1FE48272368DF59F5D", "A490E25163DC724E75", "8681E36453EF152A51",
            "79DA832047ADDA15CB", "0BBAC72220A3590132", "6C4DC274221CE7D7D7", "6B7D06D0CCE4B34965", "793A23149891BF8F8B",
            "3C7C2315E1AFC97CA7", "7BC14764A6E7CD3CD2", "39830024969F1C290F", "7BF24443C0D0EC0CD2", "0AD41961C475A10F01",
            "3BE43863D645A10933", "5A915C5018F71EBC6A", "2DD56D5242BA1A4C56", "2DC12D4090A9F676DF", "6F9046C0A11893C0F6",
            "2DC22084C14647B00E", "3EF322A6B03357E52E", "4FB36686F76BD383B1", "7A92250905B8B1CDDE", "4CF12A3816B858A64D",
            "6BAD08A22E41DE9C03", "D771E324649FCEC62F"
        };


        AMBESynthesizer synthesizer = new AMBESynthesizer();

        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            8000.0f, 16, 1, 2, 8000.0f, false);
        DataLine.Info datalineinfo = new DataLine.Info(SourceDataLine.class, audioFormat);

        if(AudioSystem.isLineSupported(datalineinfo))
        {
            try
            {
                SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
                sourceDataLine.open(audioFormat);

                boolean started = false;

                for(String frame : frames)
                {
                    AMBEFrame ambe = new AMBEFrame(frame);
                    float[] samples = synthesizer.getAudio(ambe);
                    ByteBuffer converted = ByteBuffer.allocate(samples.length * 2);
                    converted = converted.order(ByteOrder.LITTLE_ENDIAN);

                    for(float sample : samples)
                    {
                        converted.putShort((short)(sample * Short.MAX_VALUE));
                    }

                    byte[] bytes = converted.array();
                    sourceDataLine.write(bytes, 0, bytes.length);

                    if(!started)
                    {
                        sourceDataLine.start();
                        started = true;
                    }
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            System.out.println("Audio Format Not Supported by Host Audio System: " + audioFormat);
        }
    }
}
