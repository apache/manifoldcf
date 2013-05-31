/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.manifoldcf.crawler.connectors.googledrive;

/**
 *
 * @author andrew
 */
public class GoogleDriveConfig {

    public static final String CLIENT_ID_PARAM = "clientid";
    public static final String CLIENT_SECRET_PARAM = "clientsecret";
    public static final String REFRESH_TOKEN_PARAM = "refreshtoken";
    public static final String REPOSITORY_ID_DEFAULT_VALUE = "googledrive";
    public static final String GOOGLEDRIVE_QUERY_PARAM = "googledriveQuery";
    public static final String GOOGLEDRIVE_QUERY_DEFAULT = "mimeType='application/vnd.google-apps.folder' and trashed=false";
}
