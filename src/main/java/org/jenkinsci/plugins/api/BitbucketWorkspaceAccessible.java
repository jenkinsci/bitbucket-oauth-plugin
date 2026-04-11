package org.jenkinsci.plugins.api;

import com.google.gson.annotations.SerializedName;

public class BitbucketWorkspaceAccessible
{
    @SerializedName("administrator")
    private boolean administrator;

    @SerializedName("workspace")
    private Workspace workspace;

    public boolean isAdministrator() {
        return administrator;
    }

    public void setAdministrator(boolean administrator) {
        this.administrator = administrator;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    public static class Workspace {
        @SerializedName("slug")
        private String slug;

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }
    }

}
