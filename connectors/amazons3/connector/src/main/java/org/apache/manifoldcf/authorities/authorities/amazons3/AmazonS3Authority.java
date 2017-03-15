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
package org.apache.manifoldcf.authorities.authorities.amazons3;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.core.interfaces.ConfigNode;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPasswordMapperActivity;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CanonicalGrantee;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.Grantee;
import com.amazonaws.services.s3.model.Owner;

/**
 * Authority connector for Amazons3
 * @author Kuhajeyan
 *
 */
public class AmazonS3Authority extends org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector {
  private static final String TAB_NAME = "TabName";

  protected long lastSessionFetch = -1L;

  protected static final long timeToRelease = 300000L;

  protected AmazonS3 amazonS3;

  protected boolean connected = false;

  protected String amazons3ProxyHost = null;

  protected String amazons3ProxyPort = null;

  protected String amazons3ProxyDomain = null;

  protected String amazons3ProxyUserName = null;

  protected String amazons3ProxyPassword = null;

  protected String amazons3AwsAccessKey = null;

  protected String amazons3AwsSecretKey = null;

  public AmazonS3Authority() {

  }

  @Override
  public void disconnect() throws ManifoldCFException {
    amazons3AwsAccessKey = null;
    amazons3AwsSecretKey = null;

    amazons3ProxyHost = null;
    amazons3ProxyPort = null;
    amazons3ProxyDomain = null;
    amazons3ProxyUserName = null;
    amazons3ProxyPassword = null;
  }

  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    // aws access and secret keys
    amazons3AwsAccessKey = configParams
        .getParameter(AmazonS3Config.AWS_ACCESS_KEY);
    amazons3AwsSecretKey = configParams
        .getObfuscatedParameter(AmazonS3Config.AWS_SECRET_KEY);

