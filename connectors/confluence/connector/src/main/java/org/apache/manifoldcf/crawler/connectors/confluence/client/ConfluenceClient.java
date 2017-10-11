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

package org.apache.manifoldcf.crawler.connectors.confluence.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.connectorcommon.common.InterruptibleSocketFactory;
import org.apache.manifoldcf.connectorcommon.interfaces.KeystoreManagerFactory;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.connectors.confluence.exception.ConfluenceException;
import org.apache.manifoldcf.crawler.connectors.confluence.model.Attachment;
import org.apache.manifoldcf.crawler.connectors.confluence.model.ConfluenceResource;
import org.apache.manifoldcf.crawler.connectors.confluence.model.ConfluenceResponse;
import org.apache.manifoldcf.crawler.connectors.confluence.model.ConfluenceUser;
import org.apache.manifoldcf.crawler.connectors.confluence.model.Label;
import org.apache.manifoldcf.crawler.connectors.confluence.model.MutableAttachment;
import org.apache.manifoldcf.crawler.connectors.confluence.model.MutablePage;
import org.apache.manifoldcf.crawler.connectors.confluence.model.Page;
import org.apache.manifoldcf.crawler.connectors.confluence.model.Space;
import org.apache.manifoldcf.crawler.connectors.confluence.model.Spaces;
import org.apache.manifoldcf.crawler.connectors.confluence.model.builder.ConfluenceResourceBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

/**
 * <p>
 * ConfluenceClient class
 * </p>
 * <p>
 * This class is intended to be used to interact with Confluence REST API
 * </p>
 * <p>
 * There are some methods that make use of the Confluence JSON-RPC 2.0 API, but
 * until all the methods are ported to the new REST API, we will have to use
 * them to leverage all the features provided by Confluence
 * </p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 *
 */
public class ConfluenceClient {

  private static final String VIEW_PERMISSION = "view";
  
  private static final String CONTENT_PATH = "/rest/api/content";
  private static final String AUTHORITY_PATH = "/rpc/json-rpc/confluenceservice-v2/";
  private static final String EXPANDABLE_PARAMETERS = "expand=body.view,metadata.labels,space,history,version";
  private static final String CHILD_ATTACHMENTS_PATH = "/child/attachment/";
  private static final String LABEL_PATH = "/label";

  private Logger logger = LoggerFactory.getLogger(ConfluenceClient.class);

  private String protocol;
  private Integer port;
  private String host;
  private String path;
  private String username;
  private String password;

  private CloseableHttpClient httpClient;
  private HttpClientContext httpContext;

  /**
   * <p>Creates a new client instance using the given parameters</p>
   * @param protocol the protocol
   * @param host the host
   * @param port the port
   * @param path the path to Confluence instance
   * @param username the username used to make the requests. Null or empty to use anonymous user
   * @param password the password
   * @throws ManifoldCFException 
   */
  public ConfluenceClient(String protocol, String host, Integer port,
      String path, String username, String password) throws ManifoldCFException {
    this.protocol = protocol;
    this.host = host;
    this.port = port;
    this.path = path;
    this.username = username;
    this.password = password;

    connect();
  }

  /**
   * <p>Connect methods used to initialize the underlying client</p>
   * @throws ManifoldCFException 
   */
  private void connect() throws ManifoldCFException {

    int socketTimeout = 900000;
      int connectionTimeout = 60000;

      javax.net.ssl.SSLSocketFactory httpsSocketFactory = KeystoreManagerFactory.getTrustingSecureSocketFactory();
      SSLConnectionSocketFactory myFactory = new SSLConnectionSocketFactory(new InterruptibleSocketFactory(httpsSocketFactory,connectionTimeout),
        NoopHostnameVerifier.INSTANCE);

      PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
          .register("http", PlainConnectionSocketFactory.getSocketFactory())
          .register("https", myFactory)
          .build());
      poolingConnectionManager.setDefaultMaxPerRoute(1);
      poolingConnectionManager.setValidateAfterInactivity(2000);
      poolingConnectionManager.setDefaultSocketConfig(SocketConfig.custom()
        .setTcpNoDelay(true)
        .setSoTimeout(socketTimeout)
        .build());


