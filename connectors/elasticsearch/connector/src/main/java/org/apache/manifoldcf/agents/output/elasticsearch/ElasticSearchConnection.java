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

public class ElasticSearchConnection
{
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
  
  protected void call(HttpRequestBase method) throws ManifoldCFException
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
        
        if (!checkResultCode(ct.getResultCode()))
          throw new ManifoldCFException(getResultDescription());
        response = ct.getResponse();
      }
      catch (InterruptedException e)
      {
        ct.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
    }
    catch (HttpException e)
    {
      setResult(Result.ERROR, e.getMessage());
      throw new ManifoldCFException(e);
    }
    catch (IOException e)
    {
      setResult(Result.ERROR, e.getMessage());
      throw new ManifoldCFException(e);
    }
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

  private boolean checkResultCode(int code)
  {
    switch (code)
    {
    case 0:
      setResult(Result.UNKNOWN, null);
      return false;
    case 200:
      setResult(Result.OK, null);
      return true;
    case 404:
      setResult(Result.ERROR, "Server/page not found");
      return false;
    default:
      setResult(Result.ERROR, null);
      return false;
    }
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
