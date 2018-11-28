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

package org.apache.manifoldcf.agents.output.opensearchserver;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.io.StringWriter;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URLEncoder;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.RedirectException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class OpenSearchServerConnection {

  private String serverLocation;

  private String indexName;

  private String userName;

  private String apiKey;

  private String resultDescription;

  private String callUrlSnippet;

  private String response;

  private Document xmlResponse;

  private HttpClient httpClient;
  
  protected String xPathStatus = "/response/entry[@key='Status']/text()";
  protected String xPathException = "/response/entry[@key='Exception']/text()";

  public enum Result {
    OK, ERROR, UNKNOWN;
  }

  private Result result;

  protected OpenSearchServerConnection(HttpClient client, OpenSearchServerConfig config) {
    this.httpClient = client;
    result = Result.UNKNOWN;
    response = null;
    xmlResponse = null;
    resultDescription = "";
    callUrlSnippet = null;
    serverLocation = config.getServerLocation();
    indexName = config.getIndexName();
    userName = config.getUserName();
    apiKey = config.getApiKey();
  }

  protected final String urlEncode(String t) throws ManifoldCFException {
    try {
      return URLEncoder.encode(t, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new ManifoldCFException(e);
    }
  }

  protected StringBuffer getApiUrl(String command) throws ManifoldCFException {
    StringBuffer url = new StringBuffer(serverLocation);
    if (!serverLocation.endsWith("/"))
      url.append('/');
    url.append(command);
    url.append("?use=");
    url.append(urlEncode(indexName));
    callUrlSnippet = url.toString();
    if (userName != null && apiKey != null && userName.length() > 0
        && apiKey.length() > 0) {
      url.append("&login=");
      url.append(urlEncode(userName));
      url.append("&key=");
      url.append(apiKey);
    }
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
    CallThread ct = new CallThread(httpClient, method);
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

  private void readXmlResponse() throws ManifoldCFException {
    if (xmlResponse != null)
      return;
    StringReader sw = null;
    try {
      sw = new StringReader(response);
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true); // never forget this!
      DocumentBuilder builder;
      builder = dbf.newDocumentBuilder();
      xmlResponse = builder.parse(new InputSource(sw));
    } catch (ParserConfigurationException e) {
      throw new ManifoldCFException(e);
    } catch (SAXException e) {
      throw new ManifoldCFException(e);
    } catch (IOException e) {
      throw new ManifoldCFException(e);
    } finally {
      if (sw != null) {
        sw.close();
      }
    }
  }

  protected String checkXPath(String xPathQuery) throws ManifoldCFException {
    try {
      readXmlResponse();
      XPathFactory factory = XPathFactory.newInstance();
      XPath xpath = factory.newXPath();
      XPathExpression xPathExpr = xpath.compile(xPathQuery);
      return xPathExpr.evaluate(xmlResponse);
    } catch (XPathExpressionException e) {
      throw new ManifoldCFException(e);
    }
  }

  protected void setResult(Result res, String desc) {
    if (res != null)
      result = res;
    if (desc != null)
      if (desc.length() > 0)
        resultDescription = desc;
  }

  public String getResultDescription() {
    return resultDescription;
  }

  protected String getResponse() {
    return response;
  }

  private boolean checkResultCode(int code) {
    switch (code) {
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

  public Result getResult() {
    return result;
  }

  public String getCallUrlSnippet() {
    return callUrlSnippet;
  }
}