      RequestConfig.Builder requestBuilder = RequestConfig.custom()
        .setCircularRedirectsAllowed(true)
        .setSocketTimeout(socketTimeout)
        .setExpectContinueEnabled(true)
        .setConnectTimeout(connectionTimeout)
        .setConnectionRequestTimeout(socketTimeout);


      httpClient = HttpClients.custom()
        .setConnectionManager(poolingConnectionManager)
        .disableAutomaticRetries()
        .setDefaultRequestConfig(requestBuilder.build())
        .setRequestExecutor(new HttpRequestExecutor(socketTimeout))
        .setRedirectStrategy(new LaxRedirectStrategy())
        .build();
      
     }

  /**
   * <p>Close the client. No further requests can be done</p>
   */
  public void close() {
    if (httpClient != null) {
      try {
        httpClient.close();
      } catch (IOException e) {
        logger.debug("Error closing http connection. Reason: {}",
            e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /**
   * <p>Check method used to test if Confluence instance is up and running</p>
   * 
   * @return a {@code Boolean} indicating whether the Confluence instance is alive or not
   * 
   * @throws Exception
   */
  public boolean check() throws Exception {
    HttpResponse response;
    try {
      if (httpClient == null) {
        connect();
      }

      String url = String.format(Locale.ROOT, "%s://%s:%s%s%s?limit=1", protocol, host,
          port, path, CONTENT_PATH);
      logger.debug(
          "[Processing] Hitting url: {} for confluence status check fetching : ",
          "Confluence URL", sanitizeUrl(url));
      HttpGet httpGet = createGetRequest(url);
      response = httpClient.execute(httpGet);
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != 200)
        throw new Exception(
            "[Checking connection] Confluence server appears to be down");
      else
        return true;
    } catch (IOException e) {
      logger.warn(
          "[Checking connection] Confluence server appears to be down",
          e);
      throw new Exception("Confluence appears to be down", e);
    }
  }

  /**
   * <p>Check method used to test if Confluence instance is up and running when using Authority connector (JSON-RPC API)</p>
   * <p>This method will be deleted when all JSON-RPC methods are available through the REST API
   * 
   * @return a {@code Boolean} indicating whether the Confluence instance is alive or not
   * 
   * @throws Exception
   */
  public boolean checkAuth() throws Exception {
    try {
      if (httpClient == null) {
        connect();
      }
      getSpaces();
      return true;
    } catch (Exception e) {
      logger.warn(
          "[Checking connection] Confluence server appears to be down",
          e);
      throw e;
    }
  }
  /**
   * <p>
   * Create a get request for the given url
   * </p>
   * 
   * @param url
   *            the url
   * @return the created {@code HttpGet} instance
   */
  private HttpGet createGetRequest(String url) {
    String finalUrl = useBasicAuthentication() ? url + "&os_authType=basic": url;
    String sanitizedUrl = sanitizeUrl(finalUrl);
    HttpGet httpGet = new HttpGet(sanitizedUrl);
    httpGet.addHeader("Accept", "application/json");
    if (useBasicAuthentication()) {
      httpGet.addHeader(
          "Authorization",
          "Basic "
              + Base64.encodeBase64String(String.format(Locale.ROOT, "%s:%s",
                  this.username, this.password).getBytes(
                  Charset.forName("UTF-8"))));
    }
    return httpGet;
  }

  /**
   * <p>
   * Get a list of Confluence pages
   * </p>
   * 
   * @return a {@code ConfluenceResponse} containing the result pages and
   *         some pagination values
   * @throws Exception
   */
  public ConfluenceResponse<Page> getPages() throws Exception {
    return getPages(0, 50, Optional.<String> absent());
  }

  /**
   * <p>
   * Get a list of Confluence pages using pagination
   * </p>
   * 
   * @param start The start value to get pages from
   * @param limit The number of pages to get from start
   * @return a {@code ConfluenceResponse} containing the result pages and
   *         some pagination values
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  public ConfluenceResponse<Page> getPages(int start, int limit,
      Optional<String> space) throws Exception {
    String url = String.format(Locale.ROOT, "%s://%s:%s%s%s?limit=%s&start=%s", protocol,
        host, port, path, CONTENT_PATH, limit, start);
    if (space.isPresent()) {
      url = String.format(Locale.ROOT, "%s&spaceKey=%s", url, space.get());
    }
    return (ConfluenceResponse<Page>) getConfluenceResources(url, Page.builder());
  }

  /**
   * <p>Get the {@code ConfluenceResources} from the given url</p>
   * @param url The url identifying the REST resource to get the documents
   * @param builder The builder used to build the resources contained in the response
   * @return a {@code ConfluenceResponse} containing the page results
   * @throws Exception
   */
  private ConfluenceResponse<? extends ConfluenceResource> getConfluenceResources(String url, ConfluenceResourceBuilder<? extends ConfluenceResource> builder) throws Exception {
    logger.debug("[Processing] Hitting url for get confluence resources: {}", sanitizeUrl(url));

    try {
      HttpGet httpGet = createGetRequest(url);
      HttpResponse response = executeRequest(httpGet);
      ConfluenceResponse<? extends ConfluenceResource> confluenceResponse = responseFromHttpEntity(response
          .getEntity(), builder);
      EntityUtils.consume(response.getEntity());
      return confluenceResponse;
    } catch (IOException e) {
      logger.error("[Processing] Failed to get page(s)", e);
      throw new Exception("Confluence appears to be down", e);
    }
  }

  /**
   * <p>Creates a ConfluenceResponse from the entity returned in the HttpResponse</p>
   * @param entity the {@code HttpEntity} to extract the response from
   * @return a {@code ConfluenceResponse} with the requested information
   * @throws Exception
   */
  private <T extends ConfluenceResource> ConfluenceResponse<T> responseFromHttpEntity(HttpEntity entity, ConfluenceResourceBuilder<T> builder)
      throws Exception {
    String stringEntity = EntityUtils.toString(entity, "UTF-8");

    final JSONParser parser = new JSONParser();
    final JSONObject responseObject = (JSONObject)parser.parse(new StringReader(stringEntity));
    ConfluenceResponse<T> response = ConfluenceResponse
        .fromJson(responseObject, builder);
    if (response.getResults().size() == 0) {
      logger.debug("[Processing] No {} found in the Confluence response", builder.getType().getSimpleName());
    }

    return response;
  }
  
  /**
   * <p>Get the attachments of the given page</p>
   * @param pageId the page id
   * @return a {@code ConfluenceResponse} instance containing the attachment results and some pagination values</p>
   * @throws Exception
   */
  public ConfluenceResponse<Attachment> getPageAttachments(String pageId)
      throws Exception {
    return getPageAttachments(pageId, 0, 50);
  }

  /**
   * <p>Get the attachments of the given page using pagination</p>
   * @param pageId the page id
   * @param start The start value to get attachments from
   * @param limit The number of attachments to get from start
   * @return a {@code ConfluenceResponse} instance containing the attachment results and some pagination values</p>
   * @throws Exception
   */
  public ConfluenceResponse<Attachment> getPageAttachments(String pageId, int start,
      int limit) throws Exception {
    String url = String.format(Locale.ROOT, "%s://%s:%s%s%s/%s%s?limit=%s&start=%s",
        protocol, host, port, path, CONTENT_PATH, pageId, CHILD_ATTACHMENTS_PATH,
        limit, start);
    @SuppressWarnings("unchecked")
    ConfluenceResponse<Attachment> confluenceResources = (ConfluenceResponse<Attachment>) getConfluenceResources(url, Attachment.builder());
    return confluenceResources;
  }
  
  /**
   * <p>
   * Gets a specific attachment contained in the specific page
   * </p>
   * 
   * @param attachmentId
   * @param pageId
   * @return the {@code Attachment} instance
   */
  public Attachment getAttachment(String attachmentId) {
    String url = String
        .format(Locale.ROOT, "%s://%s:%s%s%s/%s?%s",
            protocol, host, port, path, CONTENT_PATH, attachmentId, EXPANDABLE_PARAMETERS);
    logger.debug(
        "[Processing] Hitting url for getting document content : {}",
        sanitizeUrl(url));
    try {
      HttpGet httpGet = createGetRequest(url);
      HttpResponse response = executeRequest(httpGet);
      HttpEntity entity = response.getEntity();
      MutableAttachment attachment = attachmentFromHttpEntity(entity);
      EntityUtils.consume(entity);
      retrieveAndSetAttachmentContent(attachment);
      return attachment;
    } catch (Exception e) {
      logger.error("[Processing] Failed to get attachment {}. Error: {}",
          url, e.getMessage());
    }

    return new Attachment();
  }

  /**
   * <p>
   * Downloads and retrieves the attachment content, setting it in the given
   * {@code Attachment} instance
   * </p>
   * 
   * @param attachment
   *            the {@code Attachment} instance to download and set the
   *            content
   * @throws Exception
   */
  private void retrieveAndSetAttachmentContent(MutableAttachment attachment)
      throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append(attachment.getBaseUrl()).append(attachment.getUrlContext())
        .append(attachment.getDownloadUrl());
    String url = sanitizeUrl(sb.toString());
    logger.debug(
        "[Processing] Hitting url for getting attachment content : {}",
        url);
    try {
      HttpGet httpGet = createGetRequest(url);
      HttpResponse response = executeRequest(httpGet);
      attachment.setLength(response.getEntity().getContentLength());
      byte[] byteContent = IOUtils.toByteArray(response.getEntity()
          .getContent());
      EntityUtils.consumeQuietly(response.getEntity());
      attachment.setContentStream(new ByteArrayInputStream(byteContent));
    } catch (Exception e) {

      logger.error(
          "[Processing] Failed to get attachment content from {}. Error: {}",
          url, e.getMessage());
      throw e;
    }

  }


  /**
   * <p>Get a Confluence page identified by its id</p>
   * @param pageId the page id
   * @return the Confluence page
   */
  public Page getPage(String pageId) {
    String url = String
        .format(Locale.ROOT, "%s://%s:%s%s%s/%s?%s",
            protocol, host, port, path, CONTENT_PATH, pageId, EXPANDABLE_PARAMETERS);
    url = sanitizeUrl(url);
    logger.debug(
        "[Processing] Hitting url for getting document content : {}",
        url);
    try {
      HttpGet httpGet = createGetRequest(url);
      HttpResponse response = executeRequest(httpGet);
      HttpEntity entity = response.getEntity();
      MutablePage page = pageFromHttpEntity(entity);
      EntityUtils.consume(entity);
      List<Label> labels = getLabels(pageId);
      page.setLabels(labels);
      return page;
    } catch (Exception e) {
      logger.error("[Processing] Failed to get page {0}. Error: {1}",
          url, e.getMessage());
    }

    return new Page();
  }

  /**
   * <p>Get the labels of a specific page</p> 
   * @param pageId The pageId to get the labels
   * @return a {@code List<Label>} of labels
   */
  public List<Label> getLabels(String pageId) {
        
    List<Label> labels = Lists.newArrayList();
    int lastStart = 0;
    int limit = 50;
    boolean isLast = false;
    do {
      String url = String
          .format(Locale.ROOT, "%s://%s:%s%s%s/%s/%s?start=%s&limit=%s",
              protocol, host, port, path, CONTENT_PATH, pageId, LABEL_PATH, lastStart, limit);
      url = sanitizeUrl(url);
      logger.debug(
          "[Processing] Hitting url for getting page labels : {}",
          url);
      try {
        @SuppressWarnings("unchecked")
        ConfluenceResponse<Label> response = (ConfluenceResponse<Label>) getConfluenceResources(url, Label.builder());
        labels.addAll(response.getResults());
        lastStart += response.getResults().size();
        isLast = response.isLast();
      } catch (Exception e) {
        logger.debug("Error getting labels for page {}. Reason: {}", pageId, e.getMessage());
      }
    }
    while(!isLast);
    
    return labels;
  }
  
  /**
   * 
   * @param username
   * @return
   * @throws Exception
   */
  public ConfluenceUser getUserAuthorities(String username) throws Exception {
    List<String> authorities = Lists.<String>newArrayList();
    Spaces spaces = getSpaces();
    for(Space space: spaces) {
      List<String> permissions = getSpacePermissionsForUser(space, username);
      if(permissions.contains(VIEW_PERMISSION)) {
        authorities.add(space.getKey());
      }
    }
    
    return new ConfluenceUser(username, authorities);
  
  }
  
  private HttpPost createPostRequest(String url) {
    HttpPost httpPost = new HttpPost(url);
    httpPost.addHeader("Accept", "application/json");
    httpPost.addHeader("Content-Type", "application/json");
    if (useBasicAuthentication()) {
      httpPost.addHeader(
          "Authorization",
          "Basic "
              + Base64.encodeBase64String(String.format(Locale.ROOT, "%s:%s",
                  this.username, this.password).getBytes(
                  Charset.forName("UTF-8"))));
    }
    return httpPost;
  }
  
  /**
   * <p>Execute the given {@code HttpUriRequest} using the configured client</p> 
   * @param request the {@code HttpUriRequest} to be executed
   * @return the {@code HttpResponse} object returned from the server
   * @throws Exception
   */
  private HttpResponse executeRequest(HttpUriRequest request)
      throws Exception {
    String url = request.getURI().toString();
    logger.debug(
        "[Processing] Hitting url for getting document content : {}",
        url);

    try {
      HttpResponse response = httpClient.execute(request, httpContext);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new Exception("Confluence error. "
            + response.getStatusLine().getStatusCode() + " "
            + response.getStatusLine().getReasonPhrase());
      }
      return response;
    } catch (Exception e) {
      logger.error("[Processing] Failed to get page {}. Error: {}",
          url, e.getMessage());
      throw e;
    }
  }

  /**
   * <p>Creates a Confluence page object from the given entity returned by the server</p>
   * @param entity the {@code HttpEntity} to create the {@code MutablePage} from
   * @return the Confluence page instance
   * @throws Exception
   */
  private MutablePage pageFromHttpEntity(HttpEntity entity) throws Exception {
    String stringEntity = EntityUtils.toString(entity, "UTF-8");

    final JSONParser parser = new JSONParser();
    final JSONObject responseObject = (JSONObject)parser.parse(new StringReader(stringEntity));
    @SuppressWarnings("unchecked")
    MutablePage response = ((ConfluenceResourceBuilder<MutablePage>)MutablePage.builder()).fromJson(responseObject, new MutablePage());
    return response;
  }

  /**
   * <p>Creates a {@code MutableAttachment} object from the given entity returned by the server</p>
   * @param entity the {@code HttpEntity} to create the {@code MutableAttachment} from
   * @return the Confluence MutableAttachment instance
   * @throws Exception
   */
  private MutableAttachment attachmentFromHttpEntity(HttpEntity entity)
      throws Exception {
    String stringEntity = EntityUtils.toString(entity, "UTF-8");
    final JSONParser parser = new JSONParser();
    final JSONObject responseObject = (JSONObject)parser.parse(new StringReader(stringEntity));;
    MutableAttachment response = (MutableAttachment) Attachment
        .builder()
        .fromJson(responseObject, new MutableAttachment());
    return response;
  }

  /**
   * <p>Method to check if basic authentication must be used</p>
   * @return {@code Boolean} indicating whether basic authentication must be used or not
   */
  private boolean useBasicAuthentication() {
    return this.username != null && !"".equals(username)
        && this.password != null;
  }

  /**
   * <p>
   * Sanitize the given url replacing the appearance of more than one slash by
   * only one slash
   * </p>
   * 
   * @param url
   *            The url to sanitize
   * @return the sanitized url
   */
  private String sanitizeUrl(String url) {
    int colonIndex = url.indexOf(":");
    String urlWithoutProtocol = url.startsWith("http") ? url.substring(colonIndex+3) : url;
    String sanitizedUrl = urlWithoutProtocol.replaceAll("\\/+", "/");
    return url.substring(0,colonIndex) + "://" + sanitizedUrl;
  }
  
  private Spaces getSpaces() throws Exception {
    String url = String.format(Locale.ROOT, "%s://%s:%s%s%sgetSpaces", protocol, host,
        port, path, AUTHORITY_PATH);

    logger.debug(
        "[Processing] Hitting url for getting Confluence spaces : {}",
        url);

    HttpPost httpPost = createPostRequest(url);
    httpPost.setEntity(new StringEntity("[]"));
    HttpResponse response = httpClient.execute(httpPost);
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new ConfluenceException("Confluence error. "
          + response.getStatusLine().getStatusCode() + " "
          + response.getStatusLine().getReasonPhrase());
    }
    HttpEntity entity = response.getEntity();
    Spaces spaces = spacesFromHttpEntity(entity);
    EntityUtils.consume(entity);
    return spaces;
  }
  
  private List<String> getSpacePermissionsForUser(Space space, String username) throws Exception {
    String url = String.format(Locale.ROOT, "%s://%s:%s%s%sgetPermissionsForUser", protocol, host,
        port, path, AUTHORITY_PATH);

    logger.debug(
        "[Processing] Hitting url {} for getting Confluence permissions for user {} in space {}",
        url, username, space.getKey());

    HttpPost httpPost = createPostRequest(url);
    final JSONArray jsonArray = new JSONArray();
    jsonArray.add(space.getKey());
    jsonArray.add(username);
    StringEntity stringEntity = new StringEntity(jsonArray.toJSONString());
    httpPost.setEntity(stringEntity);
    HttpResponse response = httpClient.execute(httpPost);
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new ConfluenceException("Confluence error. "
          + response.getStatusLine().getStatusCode() + " "
          + response.getStatusLine().getReasonPhrase());
    }
    HttpEntity entity = response.getEntity();
    List<String> permissions = permissionsFromHttpEntity(entity);
    EntityUtils.consume(entity);
    return permissions;
  }

  private Spaces spacesFromHttpEntity(HttpEntity entity) throws Exception {
    String stringEntity = EntityUtils.toString(entity, "UTF-8");
    final JSONParser parser = new JSONParser();
    final JSONArray responseObject = (JSONArray)parser.parse(new StringReader(stringEntity));
    Spaces response = Spaces.fromJson(responseObject);

    return response;
  }
  
  private List<String> permissionsFromHttpEntity(HttpEntity entity) throws Exception {
    String stringEntity = EntityUtils.toString(entity, "UTF-8");
    final JSONParser parser = new JSONParser();
    final JSONArray responseObject = (JSONArray)parser.parse(new StringReader(stringEntity));
    final List<String> permissions = Lists.newArrayList();
    for(int i=0,len=responseObject.size();i<len;i++) {
      permissions.add(responseObject.get(i).toString());
    }

    return permissions;
  }
}
