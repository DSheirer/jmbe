/*
 * ******************************************************************************
 * Copyright (C) 2015-2020 Dennis Sheirer
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

package io.github.dsheirer.jmbe.creator.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * GitHub release
 */
public class Release
{
    private Version mVersion;
    private JsonObject mJsonObject;

    /**
     * Constructs an instance
     * @param version of the release
     * @param jsonObject that represents the release
     */
    public Release(Version version, JsonObject jsonObject)
    {
        mVersion = version;
        mJsonObject = jsonObject;
    }

    /**
     * Version stamp for the release
     */
    public Version getVersion()
    {
        return mVersion;
    }

    /**
     * JsonObject for the release
     */
    public JsonObject getJsonObject()
    {
        return mJsonObject;
    }

    /**
     * Name of the release
     */
    public String getName()
    {
        return getTag("name");
    }

    /**
     * Assets URL
     */
    public String getAssetsUrl()
    {
        return getTag("assets_url");
    }

    /**
     * HTML URL
     */
    public String getHtmlUrl()
    {
        return getTag("html_url");
    }

    /**
     * Zip File Download URL
     */
    public String getDownloadUrl()
    {
        return getTag("zipball_url");
    }

    /**
     * Access the string contents of a json tag
     * @param tagName to access
     * @return string value or null
     */
    private String getTag(String tagName)
    {
        if(mJsonObject != null)
        {
            JsonElement element = mJsonObject.get(tagName);

            if(!element.isJsonNull())
            {
                return element.toString().replace("\"", "");
            }
        }

        return null;
    }

    /**
     * Indicates if the release has a download URL
     */
    public boolean hasDownloadUrl()
    {
        return getDownloadUrl() != null;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("GitHub Release - Name:").append(getName());
        sb.append(" Version: ").append(getVersion().toString());
        sb.append(" HTML URL:").append(getHtmlUrl());
        sb.append(" Download URL:").append(getDownloadUrl());
        return sb.toString();
    }
}