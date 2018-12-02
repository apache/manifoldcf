/* $Id: ElasticSearchConnection.java 1299512 2012-03-12 00:58:38Z piergiorgio $ */

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

package org.apache.manifoldcf.agents.output.elasticsearch;

import java.io.IOException;
import java.io.Writer;
import java.io.StringWriter;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.Header;
import org.apache.http.ProtocolVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.RedirectException;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.ParseException;

import org.apache.manifoldcf.agents.interfaces.IOutputHistoryActivity;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.util.URLEncoder;
import org.apache.manifoldcf.crawler.system.Logging;

public class ElasticSearchConnection
{
  
  public enum Result
  {
    OK, ERROR, UNKNOWN;
  }

  protected final ElasticSearchConfig config;
  
  private final HttpClient client;
  private final String serverLocation;
  private final String indexName;
  private final String userName;
  private final String userPassword;
  
  private String resultDescription = "";
  private String callUrlSnippet = null;
  private String response = null;
  private String resultCode = null;
  private Result result = Result.UNKNOWN;
  
  protected final static String jsonException = "\"error\"";

  protected ElasticSearchConnection(ElasticSearchConfig config, HttpClient client)
  {
    this.config = config;
    this.client = client;
    serverLocation = config.getServerLocation();
    indexName = config.getIndexName();
    userName = config.getUserName();
    userPassword = config.getPassword();
  }

  protected StringBuffer getApiUrl(String command, boolean checkConnection) throws ManifoldCFException
  {
    String basicAuth = "";
    if (userName.length() > 0 && userPassword.length() >0) {
      basicAuth = userName + ":" + userPassword + "@";
    }
    Logging.connectors.debug("Auth: " + basicAuth);

    String[] serverLocationSplit = serverLocation.split("://",2);
    StringBuffer url;
    if (basicAuth.length() > 0) {
      url = new StringBuffer(serverLocationSplit[0] + "://" + basicAuth + serverLocationSplit[1]);
    }
    else {
      url = new StringBuffer(serverLocationSplit[0] + "://" + basicAuth + serverLocationSplit[1]);
    }
    if (!serverLocation.endsWith("/"))
      url.append('/');

    Logging.connectors.debug("Url: " + url);

    if(!checkConnection)
      url.append(URLEncoder.encode(indexName)).append("/");
    url.append(command);
    callUrlSnippet = url.toString();

    Logging.connectors.debug("UrlEnc: " + url);

    return url;
  }

  protected static class CallThread extends Thread
  {
    protected final HttpClient client;
    protected final HttpRequestBase method;
    protected int resultCode = -1;
    protected String response = null;
    protected Throwable exception = null;
    
    public CallThread(HttpClient client, HttpRequestBase method)
    {
      this.client = client;
      this.method = method;
      setDaemon(true);
    }
    
    @Override
    public void run()
    {
      try
      {
        try
        {
          HttpResponse resp = client.execute(method);
          resultCode = resp.getStatusLine().getStatusCode();
          response = getResponseBodyAsString(resp.getEntity());
        }
        finally
        {
          method.abort();
        }
      }
      catch (java.net.SocketTimeoutException e)
      {
        exception = e;
      }
      catch (InterruptedIOException e)
      {
        // Just exit
      }
      catch (Throwable e)
      {
        exception = e;
      }
    }

    public void finishUp()
      throws HttpException, IOException, InterruptedException
    {
      join();
      Throwable t = exception;
      if (t != null)
      {
        if (t instanceof HttpException)
          throw (HttpException)t;
        else if (t instanceof IOException)
          throw (IOException)t;
        else if (t instanceof RuntimeException)
          throw (RuntimeException)t;
        else if (t instanceof Error)
          throw (Error)t;
        else
          throw new RuntimeException("Unexpected exception thrown: "+t.getMessage(),t);
      }
    }
    
    public int getResultCode()
    {
      return resultCode;
    }
    
    public String getResponse()
    {
      return response;
    }
    
    public Throwable getException()
    {
      return exception;
    }
  }
  
