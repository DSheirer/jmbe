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
import jmbe.iface.IAudioCodec;
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

    public void reset()
    {
        mPreviousFrame = new AMBEModelParameters();
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
            "C8C289B13313F92DF3",
            "CA1AA3032535CCF205",
            "BD06A3143615944BA6",
            "9F2681012523C67ED5",
            "6EFFA7075202C91D72",
            "7DEEA7273333DB2B37",
            "7DC3DC20362DB8FBAA",
            "4FCCA6354341C82E44",
            "7ECC871544169D6C25",
            "81A7E52607600652AB",
            "80B7E760254060279D",
            "B285E7702721447088",
            "A3A4C55660324532CD",
            "91A5C774036161079F",
            "8286E5164523065599",
            "FFE6A36653111CE7C9",
            "E563CF927310784188",
            "BD72A5E75232BCFB48",
            "6E268DD72403A993E1",
            "7E88AE3405D9500B38",
            "EEE5A16620206CF7EF",
            "6F348ED62060B9C3C5",
            "E6A8E55067160BDD4F",
            "B1DF84344297BED538",
            "80FC84376190BFA17F",
            "B5C9A113665FABCD87",
            "1DBEC25333082A6889",
            "2E8FC2315439285EB8",
            "1DBFC25333082968BB",
            "85E8A015663FDAFCC5",
            "965AE7533267A50436",
            "A54BE5335502B46263",
            "B659C7515015964732",
            "F366E4444550ED8AF6",
            "E074C7020530A8CFB3",
            "D345C7044040CC8BF7",
            "A448C5422631B17337",
            "A203B7CDD8DFAA35A6",
            "CB72A3062A3890CBEA",
            "2F2A5CD88FA56FF88D",
            "6F68643E818DA9B0B8",
            "5F696538A39FDB808E",
            "2F4F3236E3F9AA3893",
            "0E5F601398503FD38B",
            "3DE1054DB469EBCD47",
            "6BA2267D348A94F89B",
            "2A893A0705B781A06B",
            "59DBE0634D134FDBD1",
            "6BCAA226659AAF03FB",
            "3DBFE232325F184CAA",
            "829489E37B37060E35",
            "2C9278C256EB018FD9",
            "49B37E605B937FAC5B",
            "2FE5056F28D5F41CB7",
            "3DF6056D3AA5A41F85",
            "3CC4073A08E3975897",
            "2CC2263EB04DAFCE66",
            "3EF2043F935DEFDD75",
            "78ED2429E57FD341B7",
            "0C8E252F45FABD025F",
            "0EDA76665F6B9E00F0",
            "3BEC61207DE38A4D6D",
            "5BEC056DE55DA052A6",
            "49FE462DADD143CC8D",
            "2FB9062FA961A5B2EC",
            "1ACE42542AE1F83B4B",
            "0CBF3F17112B90CDD6",
            "B7666B2A7288648F79",
            "E4704C042E51F4AD4B",
            "5A9B0535961424A931",
            "D7006600D7772169C3",
            "E056400D491BE90809",
            "9005242B7C134F3B93",
            "C1103132469DFE3EA3",
            "E123203607E9FF4FE6",
            "C21242055F7339A6AE",
            "F53760EA0AA5F4F22C",
            "4A9E00BD5AB1F660CD",
            "4BEF251FE318962586",
            "78DD043DA21FC336B0",
            "0CB96639C4CD3459D2",
            "6EEB2318F6C3D3495B",
            "7FBE0112D49D4791DD",
            "6FBD4112DE47931C90",
            "2DB92419EF51E3C5E8",
            "48FE061B955BA300B4",
            "9337472E618BCCF6AD",
            "836107005E0CA8C221",
            "A05026050C0CFCD517",
            "F1122036258AFD6C81",
            "8612437E1765F8E953",
            "A66AE4521635D53521",
            "F5298270720F1001FA",
            "E47619FF9C2DE3D9B9",
            "E4052F7800A697FD81",
            "E25685002DB95D759D",
            "9A31E7001E4343CE36",
            "835DC33521CFB16DE9",
            "18883F9B70ED1FD853",
            "2EFE2C5F9BFD2EF2C1",
            "E06344E2C2ED0CB7B1",
            "F1510684F8379C2CFD",
            "A310666CBD7090047C",
            "8230466FAE51A1134D",
            "C5576549A279E09621",
            "9514220996235382DF",
            "A01425091D116C7CB2",
            "9326650D12EFDCD7CA",
            "B327450D31998EE6F8",
            "9105476F3489FDF1DE",
            "C254627A6C3BEB0809",
            "C56372C558F9522CCE",
            "D503408BF679A852CE",
            "956105FCC0452C4754",
            "A4430116F249C47C0D",
            "9726025BB4753483CC",
            "D36907A80CDF0B1F36",
            "B50F458D2E3FDF4042",
            "E57C429E669DFB8EE5",
            "C56C4F3B04001D1E22",
            "A479E4732434F45505",
            "D366E4130702B8AFA3",
            "4C0BE90164B12887FA",
            "EA3B83534206BCC301",
            "FB3DE210A62717FBDE",
            "E257C6013531E8FDC4",
            "95426243D98160A503",
            "D0050176C845F7DA60",
            "B54003529719E74D3D",
            "E6014473F371371AC3",
            "E07F22248078FCA5CF",
            "E37F2021824AFF91C8",
            "931D460390077CB063",
            "A118643228BE5126D1",
            "951C013362EAF4E753",
            "B10A09B15588612E6A",
            "B669A5317E8A16DF2C",
            "B17CC337139D802DC9",
            "A26DE004229DB26EB8",
            "936E81325E1141A093",
            "D6F2C7052F0B60D5D3",
            "B4A1A3650E55F0A06B",
            "E5E1866011D3D268CD",
            "B3A6E64074264453CC",
            "CEF48272223769E7EF",
            "B2178071711C1A9D3F",
            "C4112C19BE1DFA2B04",
            "C50561B929D787E418",
            "B100467CFC37C5244F",
            "A133655D8C01C03348",
            "A313663B8B31E4116C",
            "B233253DA589679937",
            "A026651B15CAFDF5D8",
            "B126473F15DECFD1BF",
            "C71B246E2C10648373",
            "A57B236872A4726984",
            "C26B45C93646BDF71F",
            "944AE6723415C54361",
            "FF6A82E2018DF74E65",
            "3BA88A9508E75E1CD9",
            "DB60E23252851716E5",
            "C83AC21438EF1A591F",
            "BF5C81545D0DED4339",
            "A4137DAD27BD6C68E7",
            "D0630822B9F95EFB59",
            "A6163B66D1921F1F21",
            "862604A5C12BCCBBB4",
            "9131677EE963C0247D",
            "A112465C9B35F0007F",
            "C140437894D5A5C88D",
            "A7722112F039F04F2D",
            "D54546399618F49310",
            "B104671F25CDFCE7CF",
            "A024643A12EF9C87F9",
            "B325673E769DDCE2CC",
            "A206473E128B8FF1FF",
            "B36303DE0D3166B92E",
            "8763258F9477096143",
            "F53265039607531BD7",
            "D50767726EAF0F8C40",
            "844626FE58AD2092D5",
            "B56425FC5AA95393E7",
            "F50761BB5897C3C51B",
            "E64C628C01F9F88CD7",
            "D56C4E0853201C0D25",
            "9479E6516424A45741",
            "A75AE5554736C43154",
            "D5AAE54343564B9B6B",
            "AC2987F3283DA0F690",
            "5767BD6772C05DC97E",
            "58F95BBB65F1EA8FDA",
            "6A9862BAD8917C3F33",
            "5DAC43079C05F32FA3",
            "7CBE6001A945E71DB5",
            "2CCE2624893C376978",
            "6EAF2142A19A13C38A",
            "6CAF61609B74A43A86",
            "3D9A2469CA62D2D4FF",
            "6F8D035282CF40F4CE",
            "3BF94254F61A868EE9",
            "3DBC643B2E7269CF42",
            "B6032CEC6D109DF4DC",
            "F742C34206CDAAD73A",
            "2A43D82973CBF781CC",
            "E28EE15201BD18C0A7",
            "D0BEE37027DE5CA792",
            "FA60E10201B32700F7",
            "ECE6A257531748979C",
            "CEF5825716647BE3FA",
            "FFD7A27717225C959E",
            "8304A04443490B9D7A",
            "F075E7066755BF9FC1",
            "B722A52021F77D8493",
            "A4EAA104647D9B8DB3",
            "B6DAA264074AA9BC96",
            "83FDA76462B0FED71C",
            "A468C7413764812053",
            "DED7A15753706A958C",
            "87E8A074066A8AB9C0",
            "B569C5006725F44357",
            "B678E7006654A44113",
            "8F05A3466463D61FE1",
            "91A4E47342204424CD",
            "8679E6727705813363",
            "B47AE4747227B44776",
            "8549C7503440A45012",
            "E839A2014457CCF455",
            "9C35A0043725B46DE2",
            "C21EBA817558B6F984",
            "D317C2E2BCBAAC9FCE",
            "21829C8861164D41E3",
            "8668E4531464956755",
            "A678E4711337F75167",
            "FAD5A9959EAFF69904",
            "A886A314919177D017",
            "8990A0505E2B0825A7",
            "38AA4FBA5F65C9373B",
            "3BFC6FC20D393CAEFB",
            "6DC9406A8C7B71A034",
            "6FE51211FDD72D8A46",
            "4D9D4166AA20F479A2",
            "09FA4345C61AA1DAAB",
            "2FCA67432B4ACE13F1",
            "6EAA216369471A105C",
            "0DE962DA4927041BEA",
            "7A9C61BE557825A8C7",
            "970EE2A76434CED1CC",
            "BCF580E6976B3B3A31",
            "0EA8CEE7BA5EC20FBE",
            "E9E7E40490FDB0F2CA",
            "DFC4E3003CF9DE0987",
            "5FFB3F8CE74A40EEEB",
            "0EC26097BDEDC60A63",
            "2EE1243DA15F8F8874",
            "3880115080BC0B4D09",
            "19800376A0FB1B7D3B",
            "0AA2215685DF0B2E08",
            "38910214A0B93F3E7A",
            "3B816052B9078FD546",
            "0AA14272BB55BCE704",
            "3A916270A8769FC226",
            "09B26225BA11ECA005",
            "2BA00330E5FC3C583C",
            "78D647706A2BB19F17",
            "7EF240501AD3F4E0CF",
            "7AC2463697A1B849A4",
            "2BB040158B77FCB557",
            "68C26437F6E4AC3ED3",
            "39932011B1EC784E2D",
            "2DFF6762C4D2C2C275",
            "2EFC6440D4F7D2C073",
            "5DBD226384A97080CC",
            "4FBE0165C3CA4392EB",
            "3DFC6743C482869776",
            "48AC06463ACC3818B7",
            "79E66772396AE1BE00",
            "19B12325E3CD282F7D",
            "2D8227403ADD20F453",
            "4FE161407CE0B0A09D",
            "7DE063630FB1B486CE",
            "2DA205651FDF06D553",
            "6CE067FA59AC78E9B5",
            "6DC666DFD074643954",
            "5A8A018CA71BDED46E",
            "0AE846E8937028B0B1",
            "4A9A22ECB17E9ED32F",
            "79AA00EED12BCED14C",
            "6A8922E8B43BDED31D",
            "5A9A23ECB17DBCD03F",
            "3EFD4664C397F4F315",
            "6BB80642D664749F55",
            "1C8E074831EDFC022F",
            "2CBC66083F0178CB31",
            "5DED022E3F7ECE8EEB",
            "0AFC70421C85E8590F",
            "3FFE2641AD2B313A5C",
            "0ECC63C997BB1B8E6A",
            "6B8A03FC830ACEE23D",
            "7BA923DEB13CBCD209",
            "2E9A5294F1F1AA02BE",
            "38AD5494D169B84A71",
            "7BAA00DFA76FCBC32B",
            "B26242AE23FB802550",
            "B2704B872308EDFAD8",
            "9579F4414427C50227",
            "874BE6414610874370",
            "DB1B81520117BC9325",
            "E80A811060768AE600",
            "E83BA1306270ECD202",
            "D7ABE54114733DD909",
            "F5AAE74150345FAA6E",
            "E699E50135360CE84B",
            "BF26820423729039C3",
            "AE34A1206722A57FD4",
            "8F05A1240451C30CF2",
            "B096E76744144472FF",
            "B2B7C76740726111AB",
            "8394C44566550451C9",
            "C6BBC54110375E8D3E",
            "BF0483142570E01FF4",
            "8C04A3306754867DD7",
            "CA2B826033178E8411",
            "DB1B81603064CCE322",
            "D90AA102512789C274",
            "954AE6213757E40767",
            "8668E7435227966101",
            "F588C52335267DCD7E",
            "C4AAE55536414F9B49",
            "F7A9E77317416D8D3F",
            "C909A0622126E9A004",
            "B0B4C7754574041799",
            "A2A7E75766612030DD",
            "2F65CC0D243680AE51",
            "F145C7266541E998E3",
            "D164C6442122BDABC4",
            "AE2483456752D42BF3",
            "F588C41316605CA92F",
            "E6A9E67173275FDB69",
            "C5BAC751136659DD4F",
            "EA2A822062138FC375",
            "9F35A1744761F519D7",
            "6CDD86061221F81C45",
            "4ECFA7267361EC1972",
            "5FDC84023623D85C56",
            "A2B7E644723057119B",
            "B1A4C746726070718D",
            "A287E76415471637AF",
            "CAE18671459F6BCC33",
            "A334DE827E286E9391",
            "1AF2A715435390A5A1",
            "0BF16FFD244F907CE2",
            "4A83C031644E33907D",
            "C256C41756169CFE94",
            "D365E71713029D88D2",
            "D5AAC47167627BAE48",
            "E81981073012CCD040",
            "E91B83217321DBC727",
            "DFC48043020238E78E",
            "FEF5A145607569D0F8",
            "CDE58045662668E0CF",
            "39C087103520B494D4",
            "2BF384347515A582B6",
            "3BC287367441A5E2D1",
            "A0A4E64206706037FF",
            "AC17A3175260863FF1",
            "BF36A2371132852AD1",
            "8C3780753422D60AB6",
            "D7AAC5430465299C58",
            "E48BE463655249B87A",
            "F5B9E54346701FDF2F",
            "83A7E456705360119D",
            "8094E75476375573BF",
            "A2A7C41456100441AA",
            "F374E7670600FECAD4",
            "C055C60565648C8AF1",
            "D164C74521329DB9C7",
            "C074E5056351AD89D5",
            "C347E4670631DFDE90",
            "E4B8E47704634DB93A",
            "AC27A0356163A419D4",
            "D6B8E56277204CFE5E",
            "F5BBC76417707C886F",
            "C698C663676048F96B",
            "E78BE66103664CAC4D",
            "A478C5037401967601",
            "F829A17020368CD433",
            "C908A2306343FDF447",
            "D83A833262219F8201",
            "FB29A0105062EC9005",
            "EB29A37350758CF437",
            "E6AAE44145025EEF0F",
            "9296C53417545432B8",
            "B1B5C6745566153498",
            "A294C776566323158D",
            "5DEC866557409C3D53",
            "6ECDA5253330C86F35",
            "7EDDA6055050DF0A23",
            "8579E5403275844305",
            "683286517667695AD8",
            "1F2C8744546635F57F",
            "6F5CE101701896F1D7",
            "1C1D7CEF762A267F5C",
            "29D3A7561737B2C5C1",
            "29E085301417F486B6",
            "3AF1A62776048493F1",
            "ECD58156171148E3D8",
            "F96CA7C02017B411DC",
            "4E348EE441608995C7",
            "8658C5402166A70563",
            "0C1DA646230015C66C",
            "1F0CA766246046B10A",
            "2C3F8466637125914D",
            "39E1A7372463A5D2F6",
            "2AC28717460594B591",
            "A56AC4402305925244",
            "9659C7204417F50407",
            "EE01C1D2473E3EAEC4",
            "6F158CC5303489B191",
            "EEF6834664125CD4BF",
            "AB91C14024D1B9EBE8",
            "9884E2629B3DF2480F",
            "9A30C6304E1050BF51",
            "A802C7100F1021E914",
            "80DDC544381F385A32",
            "9C598021F695A6E79F",
            "F42E1B9FF132CBD070",
            "6A89538F88B16B2903",
            "5EBE4101AF14C70EB3",
            "4D8F023084BD7395EA",
            "6E9B615476BBE9AA71",
            "B3374C447449900B44",
            "E047F4706747BD8CB3",
            "D164C73465409EDFC5",
            "A42486168A3E750533",
            "6AEC58CD9D3CE63A5D",
            "7C9F582D8893DE876F",
            "18FA2061CB900676B7",
            "6C8E4301DF55927BA3",
            "6F9E4272DA75F358D3",
            "B74102608438E03B1F",
            "A4564105204D6C1580",
            "861E0EE11541510097",
            "D7EFC1C16345720DC1",
            "E64DC9512F4F5A98BD",
            "91068100564E2DDA2D",
            "0ABB5E9F78338A760E",
            "5EF9727BF86C64A002",
            "38F864D986674B8190",
            "39CB65D986012C9387",
            "5EAC0337C4E860B5DA",
            "0CDF26159B2D207D0C",
            "92100668D7B930BD70",
            "E147031B7790788124",
            "D07A45DE6011DDE10A",
            "BCE3C293345BB756BE",
            "1DA9CD84EE59B60FAB",
            "5CCE1D9F48C6496A7F",
            "0B8F07B58CE74AC64F",
            "0CBC463929560FAB43",
            "2DBC073F37CF8E113D",
            "820527082F075F4FF5",
            "A206272C79335A5D95",
            "E25566A47D44644773",
            "B3321D24D04A6B138B",
            "E2613309E93C2336E6",
            "D3256103A39B21254D",
            "C2156141F28C64350E",
            "D1254027B7A8234559",
            "B7502150E479A02D1D",
            "B5632232A018A02D2C",
            "F62464167F8C3CEF65",
            "806327701818FCF260",
            "B16227362B7FCF9315",
            "E40646160FBF4F9937",
            "F4114476C1535648A2",
            "A6422352846FA10A4F",
            "F63745726CE878DF46",
            "C3562178169759B522",
            "B097D47266444503D8",
            "F788C66347355E895C",
            "DAF48B95DE9FA2BA25",
            "C298C334BD43412755",
            "87EEC050B55D53D47B",
            "D7AD8772B66393E7F0",
            "3DAC7B9B0ADA8D08A3",
            "5AAB339DC66F9E8549",
            "39BD6239C67744036A",
            "79AB3475B25256CA20",
            "83660442B3A4F45293",
            "A0110D27817E6D46FE",
            "E2D4C1041BE403CD2D",
            "93858646199CB6BBE6",
            "E2C0C071D71D0C5E8D",
            "E2A9A017C599B48C0A",
            "4FED1EFD1CB5784A6C",
            "7BEE4D75BE737B4562",
            "C7334622F227574EF4",
            "B125242F7E474C3E84",
            "9305265C5E266F0EF2",
            "D12214BB12A744679C",
            "B56BD4247714854323",
            "8678C7627527F74374",
            "D125C3D4D8DDAADC8E",
            "B5FAC3707DA15D53AD",
            "C29CC37166FD7C8797",
            "D3541DE8371DBC0081",
            "C2430A2298CA6BAB7F",
            "C4766F62F1B8CA3BD8",
            "A4043A47E3911F5F25",
            "E0636278A0B184DFA8",
            "F3366132F2BB146469",
            "E1160036DA63D4E867",
            "B3450511E0E2C021B6",
            "B323255D86B8248F25",
            "F546445F943DD0E221",
            "9026467D56FDACB39C",
            "C375E537707399CCE5",
            "8678E6113637F54727",
            "8649C4533535F00515",
            "85AA85A1FDD7BCCEBB",
            "B6211FA93800EB978C",
            "A62434F6C31BEBDCE3",
            "F5062D3A069484DB91",
            "9772193CE4EBE9E293",
            "E11547FEC6F79B1A20",
            "83620430793B8BC467",
            "C2302217548AFD7F91",
            "E57F68A63009B17029",
            "944BE6723406D55153",
            "9749C6501075E55016",
            "D90AA1024027BBC067",
            "D4388210660D7150DF",
            "E408A052257C0427F9",
            "E50AA252464F5514CF",
            "D4C1874342A3C03EA8",
            "D5F2A54361F5B228CE",
            "80A4E54504724051AF",
            "BDA5E412547DB9F640",
            "FFC68174507678D49A",
            "D4188273145D0714FF",
            "847AC5335415C57304",
            "D699E53323677CFC6B",
            "E78BC65227404E8A7D",
            "E49AE56765336DFF3F",
            "C4B8E40306760CB849",
            "93A5C76754550744CF",
            "9184C777557740719D",
            "685ACC094006DE46E1",
            "D700F966F8DB7B1493",
            "2DDE21FFA866B94435",
            "7FBE70558A10C40F97",
            "09CB6377C629A6DACF",
            "79AA2771F316548D13",
            "953C6FC60FD8F18BF9",
            "B89083210F581F01F2",
            "BDA484272AC30F4B2B",
            "B76B865238EA35994B",
            "806D81127B6656E5B4",
            "B14CA0541E03619791",
            "CED6A36731115CC29A",
            "18AB1EAB30D97B9801",
            "59FE71879E9FFCC5D3",
            "39C82236BED2731093",
            "3DFD24118C08230B3F",
            "4CBF61519865B30FE0",
            "D1523582AB26FB5CDB",
            "C0362120A81687FD13",
            "D3162260E874A4B977",
            "D3054362E0DE03526A",
            "E7024540F005205BA7",
            "C114602094AE13553B",
            "F3166242B2CE53506B",
            "A5700026B62BD34B79",
            "E5116662F2026438A7",
            "C6004545E335745F95",
            "E333422578670A94AD",
            "E147021A6682588216",
            "C2122F9255004F8A65",
            "C0273EA299FD366CF3",
            "8525406FCEBDE318A0",
            "D1253003CB55C4C876",
            "E2142305B953D4ED67",
            "82126458AB71803549",
            "8011473EC957D7074E",
            "80455730CB5F56FBEB",
            "E03621768A60A79F75",
            "D3274356A2A937064A",
            "F1360054BF44B78875",
            "F1264010929C30255F",
            "A6430153D669F0692E",
            "A30D6524C3456C9767",
            "861B6142F4CF1BDE9E",
            "951A2047EE70F81097",
            "C13D15C33E87AFC1E2",
            "E738739402E61C2270",
            "914C3E14668B18C886",
            "B72E0C960065346781",
            "A64BC4502327D55505",
            "F4198072026C44149C",
            "C804A6D31F19EA3912",
            "D120CC1647AE6188F9",
            "803318DDF205F62AA0",
            "863009373A0B71FBB5",
            "D043425B81C5A4BDD9",
            "A303675ED955E7053B",
            "B2542612D1F5A701B3",
            "D23721349A35D6AE52",
            "E33500749A2393DE40",
            "C32402778840B7DD77",
            "D117437780DC70666A",
            "D3044373E1DC335379",
            "C33503379837F5BD07",
            "A57303559439E70E2A",
            "B75323519659E03C0A",
            "A31C4566A3007FC775",
            "B10C4501A1360F9552",
            "E56E53DE44FCFDDED7",
            "A10829D2349E050859",
            "88552EA43E15D59C96",
            "83A82CB65C298B2BB5",
            "F45083153C767D6D33",
            "0DD58312148EC3AF3F",
            "85207F8B00E91C7EF1",
            "F47441E1A47128D949",
            "D071430B87B384F89D",
            "9320644FC977C4317E",
            "D3055365F39974017B",
            "D32400568B15F4CD51",
            "A6630157855F837B0E",
            "E45C470641E661F1A8",
            "F74D20CB28173D118E",
            "81290AE3509F55480A",
            "D0307FF77FBCAE7218",
            "F20624FBBA7D1EF16F",
            "95610053D27CF31A0A",
            "B7386204E7CC2CE899",
            "F5692606D693ADFE27",
            "E35F2221E10FDAB0C8",
            "A31C6516830278A043",
            "D53830D41A5CECBE3D",
            "A6E9E3404AF61D469F",
            "E2B8A02686F8D0BE5B",
            "BFA4A4153AC06E4D3F",
            "CAE1E5331933EE531D",
            "8B5985155FF69A2FA6",
            "4CEC2FFF58A049690B",
            "C2137FF35B9FAF077C",
            "C60063FD955DFF51DB",
            "F32D016C3D9B438D99",
            "876846E379223CFA91",
            "D63B03F46B48BC9C59",
            "F12E20181CBC7089BC",
            "F70E645ADC73DEA98B",
            "A349445EE53D986AF2",
            "F108205EA2146C0D6A",
            "D038014DB1374F087B",
            "E00D004D69BE478FBA",
            "E23D22094ABB42B9AF",
            "D528664B60CBE34A2E",
            "910A4E6A5D3D7B8D59",
            "A41D492C5AD158F1D2",
            "C70A20C25C7CF88A3C",
            "B579258034C98820C8",
            "E67F48C6540A915239",
            "B77AC4722034961547",
            "8479C5720137A34235",
            "BC24A3145645B50EB3",
            "8C0783343151D20BD4",
            "976AE7510456F12736",
            "B76BE7716014946372",
            "F057C7157627DDFA95",
            "C046E6357170BB8BF5",
            "847BC7522076844141",
            "8294C540070174728D",
            "E31E841152C6523E64",
            "876BC7511766946500",
            "844AE5553514E11354",
            "82B7E56537714022AB",
            "9385E54572116725FE",
            "F7A9E41006471EC81D",
            "A45AC7125302853741",
            "B45BE7523605D36725",
            "947AE7767631944371",
            "EDE5A27332026DE2DB",
            "A44AC7631772B44071",
            "B759E7611045943447",
            "36D246811293001597",
            "36D246811293001597"        };

        IAudioCodec audioCodec = new AMBEAudioCodec();

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
                    byte[] data = new byte[frame.length() / 2];
                    for(int x = 0; x < frame.length(); x += 2)
                    {
                        data[x / 2] = (byte)(0xFF & Integer.parseInt(frame.substring(x, x + 2), 16));
                    }

                    float[] samples = audioCodec.getAudio(data);
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
