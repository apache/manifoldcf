package com.mcplusa.manifoldcf.agents.output.appsearch;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Locale;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.HttpResponse;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.client.AuthCache;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;

import org.apache.manifoldcf.agents.interfaces.IOutputHistoryActivity;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.util.URLEncoder;

public class AppSearchConnection {

  public enum Result {
    OK, ERROR, UNKNOWN;
  }

  protected final AppSearchConfig config;

  private final HttpClient client;
  private final String serverLocation;
  private final String engineName;

  private String resultDescription = "";
  private String callUrlSnippet = null;
  private String response = null;
  private String resultCode = null;
  private Result result = Result.UNKNOWN;

  protected final static String jsonException = "\"errors\"";

  protected AppSearchConnection(AppSearchConfig config, HttpClient client) {
    this.config = config;
    this.client = client;
    serverLocation = config.getServerLocation();
    engineName = config.getEngineName();
  }

  protected StringBuffer getApiUrl(String command) throws ManifoldCFException {
    StringBuffer url = new StringBuffer(serverLocation);
    if (!serverLocation.endsWith("/")) {
      url.append('/');
    }

    url.append("api/as/v1/engines/").append(URLEncoder.encode(engineName)).append("/");
    url.append(command);
    callUrlSnippet = url.toString();

    return url;
  }

  protected static class CallThread extends Thread {

    protected final HttpClient client;
    protected final HttpRequestBase method;
    protected int resultCode = -1;
    protected String response = null;
    protected Throwable exception = null;

    public CallThread(HttpClient client, HttpRequestBase method) {
      this.client = client;
      this.method = method;
      setDaemon(true);
    }

    @Override
    public void run() {
      HttpHost target = new HttpHost(method.getURI().getHost(), method.getURI().getPort(), method.getURI().getScheme());
      // Create AuthCache instance
      AuthCache authCache = new BasicAuthCache();
      // Generate BASIC scheme object and add it to the local
      // auth cache
      BasicScheme basicAuth = new BasicScheme();
      authCache.put(target, basicAuth);

      // Add AuthCache to the execution context
      HttpClientContext localContext = HttpClientContext.create();
      localContext.setAuthCache(authCache);

      try {
	try {
	  HttpResponse resp = client.execute(method, localContext);
	  resultCode = resp.getStatusLine().getStatusCode();
	  response = EntityUtils.toString(resp.getEntity(), "UTF-8");
	} finally {
	  method.abort();
	}
      } catch (java.net.SocketTimeoutException e) {
	exception = e;
      } catch (InterruptedIOException e) {
	// Just exit
      } catch (Throwable e) {
	exception = e;
      }
    }

    public void finishUp()
	    throws HttpException, IOException, InterruptedException {
      join();
      Throwable t = exception;
      if (t != null) {
	if (t instanceof HttpException) {
	  throw (HttpException) t;
	} else if (t instanceof IOException) {
	  throw (IOException) t;
	} else if (t instanceof RuntimeException) {
	  throw (RuntimeException) t;
	} else if (t instanceof Error) {
	  throw (Error) t;
	} else {
	  throw new RuntimeException("Unexpected exception thrown: " + t.getMessage(), t);
	}
      }
    }

    public int getResultCode() {
      return resultCode;
    }

    public String getResponse() {
      return response;
    }

    public Throwable getException() {
      return exception;
    }
  }

