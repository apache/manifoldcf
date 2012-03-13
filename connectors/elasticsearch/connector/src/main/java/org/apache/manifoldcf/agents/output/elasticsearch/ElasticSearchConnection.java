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
import java.net.URLEncoder;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

public class ElasticSearchConnection
{
  private HttpClient client;
  
  private String serverLocation;

  private String indexName;

  private String userName;

  private String apiKey;

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
    userName = config.getUserName();
    apiKey = config.getApiKey();
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
    if (userName != null && apiKey != null && userName.length() > 0
        && apiKey.length() > 0)
    {
      url.append("&login=");
      url.append(urlEncode(userName));
      url.append("&key=");
      url.append(apiKey);
    }
    return url;
  }

  protected void call(HttpMethod method) throws ManifoldCFException
  {
    HttpClient hc = client;
    try
    {
      hc.executeMethod(method);
      if (!checkResultCode(method.getStatusCode()))
        throw new ManifoldCFException(getResultDescription());
      response = IOUtils.toString(method.getResponseBodyAsStream());
    } catch (HttpException e)
    {
      setResult(Result.ERROR, e.getMessage());
      throw new ManifoldCFException(e);
    } catch (IOException e)
    {
      setResult(Result.ERROR, e.getMessage());
      throw new ManifoldCFException(e);
    } finally
    {
      if (method != null)
        method.releaseConnection();
    }
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
