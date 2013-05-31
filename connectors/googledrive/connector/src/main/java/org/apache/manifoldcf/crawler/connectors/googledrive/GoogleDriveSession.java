/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.manifoldcf.crawler.connectors.googledrive;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import java.util.Map;



import java.util.HashMap;
import java.util.HashSet;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author andrew
 */
public class GoogleDriveSession {

    private static String APPNAME = "Searchbox Google drive Connector";
    private Drive drive;
    private static HttpTransport HTTP_TRANSPORT;
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    GoogleDriveSession(Map<String, String> parameters) {
        try {

            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            GoogleCredential credentials = new GoogleCredential.Builder().setClientSecrets(parameters.get("clientid"), parameters.get("clientsecret"))
                    .setJsonFactory(JSON_FACTORY).setTransport(HTTP_TRANSPORT).build().setRefreshToken(parameters.get("refreshtoken"));

            drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials).setApplicationName(APPNAME).build();
        } catch (Exception ex) {
            Logger.getLogger(GoogleDriveSession.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Map<String, String> getRepositoryInfo() throws IOException {
        Map<String, String> info = new HashMap<String, String>();
        info.put("Application Name", drive.getApplicationName());
        info.put("Base URL", drive.getBaseUrl());
        return info;
    }

    HashSet<String> getSeeds(String googleDriveQuery) throws IOException {
        HashSet<String> ids = new HashSet<String>();
        Drive.Files.List request;

        request = drive.files().list().setQ(googleDriveQuery);

        do {
            try {
                FileList files = request.execute();
                for (File f : files.getItems()) {
                    ids.add(f.getId());
                }
                request.setPageToken(files.getNextPageToken());
            } catch (IOException e) {
                System.out.println("An error occurred: " + e);
                request.setPageToken(null);
            }
        } while (request.getPageToken() != null
                && request.getPageToken().length() > 0);
        return ids;
    }

    public File getObject(String id) throws IOException {
        File file = drive.files().get(id).execute();
        return file;
    }

    List<String> getChildren(String nodeId) throws IOException {
        ArrayList<String> ids = new ArrayList<String>();
        Drive.Files.List request = drive.files().list().setQ("'" + nodeId + "' in parents");

        do {
            try {
                FileList files = request.execute();
                for (File f : files.getItems()) {
                    ids.add(f.getId());
                }
                request.setPageToken(files.getNextPageToken());
            } catch (IOException e) {

                request.setPageToken(null);
            }
        } while (request.getPageToken() != null
                && request.getPageToken().length() > 0);
        return ids;
    }

    String getUrl(File googleFile, String exportType) {
        if (googleFile.containsKey("fileSize")) {
            return googleFile.getDownloadUrl();
        } else {
            return googleFile.getExportLinks().get(exportType);
        }
    }

    ByteArrayOutputStream getGoogleDriveOutputStream(String documentURI) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        MediaHttpDownloader downloader =
                new MediaHttpDownloader(HTTP_TRANSPORT, drive.getRequestFactory().getInitializer());
        downloader.setDirectDownloadEnabled(false);
        downloader.download(new GenericUrl(documentURI), outputStream);
        return outputStream;
    }
}