    // proxy values
    amazons3ProxyHost = configParams
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_HOST);
    amazons3ProxyPort = configParams
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_PORT);
    amazons3ProxyDomain = configParams
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_DOMAIN);
    amazons3ProxyUserName = configParams
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_USERNAME);
    amazons3ProxyPassword = configParams
        .getObfuscatedParameter(AmazonS3Config.AMAZONS3_PROXY_PASSWORD);
  }

  /**
   * Test the connection. Returns a string describing the connection
   * integrity.
   *
   * @return the connection's status as a displayable string.
   */
  @Override
  public String check() throws ManifoldCFException {
    // connect with amazons3 client
    Logging.authorityConnectors.info("Checking connection");

    try {
      // invokes the check thread
      CheckThread checkThread = new CheckThread(getClient());
      checkThread.start();
      checkThread.join();
      if (checkThread.getException() != null) {
        Throwable thr = checkThread.getException();
        return "Check exception: " + thr.getMessage();
      }
      return checkThread.getResult();
    }
    catch (InterruptedException ex) {
      Logging.authorityConnectors.error(
          "Error while checking connection", ex);
      throw new ManifoldCFException(ex.getMessage(), ex,
          ManifoldCFException.INTERRUPTED);
    }

  }

  /**
   * Get the Amazons3 client, relevant access keys should have been posted
   * already
   * @return
   */
  protected AmazonS3 getClient() {
    if (amazonS3 == null) {
      try {
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(
            amazons3AwsAccessKey, amazons3AwsSecretKey);
        amazonS3 = new AmazonS3Client(awsCreds);
      }
      catch (Exception e) {
        Logging.authorityConnectors.error(
            "Error while amazon s3 connectionr", e);

      }
    }
    lastSessionFetch = System.currentTimeMillis();
    return amazonS3;
  }

  @Override
  public boolean isConnected() {
    return amazonS3 != null && amazonS3.getS3AccountOwner() != null;
  }

  @Override
  public void poll() throws ManifoldCFException {
    if (lastSessionFetch == -1L) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease) {
      amazonS3 = null;
      lastSessionFetch = -1L;
    }
  }

  private void fillInServerConfigurationMap(Map<String, Object> out,
      IPasswordMapperActivity mapper, ConfigParams parameters) {

    String amazons3AccessKey = parameters
        .getParameter(AmazonS3Config.AWS_ACCESS_KEY);
    String amazons3SecretKey = parameters
        .getObfuscatedParameter(AmazonS3Config.AWS_SECRET_KEY);

    // default values
    if (amazons3AccessKey == null)
      amazons3AccessKey = AmazonS3Config.AMAZONS3_AWS_ACCESS_KEY_DEFAULT;
    if (amazons3SecretKey == null)
      amazons3SecretKey = AmazonS3Config.AMAZONS3_AWS_SECRET_KEY_DEFAULT;
    else
      amazons3SecretKey = mapper.mapPasswordToKey(amazons3SecretKey);

    // fill the map
    out.put("AMAZONS3_AWS_ACCESS_KEY", amazons3AccessKey);
    out.put("AMAZONS3_AWS_SECRET_KEY", amazons3SecretKey);
  }

  private void fillInProxyConfigurationMap(Map<String, Object> out,
      IPasswordMapperActivity mapper, ConfigParams parameters) {
    String amazons3ProxyHost = parameters
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_HOST);
    String amazons3ProxyPort = parameters
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_PORT);
    String amazons3ProxyDomain = parameters
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_DOMAIN);
    String amazons3ProxyUserName = parameters
        .getParameter(AmazonS3Config.AMAZONS3_PROXY_USERNAME);
    String amazons3ProxyPassword = parameters
        .getObfuscatedParameter(AmazonS3Config.AMAZONS3_PROXY_PASSWORD);

    if (amazons3ProxyHost == null)
      amazons3ProxyHost = AmazonS3Config.AMAZONS3_PROXY_HOST_DEFAULT;
    if (amazons3ProxyPort == null)
      amazons3ProxyPort = AmazonS3Config.AMAZONS3_PROXY_PORT_DEFAULT;
    if (amazons3ProxyDomain == null)
      amazons3ProxyDomain = AmazonS3Config.AMAZONS3_PROXY_DOMAIN_DEFAULT;
    if (amazons3ProxyUserName == null)
      amazons3ProxyUserName = AmazonS3Config.AMAZONS3_PROXY_USERNAME_DEFAULT;
    if (amazons3ProxyPassword == null)
      amazons3ProxyPassword = AmazonS3Config.AMAZONS3_PROXY_PASSWORD_DEFAULT;
    else
      amazons3ProxyPassword = mapper
          .mapPasswordToKey(amazons3ProxyPassword);

    // fill the map
    out.put("AMAZONS3_PROXY_HOST", amazons3ProxyHost);
    out.put("AMAZONS3_PROXY_PORT", amazons3ProxyPort);
    out.put("AMAZONS3_PROXY_DOMAIN", amazons3ProxyDomain);
    out.put("AMAZONS3_PROXY_USERNAME", amazons3ProxyUserName);
    out.put("AMAZONS3_PROXY_PWD", amazons3ProxyPassword);
  }

  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML
   * that is output from this configuration will be within appropriate <html>
   * and <body> tags.
   * 
   * */
  public void viewConfiguration(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in map from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);
    fillInProxyConfigurationMap(paramMap, out, parameters);

    Messages.outputResourceWithVelocity(out, locale, AmazonS3Config.VIEW_CONFIG_FORWARD, paramMap);
  }

  /**
   * Output the configuration header section. This method is called in the
   * head section of the connector's configuration page. Its purpose is to add
   * the required tabs to the list, and to output any javascript methods that
   * might be needed by the configuration editing HTML.
   * */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      List<String> tabsArray) throws ManifoldCFException, IOException {
    // Add the Server tab
    tabsArray.add(Messages.getString(locale,
        AmazonS3Config.AMAZONS3_SERVER_TAB_PROPERTY));
    // Add the Proxy tab
    tabsArray.add(Messages.getString(locale,
        AmazonS3Config.AMAZONS3_PROXY_TAB_PROPERTY));
    // Map the parameters
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Fill in the parameters from each tab
    fillInServerConfigurationMap(paramMap, out, parameters);
    fillInProxyConfigurationMap(paramMap, out, parameters);

    // Output the Javascript - only one Velocity template for all tabs
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.EDIT_CONFIG_HEADER_FORWARD, paramMap);
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext,
      IHTTPOutput out, Locale locale, ConfigParams parameters,
      String tabName) throws ManifoldCFException, IOException {

    // Call the Velocity templates for each tab
    Map<String, Object> paramMap = new HashMap<String, Object>();
    // Set the tab name
    paramMap.put(TAB_NAME, tabName);

    // Fill in the parameters
    fillInServerConfigurationMap(paramMap, out, parameters);
    fillInProxyConfigurationMap(paramMap, out, parameters);

    // Server tab
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.EDIT_CONFIG_FORWARD_SERVER, paramMap);
    // Proxy tab
    Messages.outputResourceWithVelocity(out, locale,
        AmazonS3Config.EDIT_CONFIG_FORWARD_PROXY, paramMap);

  }

  /**
   * Process a configuration post. This method is called at the start of the
   * connector's configuration page, whenever there is a possibility that form
   * data for a connection has been posted. Its purpose is to gather form
   * information and modify the configuration parameters accordingly. The name
   * of the posted form is "editconnection".
   * */
  @Override
  public String processConfigurationPost(IThreadContext threadContext,
      IPostParameters variableContext, Locale locale,
      ConfigParams parameters) throws ManifoldCFException {
    // server tab
    String awsAccessKey = variableContext.getParameter("aws_access_key");

    if (awsAccessKey != null) {
      parameters
          .setParameter(AmazonS3Config.AWS_ACCESS_KEY, awsAccessKey);
    }
    String awsSecretKey = variableContext.getParameter("aws_secret_key");
    if (awsSecretKey != null) {
      // set as obfuscated parameter
      parameters.setObfuscatedParameter(AmazonS3Config.AWS_SECRET_KEY,
          variableContext.mapKeyToPassword(awsSecretKey));
    }
    Logging.authorityConnectors.info("Saved values for aws keys");

    int i = 0;
    while (i < parameters.getChildCount()) {
      ConfigNode cn = parameters.getChild(i);
      if (cn.getType().equals(AmazonS3Config.AWS_ACCESS_KEY)
          || cn.getType().equals(AmazonS3Config.AWS_SECRET_KEY))
        parameters.removeChild(i);
      else
        i++;
    }

    // proxy tab
    String amazons3ProxyHost = variableContext
        .getParameter("amazons3_proxy_host");
    if (amazons3ProxyHost != null) {
      parameters.setParameter(AmazonS3Config.AMAZONS3_PROXY_HOST,
          amazons3ProxyHost);
    }
    String amazons3ProxyPort = variableContext
        .getParameter("amazons3_proxy_port");
    if (amazons3ProxyPort != null) {
      parameters.setParameter(AmazonS3Config.AMAZONS3_PROXY_PORT,
          amazons3ProxyPort);
    }
    String amazons3ProxyDomain = variableContext
        .getParameter("amazons3_proxy_domain");
    if (amazons3ProxyDomain != null) {
      parameters.setParameter(AmazonS3Config.AMAZONS3_PROXY_DOMAIN,
          amazons3ProxyDomain);
    }
    String amazons3ProxyUserName = variableContext
        .getParameter("amazons3_proxy_username");
    if (amazons3ProxyUserName != null) {
      parameters.setParameter(AmazonS3Config.AMAZONS3_PROXY_USERNAME,
          amazons3ProxyUserName);
    }
    String amazons3ProxyPassword = variableContext
        .getParameter("amazons3_proxy_pwd");
    if (amazons3ProxyPassword != null) {
      // set as obfuscated parameter
      parameters.setObfuscatedParameter(
          AmazonS3Config.AMAZONS3_PROXY_PASSWORD,
          variableContext.mapKeyToPassword(amazons3ProxyPassword));
    }

    return null;
  }

  @Override
  public AuthorizationResponse getAuthorizationResponse(String userName)
      throws ManifoldCFException {

    try {
      HashMap<String, Set<Grant>> checkUserExists = checkUserExists(userName);
      if (isUserAvailable(userName, checkUserExists.values())) {
        return new AuthorizationResponse(new String[] { userName },
          AuthorizationResponse.RESPONSE_OK);
      }
		
    }
    catch (Exception e) {
      Logging.authorityConnectors.error("Error while getting authorization response",e);
      return RESPONSE_UNREACHABLE;
    }
    return RESPONSE_USERNOTFOUND;
  }

  private boolean isUserAvailable(String userName,
      Collection<Set<Grant>> collection) {
    String[] users = getUsers(collection);
    return Arrays.asList(users).contains(userName);
  }

  private String[] getUsers(Collection<Set<Grant>> collection) {
    Set<String> users = new HashSet<String>();// no duplicates
    for (Collection c : collection) {
      Set<Grant> c1 = (Set<Grant>) c;
      for (Grant grant : c1) {
        if (grant != null && grant.getGrantee() != null) {
          Grantee grantee = grant.getGrantee();

          if (grantee instanceof CanonicalGrantee) {
            users.add(((CanonicalGrantee) grantee).getDisplayName());
          }
          else {
            users.add(grantee.getIdentifier());
          }
        }
      }
    }

    return users.toArray(new String[users.size()]);
  }

  private HashMap<String, Set<Grant>> checkUserExists(String userName)
      throws ManifoldCFException {
    GrantsThread t = new GrantsThread(getClient());
    try {
      t.start();
      t.finishUp();
      return t.getResult();
    }
    catch (InterruptedException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
          ManifoldCFException.INTERRUPTED);
    }
    catch (java.net.SocketTimeoutException e) {
      handleIOException(e);
    }
    catch (InterruptedIOException e) {
      t.interrupt();
      handleIOException(e);
    }
    catch (IOException e) {
      handleIOException(e);
    }
    catch (ResponseException e) {
      handleResponseException(e);
    }
    return null;
  }

  /**
   * Obtain the default access tokens for a given user name.
   * @param userName is the user name or identifier.
   * @return the default response tokens, presuming that the connect method
   * fails.
   */
  @Override
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName) {
    return RESPONSE_UNREACHABLE;
  }

  private static void handleIOException(IOException e)
      throws ManifoldCFException {
    if (!(e instanceof java.net.SocketTimeoutException)
        && (e instanceof InterruptedIOException)) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
          ManifoldCFException.INTERRUPTED);
    }
    Logging.authorityConnectors.warn(
        "JIRA: IO exception: " + e.getMessage(), e);
    throw new ManifoldCFException("IO exception: " + e.getMessage(), e);
  }

  private static void handleResponseException(ResponseException e)
      throws ManifoldCFException {
    throw new ManifoldCFException("Response exception: " + e.getMessage(),
        e);
  }

  protected static class GrantsThread extends Thread {

    protected Throwable exception = null;

    protected boolean result = false;

    protected AmazonS3 amazonS3 = null;

    private HashMap<String, Set<Grant>> grants;

    public GrantsThread(AmazonS3 amazonS3) {
      super();
      this.amazonS3 = amazonS3;
      setDaemon(true);
      grants = new HashMap<String, Set<Grant>>();
    }

    public void finishUp() throws InterruptedException, IOException,
        ResponseException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof IOException) {
          throw (IOException) thr;
        }
        else if (thr instanceof ResponseException) {
          throw (ResponseException) thr;
        }
        else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        }
        else {
          throw (Error) thr;
        }
      }
    }

    @Override
    public void run() {

      List<Bucket> listBuckets = amazonS3.listBuckets();
      for (Bucket bucket : listBuckets) {
        AccessControlList bucketAcl = amazonS3.getBucketAcl(bucket
            .getName());

        if (bucketAcl != null)
          grants.put(bucket.getName(), bucketAcl.getGrants());
      }
    }

    public HashMap<String, Set<Grant>> getResult() {
      return grants;
    }

  }

  protected static class CheckThread extends Thread {
    protected String result = "Unknown";

    protected AmazonS3 s3 = null;

    protected Throwable exception = null;

    public CheckThread(AmazonS3 s3) {
      this.s3 = s3;
    }

    public String getResult() {
      return result;
    }

    public Throwable getException() {
      return exception;
    }

    @Override
    public void run() {
      try {
        if (s3 != null) {
          Owner s3AccountOwner = s3.getS3AccountOwner();
          if (s3AccountOwner != null) {
            result = StringUtils.isNotEmpty(s3AccountOwner
                .getDisplayName()) ? "Connection OK"
                : "Connection Failed";
          }

        }
      }
      catch (AmazonServiceException e) {
        result = "Connection Failed : " + e.getMessage();
        exception = e;

        Logging.authorityConnectors.error(e);
      }
      catch (AmazonClientException e) {
        result = "Connection Failed : " + e.getMessage();
        exception = e;

        Logging.authorityConnectors.error(e);
      }
    }
  }

}
