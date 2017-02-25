/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.authorities.authorities.generic;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.manifoldcf.core.util.URLEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.authorities.interfaces.AuthorizationResponse;
import org.apache.manifoldcf.authorities.system.ManifoldCF;
import org.apache.manifoldcf.core.interfaces.CacheManagerFactory;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ICacheCreateHandle;
import org.apache.manifoldcf.core.interfaces.ICacheDescription;
import org.apache.manifoldcf.core.interfaces.ICacheHandle;
import org.apache.manifoldcf.core.interfaces.ICacheManager;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.StringSet;
import org.apache.manifoldcf.crawler.connectors.generic.api.Auth;
import org.apache.manifoldcf.ui.util.Encoder;

/**
 *
 * @author krycek
 */
public class GenericAuthority extends org.apache.manifoldcf.authorities.authorities.BaseAuthorityConnector {

  public static final String _rcsid = "@(#)$Id: GenericAuthority.java 1496653 2013-06-25 22:05:04Z mlizewski $";

  /**
   * This is the active directory global deny token. This should be ingested
   * with all documents.
   */
  private static final String globalDenyToken = "DEAD_AUTHORITY";

  private static final AuthorizationResponse unreachableResponse = new AuthorizationResponse(new String[]{globalDenyToken},
    AuthorizationResponse.RESPONSE_UNREACHABLE);

  private static final AuthorizationResponse userNotFoundResponse = new AuthorizationResponse(new String[]{globalDenyToken},
    AuthorizationResponse.RESPONSE_USERNOTFOUND);

  private final static String ACTION_PARAM_NAME = "action";

  private final static String ACTION_AUTH = "auth";

  private final static String ACTION_CHECK = "check";

  private String genericLogin = null;

  private String genericPassword = null;

  private String genericEntryPoint = null;

  private int connectionTimeoutMillis = 60 * 1000;

  private int socketTimeoutMillis = 30 * 60 * 1000;

  private long responseLifetime = 60000L; //60sec

  private int LRUsize = 1000;

  private DefaultHttpClient client = null;

  private long sessionExpirationTime = -1L;

  /**
   * Cache manager.
   */
  private ICacheManager cacheManager = null;

  /**
   * Constructor.
   */
  public GenericAuthority() {
  }

  /**
   * Set thread context.
   */
  @Override
  public void setThreadContext(IThreadContext tc)
    throws ManifoldCFException {
    super.setThreadContext(tc);
    cacheManager = CacheManagerFactory.make(tc);
  }

  /**
   * Connect. The configuration parameters are included.
   *
   * @param configParams are the configuration parameters for this connection.
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    genericEntryPoint = getParam(configParams, "genericEntryPoint", null);
    genericLogin = getParam(configParams, "genericLogin", null);
    genericPassword = "";
    try {
      genericPassword = ManifoldCF.deobfuscate(getParam(configParams, "genericPassword", ""));
    } catch (ManifoldCFException ignore) {
    }
    connectionTimeoutMillis = Integer.parseInt(getParam(configParams, "genericConnectionTimeout", "60000"));
    if (connectionTimeoutMillis == 0) {
      connectionTimeoutMillis = 60000;
    }
    socketTimeoutMillis = Integer.parseInt(getParam(configParams, "genericSocketTimeout", "1800000"));
    if (socketTimeoutMillis == 0) {
      socketTimeoutMillis = 1800000;
    }
    responseLifetime = Long.parseLong(getParam(configParams, "genericResponseLifetime", "60000"));
    if (responseLifetime == 0) {
      responseLifetime = 60000;
    }
  }

  protected DefaultHttpClient getClient() throws ManifoldCFException {
    synchronized (this) {
      if (client != null) {
        return client;
      }
      DefaultHttpClient cl = new DefaultHttpClient();
      if (genericLogin != null && !genericLogin.isEmpty()) {
        try {
          URL url = new URL(genericEntryPoint);
          Credentials credentials = new UsernamePasswordCredentials(genericLogin, genericPassword);
          cl.getCredentialsProvider().setCredentials(new AuthScope(url.getHost(), url.getPort() > 0 ? url.getPort() : 80, AuthScope.ANY_REALM), credentials);
          cl.addRequestInterceptor(new PreemptiveAuth(credentials), 0);
        } catch (MalformedURLException ex) {
          client = null;
          sessionExpirationTime = -1L;
          throw new ManifoldCFException("getClient exception: " + ex.getMessage(), ex);
        }
      }
      HttpConnectionParams.setConnectionTimeout(cl.getParams(), connectionTimeoutMillis);
      HttpConnectionParams.setSoTimeout(cl.getParams(), socketTimeoutMillis);
      sessionExpirationTime = System.currentTimeMillis() + 300000L;
      client = cl;
      return cl;
    }
  }

  /**
   * Poll. The connection should be closed if it has been idle for too long.
   */
  @Override
  public void poll()
    throws ManifoldCFException {
    if (client != null && System.currentTimeMillis() > sessionExpirationTime) {
      disconnectSession();
    }
    super.poll();
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return client != null;
  }

