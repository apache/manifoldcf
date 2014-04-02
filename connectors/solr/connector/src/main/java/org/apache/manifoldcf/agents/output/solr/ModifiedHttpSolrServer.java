/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.output.solr;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.ContentType;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.Charset;

/** This class overrides and somewhat changes the behavior of the
* SolrJ HttpSolrServer class.  The point of all this is simply to get
* the right information to Tika.  When SolrJ uses GET or POST but not
* multipart-post, it does not include multipart headers that Tika uses -
* specifically, the name of the document and the length of the document.
* Patches have been submitted to the SOLR ticket queue to address this
* problem in a method-insensitive way, but so far there has been no sign that
* the Solr team is interested in committing them.
*/
public class ModifiedHttpSolrServer extends HttpSolrServer
{
  // Here we duplicate all the private fields we need
  
  private static final String UTF_8 = "UTF-8";
  private static final String DEFAULT_PATH = "/select";

  private static Charset UTF8_CHARSET;
  static
  {
    try
    {
      UTF8_CHARSET = Charset.forName(UTF_8);
    }
    catch (Exception e)
    {
      e.printStackTrace();
      System.exit(-100);
      UTF8_CHARSET = null;
    }
  }
  
  private final HttpClient httpClient;
  private boolean followRedirects = false;
  private int maxRetries = 0;
  private boolean useMultiPartPost = true;

  public ModifiedHttpSolrServer(String baseURL, HttpClient client, ResponseParser parser) {
    super(baseURL, client, parser);
    httpClient = client;
  }
  
