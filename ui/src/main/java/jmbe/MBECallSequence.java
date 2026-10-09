/*
 * ******************************************************************************
 * Copyright (C) 2015-2026 Dennis Sheirer
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

package jmbe;

import com.fasterxml.jackson.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * MBE Call Sequence containing one or more voice frames with optional encryption parameters and optional from and to
 * radio identifiers.
 */
@JsonRootName("mbe_call")
@JsonPropertyOrder({"protocol", "version", "call_type", "from", "to", "encrypted", "system", "site", "frames"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MBECallSequence
{
    private int mVersion = 2;
    private String mProtocol;
    private String mFromIdentifier;
    private String mToIdentifier;
    private String mCallType;
    private String mSystem;
    private String mSite;
    private boolean mEncrypted;
    private List<VoiceFrame> mVoiceFrames = new ArrayList<>();

    /**
     * Constructs a call sequence
     */
    public MBECallSequence(String protocol)
    {
        mProtocol = protocol;
    }

    public MBECallSequence()
    {
        //no-arg constructor for faster jackson deserialization
    }

    /**
     * Protocol/format for the voice frames
     */
    @JsonProperty("protocol")
    public String getProtocol()
    {
        return mProtocol;
    }

    public void setProtocol(String protocol)
    {
        mProtocol = protocol;
    }

    /**
     * Gets the version number for the mbe file structure.
     * Should be incremented only on breaking structural changes, not minor additions.
     */
    @JsonProperty("version")
    public Integer getVersion()
    {
        return mVersion;
    }

    /**
     * Sets the version when loaded from a file.
     * @param version
     */
    public void setVersion(int version)
    {
        mVersion = version;
    }

    /**
     * Indicates if this sequences contains any audio frames
     */
    @JsonIgnore
    public boolean hasAudio()
    {
        return mVoiceFrames.size() > 4;
    }

    /**
     * Indicates if this call sequence is encrypted
     */
    @JsonProperty("encrypted")
    public boolean isEncrypted()
    {
        return mEncrypted;
    }

    /**
     * Sets the encrypted status of this call sequence
     *
     * @param encrypted status
     */
    public void setEncrypted(boolean encrypted)
    {
        mEncrypted = encrypted;
    }

    /**
     * Sets the from radio identifier
     *
     * @param from id
     */
    public void setFromIdentifier(String from)
    {
        if(from != null && !from.isEmpty() && !from.contentEquals("0"))
        {
            mFromIdentifier = from;
        }
    }

    /**
     * Radio identifier that originated the call
     *
     * @return from id
     */
    @JsonProperty("from")
    public String getFromIdentifier()
    {
        return mFromIdentifier;
    }

    /**
     * Sets the to radio identifier
     *
     * @param to id
     */
    public void setToIdentifier(String to)
    {
        if(to != null && !to.isEmpty() && !to.contentEquals("0"))
        {
            mToIdentifier = to;
        }
    }

    /**
     * Radio identifier that received the call
     *
     * @return to id
     */
    @JsonProperty("to")
    public String getToIdentifier()
    {
        return mToIdentifier;
    }

    /**
     * Call type
     *
     * @param type from GROUP, INDIVIDUAL, or TELEPHONE INTERCONNECT
     */
    public void setCallType(String type)
    {
        mCallType = type;
    }

    /**
     * Sets the call type
     *
     * @return call type
     */
    @JsonProperty("call_type")
    public String getCallType()
    {
        return mCallType;
    }

    /**
     * System name defined by the user
     * @return
     */
    @JsonProperty("system")
    public String getSystem()
    {
        return mSystem;
    }

    /**
     * Sets the system name
     * @param system
     */
    public void setSystem(String system)
    {
        mSystem = system;
    }

    /**
     * Site name defined by the user
     * @return
     */
    @JsonProperty("site")
    public String getSite()
    {
        return mSite;
    }

    /**
     * Sets the site name
     * @param site
     */
    public void setSite(String site)
    {
        mSite = site;
    }

    /**
     * Ordered list of voice frames for the call sequence
     *
     * @return list of unencrypted or encrypted audio frames
     */
    @JsonProperty("frames")
    public List<VoiceFrame> getVoiceFrames()
    {
        return mVoiceFrames;
    }

    public void setVoiceFrames(List<VoiceFrame> voiceFrames)
    {
        mVoiceFrames = voiceFrames;
    }

    /**
     * Adds the voice frame to the sequence
     */
    public void add(VoiceFrame voiceFrame)
    {
        mVoiceFrames.add(voiceFrame);
    }

    /**
     * Adds an encrypted audio frame to this call sequence
     *
     * @param timestamp of the audio frame
     * @param frame of hexadecimal values representing the transmitted audio frame and ecc bits
     * @param algorithm identifier for encryption
     * @param keyid identifying which encryption key was used by the radio which may have multiple keys
     * @param messageIndicator to seed the key generator
     */
    public void addEncryptedVoiceFrame(long timestamp, String frame, int algorithm, int keyid, String messageIndicator)
    {
        add(new VoiceFrame(timestamp, frame, algorithm, keyid, messageIndicator));
    }
}