  /**
   * Check connection for sanity.
   */
  @Override
  public String check()
    throws ManifoldCFException {
    HttpClient client = getClient();
    try {
      CheckThread checkThread = new CheckThread(client, genericEntryPoint + "?" + ACTION_PARAM_NAME + "=" + ACTION_CHECK);
      checkThread.start();
      checkThread.join();
      if (checkThread.getException() != null) {
        Throwable thr = checkThread.getException();
        return "Check exception: " + thr.getMessage();
      }
      return checkThread.getResult();
    } catch (InterruptedException ex) {
      throw new ManifoldCFException(ex.getMessage(), ex, ManifoldCFException.INTERRUPTED);
    }
  }

  /**
   * Close the connection. Call this before discarding the repository connector.
   */
  @Override
  public void disconnect()
    throws ManifoldCFException {
    disconnectSession();
    super.disconnect();

    // Zero out all the stuff that we want to be sure we don't use again
    genericEntryPoint = null;
    genericLogin = null;
    genericPassword = null;
  }

  protected String createCacheConnectionString() {
    StringBuilder sb = new StringBuilder();
    sb.append(genericEntryPoint).append("#").append(genericLogin);
    return sb.toString();
  }

  /**
   * Obtain the access tokens for a given user name.
   *
   * @param userName is the user name or identifier.
   * @return the response tokens (according to the current authority). (Should
   * throws an exception only when a condition cannot be properly described
   * within the authorization response object.)
   */
  @Override
  public AuthorizationResponse getAuthorizationResponse(String userName)
    throws ManifoldCFException {

    HttpClient client = getClient();
    // Construct a cache description object
    ICacheDescription objectDescription = new GenericAuthorizationResponseDescription(userName,
      createCacheConnectionString(), this.responseLifetime, this.LRUsize);

    // Enter the cache
    ICacheHandle ch = cacheManager.enterCache(new ICacheDescription[]{objectDescription}, null, null);
    try {
      ICacheCreateHandle createHandle = cacheManager.enterCreateSection(ch);
      try {
        // Lookup the object
        AuthorizationResponse response = (AuthorizationResponse) cacheManager.lookupObject(createHandle, objectDescription);
        if (response != null) {
          return response;
        }
        // Create the object.
        response = getAuthorizationResponseUncached(client, userName);
        // Save it in the cache
        cacheManager.saveObject(createHandle, objectDescription, response);
        // And return it...
        return response;
      } finally {
        cacheManager.leaveCreateSection(createHandle);
      }
    } finally {
      cacheManager.leaveCache(ch);
    }
  }

  protected AuthorizationResponse getAuthorizationResponseUncached(HttpClient client, String userName)
    throws ManifoldCFException {
    StringBuilder url = new StringBuilder(genericEntryPoint);
    url.append("?").append(ACTION_PARAM_NAME).append("=").append(ACTION_AUTH);
    url.append("&username=").append(URLEncoder.encode(userName));


    try {
      FetchTokensThread t = new FetchTokensThread(client, url.toString());
      t.start();
      t.join();
      if (t.getException() != null) {
        return unreachableResponse;
      }
      Auth auth = t.getAuthResponse();
      if (auth == null) {
        return userNotFoundResponse;
      }
      if (!auth.exists) {
        return userNotFoundResponse;
      }
      if (auth.tokens == null) {
        return new AuthorizationResponse(new String[]{}, AuthorizationResponse.RESPONSE_OK);
      }

      String[] tokens = new String[auth.tokens.size()];
      int k = 0;
      while (k < tokens.length) {
        tokens[k] = (String) auth.tokens.get(k);
        k++;
      }

      return new AuthorizationResponse(tokens, AuthorizationResponse.RESPONSE_OK);
    } catch (InterruptedException ex) {
      throw new ManifoldCFException(ex.getMessage(), ex, ManifoldCFException.INTERRUPTED);
    }
  }

