/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.manifoldcf.crawler.connectors.googledrive;

import org.apache.manifoldcf.core.common.*;
import org.apache.manifoldcf.connectorcommon.common.*;

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
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.security.GeneralSecurityException;

/**
 *
 * @author andrew
 */
public class GoogleDriveSession {

  private static String APPNAME = "ManifoldCF GoogleDrive Connector";
  
  private Drive drive;
  private HttpTransport HTTP_TRANSPORT;
  
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();
  
  /** Constructor.  Create a session.
  */
  public GoogleDriveSession(String clientId, String clientSecret, String refreshToken)
    throws IOException, GeneralSecurityException {
    HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

    GoogleCredential credentials = new GoogleCredential.Builder().setClientSecrets(clientId, clientSecret)
        .setJsonFactory(JSON_FACTORY).setTransport(HTTP_TRANSPORT).build().setRefreshToken(refreshToken);

    drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials).setApplicationName(APPNAME).build();
  }

  /** Close session.
  */
  public void close() {
    // MHL - figure out what is needed
  }

  /** Obtain repository information.
  */
  public Map<String, String> getRepositoryInfo() throws IOException {
    Map<String, String> info = new HashMap<String, String>();
    info.put("Application Name", drive.getApplicationName());
    info.put("Base URL", drive.getBaseUrl());
    // We need something that will actually cause a back-and-forth to the server!
    drive.files().get("").execute();
    return info;
  }

  /** Get the list of matching root documents, e.g. seeds.
  */
  public void getSeeds(XThreadStringBuffer idBuffer, String googleDriveQuery)
    throws IOException, InterruptedException {
    Drive.Files.List request;

    request = drive.files().list().setQ(googleDriveQuery);

    do {
      FileList files = request.execute();
      for (File f : files.getItems()) {
        idBuffer.add(f.getId());
      }
      request.setPageToken(files.getNextPageToken());
    } while (request.getPageToken() != null
        && request.getPageToken().length() > 0);
  }

  /** Get an individual document.
  */
  public File getObject(String id) throws IOException {
    File file = drive.files().get(id).execute();
    return file;
  }

  /** Get the list of child documents for a document.
  */
  public void getChildren(XThreadStringBuffer idBuffer, String nodeId)
    throws IOException, InterruptedException {
    Drive.Files.List request = drive.files().list().setQ("'" + nodeId + "' in parents");

    do {
      FileList files = request.execute();
      for (File f : files.getItems()) {
        idBuffer.add(f.getId());
      }
      request.setPageToken(files.getNextPageToken());
    } while (request.getPageToken() != null
        && request.getPageToken().length() > 0);
  }


  /** Get a stream representing the specified document.
  */
  public void getGoogleDriveOutputStream(XThreadInputStream inputStream, String documentURI) throws IOException {
    // Create an object that implements outputstream but pushes everything through to the designated input stream
    OutputStream outputStream = new XThreadOutputStream(inputStream);
    try {
      MediaHttpDownloader downloader =
          new MediaHttpDownloader(HTTP_TRANSPORT, drive.getRequestFactory().getInitializer());
      downloader.setDirectDownloadEnabled(false);
      downloader.download(new GenericUrl(documentURI), outputStream);
    } finally {
      // Make sure it is closed and flushed
      outputStream.close();
    }
  }
  
}