  /**
   * Call AppSearch.
   *
   * @param method
   * @return false if there was a "rejection".
   * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException
   * @throws org.apache.manifoldcf.agents.interfaces.ServiceInterruption
   */
  protected boolean call(HttpRequestBase method)
	  throws ManifoldCFException, ServiceInterruption {
    method.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.config.getSecretApiKey());
    method.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    CallThread ct = new CallThread(client, method);
    try {
      ct.start();
      try {
	ct.finishUp();
	response = ct.getResponse();
	return handleResultCode(ct.getResultCode(), response);
      } catch (InterruptedException e) {
	ct.interrupt();
	throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
      }
    } catch (HttpException e) {
      handleHttpException(e);
      return false;
    } catch (IOException e) {
      handleIOException(e);
      return false;
    }
  }

  protected boolean handleResultCode(int code, String response)
	  throws ManifoldCFException, ServiceInterruption {
    if (code == 200 || code == 201) {
      setResult("OK", Result.OK, null);
      return true;
    } else if (code == 404) {
      setResult(IOutputHistoryActivity.HTTP_ERROR, Result.ERROR, "Page not found: " + response);
      throw new ManifoldCFException("Server/page not found");
    } else if (code >= 400 && code < 500) {
      setResult(IOutputHistoryActivity.HTTP_ERROR, Result.ERROR, "HTTP code = " + code + ", Response = " + response);
      return false;
    } else if (code >= 500 && code < 600) {
      setResult(IOutputHistoryActivity.HTTP_ERROR, Result.ERROR, "Server exception: " + response);
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Server exception: " + response,
	      new ManifoldCFException(response),
	      currentTime + 300000L,
	      currentTime + 20L * 60000L,
	      -1,
	      false);
    }
    setResult(IOutputHistoryActivity.HTTP_ERROR, Result.UNKNOWN, "HTTP code = " + code + ", Response = " + response);
    throw new ManifoldCFException("Unexpected HTTP result code: " + code + ": " + response);
  }

  protected void handleHttpException(HttpException e)
	  throws ManifoldCFException, ServiceInterruption {
    setResult(e.getClass().getSimpleName().toUpperCase(Locale.ROOT), Result.ERROR, e.getMessage());
    throw new ManifoldCFException(e);
  }

  protected void handleIOException(IOException e)
	  throws ManifoldCFException, ServiceInterruption {
    if (e instanceof java.io.InterruptedIOException && !(e instanceof java.net.SocketTimeoutException)) {
      throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);
    }
    setResult(e.getClass().getSimpleName().toUpperCase(Locale.ROOT), Result.ERROR, e.getMessage());
    long currentTime = System.currentTimeMillis();
    // All IO exceptions are treated as service interruptions, retried for an hour
    throw new ServiceInterruption("IO exception: " + e.getMessage(), e,
	    currentTime + 60000L,
	    currentTime + 1L * 60L * 60000L,
	    -1,
	    true);
  }

  protected String checkJson(String jsonQuery) throws ManifoldCFException {
    if (response != null) {
      Gson gson = new Gson();
      ResponseBody res = null;

      // check if response is an array or object
      if (response.startsWith("[")) {
	ResponseBody[] resArray = gson.fromJson(response, ResponseBody[].class);
	if (resArray.length > 0) {
	  res = resArray[0];
	}
      } else if (response.startsWith("{")) {
	res = gson.fromJson(response, ResponseBody.class);
      }

      if (res != null) {
	if (res.getErrors() != null && res.getErrors().length > 0) {
	  return res.getErrors()[0];
	} else {
	  return null;
	}
      }

      String[] tokens = response.replaceAll("\\[", "").replaceAll("\\]", "").replaceAll("\\{", "").replaceAll("\\}", "").split(",");
      for (String token : tokens) {
	if (token.contains(jsonQuery)) {
	  return token.substring(token.indexOf(":") + 1);
	}
      }
    }

    return null;
  }

  protected void setResult(String resultCode, Result res, String desc) {
    if (res != null) {
      result = res;
    }
    if (desc != null) {
      if (desc.length() > 0) {
	resultDescription = desc;
      }
    }
    setResultCode(resultCode);
  }

  public String getResultDescription() {
    return resultDescription;
  }

  protected String getResponse() {
    return response;
  }

  public Result getResult() {
    return result;
  }

  public String getCallUrlSnippet() {
    return callUrlSnippet;
  }

  public String getResultCode() {
    return resultCode;
  }

  public void setResultCode(String resultCode) {
    this.resultCode = resultCode;
  }
}