  @Override
  public NamedList<Object> request(final SolrRequest request,
      final ResponseParser processor) throws SolrServerException, IOException {
    HttpRequestBase method = null;
    InputStream is = null;
    SolrParams params = request.getParams();
    Collection<ContentStream> streams = requestWriter.getContentStreams(request);
    String path = requestWriter.getPath(request);
    if (path == null || !path.startsWith("/")) {
      path = DEFAULT_PATH;
    }
    
    ResponseParser parser = request.getResponseParser();
    if (parser == null) {
      parser = this.parser;
    }
    
    // The parser 'wt=' and 'version=' params are used instead of the original
    // params
    ModifiableSolrParams wparams = new ModifiableSolrParams(params);
    if (parser != null) {
      wparams.set(CommonParams.WT, parser.getWriterType());
      wparams.set(CommonParams.VERSION, parser.getVersion());
    }
    if (invariantParams != null) {
      wparams.add(invariantParams);
    }
    params = wparams;
    
    int tries = maxRetries + 1;
    try {
      while( tries-- > 0 ) {
        // Note: since we aren't do intermittent time keeping
        // ourselves, the potential non-timeout latency could be as
        // much as tries-times (plus scheduling effects) the given
        // timeAllowed.
        try {
          if( SolrRequest.METHOD.GET == request.getMethod() ) {
            if( streams != null ) {
              throw new SolrException( SolrException.ErrorCode.BAD_REQUEST, "GET can't send streams!" );
            }
            method = new HttpGet( baseUrl + path + ClientUtils.toQueryString( params, false ) );
          }
          else if( SolrRequest.METHOD.POST == request.getMethod() ) {

            String url = baseUrl + path;
            boolean hasNullStreamName = false;
            if (streams != null) {
              for (ContentStream cs : streams) {
                if (cs.getName() == null) {
                  hasNullStreamName = true;
                  break;
                }
              }
            }
            boolean isMultipart = (this.useMultiPartPost || ( streams != null && streams.size() > 1 )) && !hasNullStreamName;
            
            LinkedList<NameValuePair> postParams = new LinkedList<NameValuePair>();
            if (streams == null || isMultipart) {
              HttpPost post = new HttpPost(url);
              post.setHeader("Content-Charset", "UTF-8");
              if (!isMultipart) {
                post.addHeader("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
              }

              List<FormBodyPart> parts = new LinkedList<FormBodyPart>();
              Iterator<String> iter = params.getParameterNamesIterator();
              while (iter.hasNext()) {
                String p = iter.next();
                String[] vals = params.getParams(p);
                if (vals != null) {
                  for (String v : vals) {
                    if (isMultipart) {
                      parts.add(new FormBodyPart(p, new StringBody(v, Charset.forName("UTF-8"))));
                    } else {
                      postParams.add(new BasicNameValuePair(p, v));
                    }
                  }
                }
              }

              if (isMultipart && streams != null) {
                for (ContentStream content : streams) {
                  String contentType = content.getContentType();
                  if(contentType==null) {
                    contentType = "application/octet-stream"; // default
                  }
                  String contentName = content.getName();
                  parts.add(new FormBodyPart(contentName, 
                       new InputStreamBody(
                           content.getStream(), 
                           contentType, 
                           content.getName())));
                }
              }
              
              if (parts.size() > 0) {
                ModifiedMultipartEntity entity = new ModifiedMultipartEntity(HttpMultipartMode.STRICT, null, UTF8_CHARSET);
                for(FormBodyPart p: parts) {
                  entity.addPart(p);
                }
                post.setEntity(entity);
              } else {
                //not using multipart
                post.setEntity(new UrlEncodedFormEntity(postParams, "UTF-8"));
              }

              method = post;
            }
            // It is has one stream, it is the post body, put the params in the URL
            else {
              String pstr = ClientUtils.toQueryString(params, false);
              HttpPost post = new HttpPost(url + pstr);

              // Single stream as body
              // Using a loop just to get the first one
              final ContentStream[] contentStream = new ContentStream[1];
              for (ContentStream content : streams) {
                contentStream[0] = content;
                break;
              }
              if (contentStream[0] instanceof RequestWriter.LazyContentStream) {
                post.setEntity(new InputStreamEntity(contentStream[0].getStream(), -1) {
                  @Override
                  public Header getContentType() {
                    return new BasicHeader("Content-Type", contentStream[0].getContentType());
                  }
                  
                  @Override
                  public boolean isRepeatable() {
                    return false;
                  }
                  
                });
              } else {
                post.setEntity(new InputStreamEntity(contentStream[0].getStream(), -1) {
                  @Override
                  public Header getContentType() {
                    return new BasicHeader("Content-Type", contentStream[0].getContentType());
                  }
                  
                  @Override
                  public boolean isRepeatable() {
                    return false;
                  }
                });
              }
              method = post;
            }
          }
          else {
            throw new SolrServerException("Unsupported method: "+request.getMethod() );
          }
        }
        catch( NoHttpResponseException r ) {
          method = null;
          if(is != null) {
            is.close();
          }
          // If out of tries then just rethrow (as normal error).
          if (tries < 1) {
            throw r;
          }
        }
      }
    } catch (IOException ex) {
      throw new SolrServerException("error reading streams", ex);
    }
    
    // XXX client already has this set, is this needed?
    method.getParams().setParameter(ClientPNames.HANDLE_REDIRECTS,
        followRedirects);
    method.addHeader("User-Agent", AGENT);
    
    InputStream respBody = null;
    boolean shouldClose = true;
    
    try {
      // Execute the method.
      final HttpResponse response = httpClient.execute(method);
      int httpStatus = response.getStatusLine().getStatusCode();
      
      // Read the contents
      respBody = response.getEntity().getContent();
      
      // handle some http level checks before trying to parse the response
      switch (httpStatus) {
        case HttpStatus.SC_OK:
        case HttpStatus.SC_BAD_REQUEST:
        case HttpStatus.SC_CONFLICT:  // 409
          break;
        case HttpStatus.SC_MOVED_PERMANENTLY:
        case HttpStatus.SC_MOVED_TEMPORARILY:
          if (!followRedirects) {
            throw new SolrServerException("Server at " + getBaseURL()
                + " sent back a redirect (" + httpStatus + ").");
          }
          break;
        default:
          throw new SolrException(SolrException.ErrorCode.getErrorCode(httpStatus), "Server at " + getBaseURL()
              + " returned non ok status:" + httpStatus + ", message:"
              + response.getStatusLine().getReasonPhrase());
          
      }
      if (processor == null) {
        // no processor specified, return raw stream
        NamedList<Object> rsp = new NamedList<Object>();
        rsp.add("stream", respBody);
        // Only case where stream should not be closed
        shouldClose = false;
        return rsp;
      }
      String charset = ContentType.getOrDefault(response.getEntity()).getCharset().name();
      NamedList<Object> rsp = processor.processResponse(respBody, charset);
      if (httpStatus != HttpStatus.SC_OK) {
        String reason = null;
        try {
          NamedList err = (NamedList) rsp.get("error");
          if (err != null) {
            reason = (String) err.get("msg");
            // TODO? get the trace?
          }
        } catch (Exception ex) {}
        if (reason == null) {
          StringBuilder msg = new StringBuilder();
          msg.append(response.getStatusLine().getReasonPhrase());
          msg.append("\n\n");
          msg.append("request: " + method.getURI());
          reason = java.net.URLDecoder.decode(msg.toString(), UTF_8);
        }
        throw new SolrException(
            SolrException.ErrorCode.getErrorCode(httpStatus), reason);
      }
      return rsp;
    } catch (ConnectException e) {
      throw new SolrServerException("Server refused connection at: "
          + getBaseURL(), e);
    } catch (SocketTimeoutException e) {
      throw new SolrServerException(
          "Timeout occured while waiting response from server at: "
              + getBaseURL(), e);
    } catch (IOException e) {
      throw new SolrServerException(
          "IOException occured when talking to server at: " + getBaseURL(), e);
    } finally {
      if (respBody != null && shouldClose) {
        try {
          respBody.close();
        } catch (Throwable t) {} // ignore
      }
    }
  }

  @Override
  public void setFollowRedirects(boolean followRedirects) {
    super.setFollowRedirects(followRedirects);
    this.followRedirects = followRedirects;
  }

}
