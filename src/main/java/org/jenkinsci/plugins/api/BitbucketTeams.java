package org.jenkinsci.plugins.api;

import com.google.gson.annotations.SerializedName;

public class BitbucketTeams
{
    @SerializedName("slug")
    String username;

    @SerializedName("type")
    String type;

    @SerializedName("name")
    String displayName;

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }
}