  /**
   * Obtain the default access tokens for a given user name.
   *
   * @param userName is the user name or identifier.
   * @return the default response tokens, presuming that the connect method
   * fails.
   */
  @Override
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName) {
    // The default response if the getConnection method fails
    return unreachableResponse;
  }

  // UI support methods.
  //
  // These support methods are involved in setting up authority connection configuration information. The configuration methods cannot assume that the
  // current authority object is connected.  That is why they receive a thread context argument.
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale, "generic.EntryPoint"));

    out.print(
      "<script type=\"text/javascript\">\n"
      + "<!--\n"
      + "function checkConfig() {\n"
      + "  return true;\n"
      + "}\n"
      + "\n"
      + "function checkConfigForSave() {\n"
      + "  if (editconnection.genericEntryPoint.value == \"\") {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "generic.EntryPointCannotBeBlank") + "\");\n"
      + "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "generic.EntryPoint") + "\");\n"
      + "    editconnection.genericEntryPoint.focus();\n"
      + "    return false;\n"
      + "  }\n"
      + "  return true;\n"
      + "}\n"
      + "//-->\n"
      + "</script>\n");
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException {

    String server = getParam(parameters, "genericEntryPoint", "");
    String login = getParam(parameters, "genericLogin", "");
    String password = "";
    try {
      password = out.mapPasswordToKey(ManifoldCF.deobfuscate(getParam(parameters, "genericPassword", "")));
    } catch (ManifoldCFException ignore) {
    }
    String conTimeout = getParam(parameters, "genericConnectionTimeout", "60000");
    String soTimeout = getParam(parameters, "genericSocketTimeout", "1800000");
    String respLifetime = getParam(parameters, "genericResponseLifetime", "60000");

    if (tabName.equals(Messages.getString(locale, "generic.EntryPoint"))) {
      out.print(
        "<table class=\"displaytable\">\n"
        + " <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.EntryPointColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericEntryPoint\" value=\"" + Encoder.attributeEscape(server) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.LoginColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericLogin\" value=\"" + Encoder.attributeEscape(login) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.PasswordColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"password\" size=\"32\" name=\"genericPassword\" value=\"" + Encoder.attributeEscape(password) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.ConnectionTimeoutColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericConTimeout\" value=\"" + Encoder.attributeEscape(conTimeout) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.SocketTimeoutColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericSoTimeout\" value=\"" + Encoder.attributeEscape(soTimeout) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.ResponseLifetimeColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericResponseLifetime\" value=\"" + Encoder.attributeEscape(respLifetime) + "\"/></td>\n"
        + " </tr>\n"
        + "</table>\n");
    } else {
      out.print("<input type=\"hidden\" name=\"genericEntryPoint\" value=\"" + Encoder.attributeEscape(server) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericLogin\" value=\"" + Encoder.attributeEscape(login) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericPassword\" value=\"" + Encoder.attributeEscape(password) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericConTimeout\" value=\"" + Encoder.attributeEscape(conTimeout) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericSoTimeout\" value=\"" + Encoder.attributeEscape(soTimeout) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericResponseLifetime\" value=\"" + Encoder.attributeEscape(respLifetime) + "\"/>\n");
    }
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException {

    copyParam(variableContext, parameters, "genericLogin");
    copyParam(variableContext, parameters, "genericEntryPoint");
    copyParam(variableContext, parameters, "genericConTimeout");
    copyParam(variableContext, parameters, "genericSoTimeout");
    copyParam(variableContext, parameters, "genericResponseLifetime");

    String password = variableContext.getParameter("genericPassword");
    if (password == null) {
      password = "";
    }
    parameters.setParameter("genericPassword", org.apache.manifoldcf.core.system.ManifoldCF.obfuscate(variableContext.mapKeyToPassword(password)));
    return null;
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException {
    String login = getParam(parameters, "genericLogin", "");
    String server = getParam(parameters, "genericEntryPoint", "");
    String conTimeout = getParam(parameters, "genericConnectionTimeout", "60000");
    String soTimeout = getParam(parameters, "genericSocketTimeout", "1800000");
    String respLifetime = getParam(parameters, "genericResponseLifetime", "60000");

    out.print(
      "<table class=\"displaytable\">\n"
      + " <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.EntryPointColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(server) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.LoginColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(login) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.PasswordColon") + "</nobr></td>\n"
      + "  <td class=\"value\">**********</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.ConnectionTimeoutColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(conTimeout) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.SocketTimeoutColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(soTimeout) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.ResponseLifetimeColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(respLifetime) + "</td>\n"
      + " </tr>\n"
      + "</table>\n");
  }

  private String getParam(ConfigParams parameters, String name, String def) {
    return parameters.getParameter(name) != null ? parameters.getParameter(name) : def;
  }

  private boolean copyParam(IPostParameters variableContext, ConfigParams parameters, String name) {
    String val = variableContext.getParameter(name);
    if (val == null) {
      return false;
    }
    parameters.setParameter(name, val);
    return true;
  }

  // Protected methods
  protected static StringSet emptyStringSet = new StringSet();

  private void disconnectSession() {
    synchronized (this) {
      client.getConnectionManager().shutdown();
      client = null;
    }
  }

  /**
   * This is the cache object descriptor for cached access tokens from this
   * connector.
   */
  protected class GenericAuthorizationResponseDescription extends org.apache.manifoldcf.core.cachemanager.BaseDescription {

    /**
     * The user name
     */
    protected String userName;

    /**
     * LDAP connection string with server name and base DN
     */
    protected String connectionString;

    /**
     * The response lifetime
     */
    protected long responseLifetime;

    /**
     * The expiration time
     */
    protected long expirationTime = -1;

    /**
     * Constructor.
     */
    public GenericAuthorizationResponseDescription(String userName, String connectionString, long responseLifetime, int LRUsize) {
      super("LDAPAuthority", LRUsize);
      this.userName = userName;
      this.connectionString = connectionString;
      this.responseLifetime = responseLifetime;
    }

    /**
     * Return the invalidation keys for this object.
     */
    @Override
    public StringSet getObjectKeys() {
      return emptyStringSet;
    }

    /**
     * Get the critical section name, used for synchronizing the creation of the
     * object
     */
    @Override
    public String getCriticalSectionName() {
      StringBuilder sb = new StringBuilder(getClass().getName());
      sb.append("-").append(userName).append("-").append(connectionString);
      return sb.toString();
    }

    /**
     * Return the object expiration interval
     */
    @Override
    public long getObjectExpirationTime(long currentTime) {
      if (expirationTime == -1) {
        expirationTime = currentTime + responseLifetime;
      }
      return expirationTime;
    }

    @Override
    public int hashCode() {
      return userName.hashCode() + connectionString.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof GenericAuthorizationResponseDescription)) {
        return false;
      }
      GenericAuthorizationResponseDescription ard = (GenericAuthorizationResponseDescription) o;
      if (!ard.userName.equals(userName)) {
        return false;
      }
      if (!ard.connectionString.equals(connectionString)) {
        return false;
      }
      return true;
    }
  }

  static class PreemptiveAuth implements HttpRequestInterceptor {

    private Credentials credentials;

    public PreemptiveAuth(Credentials creds) {
      this.credentials = creds;
    }

    @Override
    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
      request.addHeader(new BasicScheme(StandardCharsets.US_ASCII).authenticate(credentials, request, context));
    }
  }

  protected static class CheckThread extends Thread {

    protected HttpClient client;

    protected String url;

    protected Throwable exception = null;

    protected String result = "Unknown";

    public CheckThread(HttpClient client, String url) {
      super();
      setDaemon(true);
      this.client = client;
      this.url = url;
    }

    @Override
    public void run() {
      HttpGet method = new HttpGet(url);
      try {
        HttpResponse response = client.execute(method);
        try {
          if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            result = "Connection failed: " + response.getStatusLine().getReasonPhrase();
            return;
          }
          EntityUtils.consume(response.getEntity());
          result = "Connection OK";
        } finally {
          EntityUtils.consume(response.getEntity());
          method.releaseConnection();
        }
      } catch (IOException ex) {
        exception = ex;
      }
    }

    public Throwable getException() {
      return exception;
    }

    public String getResult() {
      return result;
    }
  }

  protected static class FetchTokensThread extends Thread {

    protected HttpClient client;

    protected String url;

    protected Throwable exception = null;

    protected Auth auth;

    public FetchTokensThread(HttpClient client, String url) {
      super();
      setDaemon(true);
      this.client = client;
      this.url = url;
      this.auth = null;
    }

    @Override
    public void run() {
      try {
        HttpGet method = new HttpGet(url.toString());

        HttpResponse response = client.execute(method);
        try {
          if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            exception = new ManifoldCFException("FetchTokensThread error - interface returned incorrect return code for: " + url + " - " + response.getStatusLine().toString());
            return;
          }
          JAXBContext context;
          context = JAXBContext.newInstance(Auth.class);
          Unmarshaller m = context.createUnmarshaller();
          auth = (Auth) m.unmarshal(response.getEntity().getContent());
        } catch (JAXBException ex) {
          exception = ex;
        } finally {
          EntityUtils.consume(response.getEntity());
          method.releaseConnection();
        }
      } catch (Exception ex) {
        exception = ex;
      }
    }

    public Throwable getException() {
      return exception;
    }

    public Auth getAuthResponse() {
      return auth;
    }
  }
}
