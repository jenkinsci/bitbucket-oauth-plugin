package org.jenkinsci.plugins.api;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Represents Bitbuckets workspaces accessible response
 *
 * https://developer.atlassian.com/cloud/bitbucket/rest/api-group-workspaces/#api-user-workspaces-get
 */
public class BitbucketWorkspaceAccessibleResponse
{
    @SerializedName("next")
    private String next;

    @SerializedName("values")
    private List<BitbucketWorkspaceAccessible> teamsList;

    public List<BitbucketWorkspaceAccessible> getTeamsList()
    {
        return teamsList;
    }

    public void setTeamsList(List<BitbucketWorkspaceAccessible> teamsList)
    {
        this.teamsList = teamsList;
    }

    public String getNext()
    {
        return next;
    }

    public void setNext(String next)
    {
        this.next = next;
    }
}