  /** Call ElasticSearch.
  *@return false if there was a "rejection".
  */
  protected boolean call(HttpRequestBase method)
    throws ManifoldCFException, ServiceInterruption
  {
    CallThread ct = new CallThread(client, method);
    try
    {
      ct.start();
      try
      {
        ct.finishUp();
        response = ct.getResponse();
        return handleResultCode(ct.getResultCode(), response);
      }
      catch (InterruptedException e)
      {
        ct.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
    }
    catch (HttpException e)
    {
      handleHttpException(e);
      return false;
    }
    catch (IOException e)
    {
      handleIOException(e);
      return false;
    }
  }

  protected boolean handleResultCode(int code, String response)
    throws ManifoldCFException, ServiceInterruption
  {
    if (code == 200 || code == 201)
    {
      setResult("OK",Result.OK, null);
      return true;
    }
    else if (code == 404)
    {
      setResult(IOutputHistoryActivity.HTTP_ERROR,Result.ERROR, "Page not found: " + response);
      throw new ManifoldCFException("Server/page not found");
    }
    else if (code >= 400 && code < 500)
    {
      setResult(IOutputHistoryActivity.HTTP_ERROR,Result.ERROR, "HTTP code = "+code+", Response = "+response);
      return false;
    }
    else if (code >= 500 && code < 600)
    {
      setResult(IOutputHistoryActivity.HTTP_ERROR,Result.ERROR, "Server exception: "+response);
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Server exception: "+response,
        new ManifoldCFException(response),
        currentTime + 300000L,
        currentTime + 20L * 60000L,
        -1,
        false);
    }
    setResult(IOutputHistoryActivity.HTTP_ERROR,Result.UNKNOWN, "HTTP code = "+code+", Response = "+response);
    throw new ManifoldCFException("Unexpected HTTP result code: "+code+": "+response);
  }

  protected void handleHttpException(HttpException e)
    throws ManifoldCFException, ServiceInterruption {
    setResult(e.getClass().getSimpleName().toUpperCase(Locale.ROOT),Result.ERROR, e.getMessage());
    throw new ManifoldCFException(e);
  }
  
  protected void handleIOException(IOException e)
    throws ManifoldCFException, ServiceInterruption {
    if (e instanceof java.io.InterruptedIOException && !(e instanceof java.net.SocketTimeoutException))
      throw new ManifoldCFException(e.getMessage(),ManifoldCFException.INTERRUPTED);
    setResult(e.getClass().getSimpleName().toUpperCase(Locale.ROOT),Result.ERROR, e.getMessage());
    long currentTime = System.currentTimeMillis();
    // All IO exceptions are treated as service interruptions, retried for an hour
    throw new ServiceInterruption("IO exception: "+e.getMessage(),e,
        currentTime + 60000L,
        currentTime + 1L * 60L * 60000L,
        -1,
        true);
  }
    
  private static String getResponseBodyAsString(HttpEntity entity)
    throws IOException, HttpException {
    InputStream is = entity.getContent();
    if (is != null)
    {
      try
      {
        Charset charSet;
        try
        {
          ContentType ct = ContentType.get(entity);
          if (ct == null)
            charSet = StandardCharsets.UTF_8;
          else
            charSet = ct.getCharset();
        }
        catch (ParseException e)
        {
          charSet = StandardCharsets.UTF_8;
        }
        char[] buffer = new char[65536];
        Reader r = new InputStreamReader(is,charSet);
        Writer w = new StringWriter();
        try
        {
          while (true)
          {
            int amt = r.read(buffer);
            if (amt == -1)
              break;
            w.write(buffer,0,amt);
          }
        }
        finally
        {
          w.flush();
        }
        return w.toString();
      }
      finally
      {
        is.close();
      }
    }
    return "";
  }

  protected String checkJson(String jsonQuery) throws ManifoldCFException
  {
    String result = null;
    if (response != null)
    {
      String[] tokens = response.replaceAll("\\{", "").replaceAll("\\}", "")
          .split(",");
      for (String token : tokens)
        if (token.contains(jsonQuery))
          result = token.substring(token.indexOf(":") + 1);
    }
    return result;
  }

  protected void setResult(String resultCode, Result res, String desc)
  {
    if (res != null)
      result = res;
    if (desc != null)
      if (desc.length() > 0)
        resultDescription = desc;
    setResultCode(resultCode);
  }

  public String getResultDescription()
  {
    return resultDescription;
  }

  protected String getResponse()
  {
    return response;
  }


  public Result getResult()
  {
    return result;
  }

  public String getCallUrlSnippet()
  {
    return callUrlSnippet;
  }

  public String getResultCode(){ return resultCode; }

  public void setResultCode(String resultCode){ this.resultCode = resultCode; }
}
