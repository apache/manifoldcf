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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.io.StringWriter;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URLEncoder;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.Header;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.ProtocolVersion;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHeader;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.RedirectException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;

import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;

public class ElasticSearchConnection
{
  protected ElasticSearchConfig config;
  
  private HttpClient client;
  
  private String serverLocation;

  private String indexName;

  private String resultDescription;

  private String callUrlSnippet;

  private String response;

  protected String jsonStatus = "\"ok\"";
  protected String jsonException = "\"error\"";

  public enum Result
  {
    OK, ERROR, UNKNOWN;
  }

  private Result result;

  protected ElasticSearchConnection(ElasticSearchConfig config, HttpClient client)
  {
    this.config = config;
    this.client = client;
    result = Result.UNKNOWN;
    response = null;
    resultDescription = "";
    callUrlSnippet = null;
    serverLocation = config.getServerLocation();
    indexName = config.getIndexName();
  }

  protected final String urlEncode(String t) throws ManifoldCFException
  {
    try
    {
      return URLEncoder.encode(t, "UTF-8");
    } catch (UnsupportedEncodingException e)
    {
      throw new ManifoldCFException(e);
    }
  }

  protected StringBuffer getApiUrl(String command, boolean checkConnection) throws ManifoldCFException
  {
    StringBuffer url = new StringBuffer(serverLocation);
    if (!serverLocation.endsWith("/"))
      url.append('/');
    if(!checkConnection)
      url.append(urlEncode(indexName)+"/");
    url.append(command);
    callUrlSnippet = url.toString();
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
        ct.join();
        Throwable t = ct.getException();
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

  private boolean handleResultCode(int code, String response)
    throws ManifoldCFException, ServiceInterruption
  {
    if (code == 200 || code == 201)
    {
      setResult(Result.OK, null);
      return true;
    }
    else if (code == 404)
    {
      setResult(Result.ERROR, "Page not found: " + response);
      throw new ManifoldCFException("Server/page not found");
    }
    else if (code >= 400 && code < 500)
    {
      setResult(Result.ERROR, "HTTP code = "+code+", Response = "+response);
      return false;
    }
    else if (code >= 500 && code < 600)
    {
      setResult(Result.ERROR, "Server exception: "+response);
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Server exception: "+response,
        new ManifoldCFException(response),
        currentTime + 300000L,
        currentTime + 20L * 60000L,
        -1,
        false);
    }
    setResult(Result.UNKNOWN, "HTTP code = "+code+", Response = "+response);
    throw new ManifoldCFException("Unexpected HTTP result code: "+code+": "+response);
  }

  private void handleHttpException(HttpException e)
    throws ManifoldCFException, ServiceInterruption {
    setResult(Result.ERROR, e.getMessage());
    throw new ManifoldCFException(e);
  }
  
  private void handleIOException(IOException e)
    throws ManifoldCFException, ServiceInterruption {
    setResult(Result.ERROR, e.getMessage());
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
        String charSet = EntityUtils.getContentCharSet(entity);
        if (charSet == null)
          charSet = "utf-8";
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

  protected void setResult(Result res, String desc)
  {
    if (res != null)
      result = res;
    if (desc != null)
      if (desc.length() > 0)
        resultDescription = desc;
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
}
