package org.apache.manifoldcf.crawler.connectors.alfrescowebscript.tests;


import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CMISUtils {

  private String serviceUrl = "http://localhost:8080/alfresco/api/-default-/public/cmis/versions/1.1/atom";
  private String user;
  private String password;
  private String folderName = "";

  private Session session = null;
  public Session getSession() {
    if (session == null) {
      SessionFactory factory = SessionFactoryImpl.newInstance();
      Map<String, String> parameter = new HashMap<String, String>();
      parameter.put(SessionParameter.USER, getUser());
      parameter.put(SessionParameter.PASSWORD, getPassword());
      parameter.put(SessionParameter.ATOMPUB_URL, getServiceUrl());
      parameter.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
      List<Repository> repositories = factory.getRepositories(parameter);
      this.session = repositories.get(0).createSession();
    }
    return this.session;
  }

  public Document createDocument(String docName, String contentType) {
    Session session = getSession();
    Folder folder = (Folder) session.getObjectByPath("/" + getFolderName());
    String timeStamp = new Long(System.currentTimeMillis()).toString();
    String filename = docName + " (" + timeStamp + ")";
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put(PropertyIds.OBJECT_TYPE_ID, contentType);
    properties.put(PropertyIds.NAME, filename);
    properties.put(PropertyIds.IS_MAJOR_VERSION, true);
    properties.put(PropertyIds.IS_LATEST_MAJOR_VERSION, true);

    String docText = "Lorem ipsum";
    byte[] content = docText.getBytes();
    InputStream stream = new ByteArrayInputStream(content);
    ContentStream contentStream = session.getObjectFactory().createContentStream(
        filename,
        Long.valueOf(content.length),
        "text/plain",
        stream);
    Document doc = folder.createDocument(
        properties,
        contentStream,
        VersioningState.MAJOR);

    return doc;
  }

  public String getServiceUrl() {
    return serviceUrl;
  }

  public void setServiceUrl(String serviceUrl) {
    this.serviceUrl = serviceUrl;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getFolderName() {
    return folderName;
  }

  public void setFolderName(String folderName) {
    this.folderName = folderName;
  }
}
