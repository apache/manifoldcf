/*
* Copyright 2001-2004 The Apache Software Foundation.
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
*
* $Id$
*/
package org.apache.manifoldcf.connectorcommon.common;

import org.apache.manifoldcf.connectorcommon.common.XThreadInputStream;

import org.apache.axis.AxisFault;
import org.apache.axis.Constants;
import org.apache.axis.Message;
import org.apache.axis.MessageContext;
import org.apache.axis.components.logger.LogFactory;
import org.apache.axis.components.net.CommonsHTTPClientProperties;
import org.apache.axis.components.net.CommonsHTTPClientPropertiesFactory;
import org.apache.axis.components.net.TransportClientProperties;
import org.apache.axis.components.net.TransportClientPropertiesFactory;
import org.apache.axis.transport.http.HTTPConstants;
import org.apache.axis.handlers.BasicHandler;
import org.apache.axis.soap.SOAP12Constants;
import org.apache.axis.soap.SOAPConstants;
import org.apache.axis.utils.JavaUtils;
import org.apache.axis.utils.Messages;
import org.apache.axis.utils.NetworkUtils;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.Header;
import org.apache.http.ProtocolVersion;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.entity.ContentType;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.client.RedirectException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.HttpException;
import org.apache.http.ParseException;

import org.apache.commons.logging.Log;

import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.io.Writer;
import java.io.StringWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.nio.charset.Charset;

/* Class to use httpcomponents to communicate with a SOAP server.
* I've replaced the original rather complicated class with a much simpler one that
* relies on having an HttpClient object passed into the invoke() method.  Since
* the object is already set up, not much needs to be done in here.
*/

public class CommonsHTTPSender extends BasicHandler {

  public static final String HTTPCLIENT_PROPERTY = "ManifoldCF_HttpClient";

  /** Field log           */
  protected static Log log =
    LogFactory.getLog(CommonsHTTPSender.class.getName());

  /** Properties */
  protected CommonsHTTPClientProperties clientProperties;

  public CommonsHTTPSender() {
    this.clientProperties = CommonsHTTPClientPropertiesFactory.create();
  }

  /**
  * invoke creates a socket connection, sends the request SOAP message and then
  * reads the response SOAP message back from the SOAP server
  *
  * @param msgContext the messsage context
  *
  * @throws AxisFault
  */
  public void invoke(MessageContext msgContext) throws AxisFault {
    if (log.isDebugEnabled())
    {
      log.debug(Messages.getMessage("enter00",
        "CommonsHTTPSender::invoke"));
    }
    
    // Catch all exceptions and turn them into AxisFaults
    try
    {
      // Get the URL
      URL targetURL =
        new URL(msgContext.getStrProp(MessageContext.TRANS_URL));

      // Get the HttpClient
      HttpClient httpClient = (HttpClient)msgContext.getProperty(HTTPCLIENT_PROPERTY);

      boolean posting = true;
      // If we're SOAP 1.2, allow the web method to be set from the
      // MessageContext.
      if (msgContext.getSOAPConstants() == SOAPConstants.SOAP12_CONSTANTS) {
        String webMethod = msgContext.getStrProp(SOAP12Constants.PROP_WEBMETHOD);
        if (webMethod != null) {
          posting = webMethod.equals(HTTPConstants.HEADER_POST);
        }
      }

      boolean http10 = false;
      String httpVersion = msgContext.getStrProp(MessageContext.HTTP_TRANSPORT_VERSION);
      if (httpVersion != null) {
        if (httpVersion.equals(HTTPConstants.HEADER_PROTOCOL_V10)) {
          http10 = true;
        }
        // assume 1.1
      }

      HttpRequestBase method;
        
      if (posting) {
        HttpPost postMethod = new HttpPost(targetURL.toString());
          
        // set false as default, addContetInfo can overwrite
        //HttpProtocolParams.setUseExpectContinue(postMethod.getParams(),false);

        Message reqMessage = msgContext.getRequestMessage();
          
        boolean httpChunkStream = addContextInfo(postMethod, msgContext);

        HttpEntity requestEntity = null;
        requestEntity = new MessageRequestEntity(reqMessage, httpChunkStream,
          http10 || !httpChunkStream);
        postMethod.setEntity(requestEntity);
        method = postMethod;
      } else {
        method = new HttpGet(targetURL.toString());
      }
        
      //if (http10)
      //  HttpProtocolParams.setVersion(method.getParams(),new ProtocolVersion("HTTP",1,0));

      BackgroundHTTPThread methodThread = new BackgroundHTTPThread(httpClient,method);
      methodThread.start();
      try
      {
        int returnCode = methodThread.getResponseCode();
          
        String contentType =
          getHeader(methodThread, HTTPConstants.HEADER_CONTENT_TYPE);
        String contentLocation =
          getHeader(methodThread, HTTPConstants.HEADER_CONTENT_LOCATION);
        String contentLength =
          getHeader(methodThread, HTTPConstants.HEADER_CONTENT_LENGTH);
        
        if ((returnCode > 199) && (returnCode < 300)) {

          // SOAP return is OK - so fall through
        } else if (msgContext.getSOAPConstants() ==
          SOAPConstants.SOAP12_CONSTANTS) {
          // For now, if we're SOAP 1.2, fall through, since the range of
          // valid result codes is much greater
        } else if ((contentType != null) && !contentType.equals("text/html")
          && ((returnCode > 499) && (returnCode < 600))) {

          // SOAP Fault should be in here - so fall through
        } else {
          String statusMessage = methodThread.getResponseStatus();
          AxisFault fault = new AxisFault("HTTP",
            "(" + returnCode + ")"
          + statusMessage, null,
            null);

          fault.setFaultDetailString(
            Messages.getMessage("return01",
            "" + returnCode,
            getResponseBodyAsString(methodThread)));
          fault.addFaultDetail(Constants.QNAME_FAULTDETAIL_HTTPERRORCODE,
            Integer.toString(returnCode));
          throw fault;
        }

        String contentEncoding =
         methodThread.getFirstHeader(HTTPConstants.HEADER_CONTENT_ENCODING);
        if (contentEncoding != null) {
          AxisFault fault = new AxisFault("HTTP",
            "unsupported content-encoding of '"
          + contentEncoding
          + "' found", null, null);
          throw fault;
        }

        Map<String,List<String>> responseHeaders = methodThread.getResponseHeaders();

        InputStream dataStream = methodThread.getSafeInputStream();

        Message outMsg = new Message(new BackgroundInputStream(methodThread,dataStream),
          false, contentType, contentLocation);
          
        // Transfer HTTP headers of HTTP message to MIME headers of SOAP message
        MimeHeaders responseMimeHeaders = outMsg.getMimeHeaders();
        for (String name : responseHeaders.keySet())
        {
          List<String> values = responseHeaders.get(name);
          for (String value : values) {
            responseMimeHeaders.addHeader(name,value);
          }
        }
        outMsg.setMessageType(Message.RESPONSE);
          
        // Put the message in the message context.
        msgContext.setResponseMessage(outMsg);
        
        // Pass off the method thread to the stream for closure
        methodThread = null;
      }
      finally
      {
        if (methodThread != null)
        {
          methodThread.abort();
          methodThread.finishUp();
        }
      }

    } catch (AxisFault af) {
      log.debug(af);
      throw af;
    } catch (Exception e) {
      log.debug(e);
      throw AxisFault.makeFault(e);
    }

    if (log.isDebugEnabled()) {
      log.debug(Messages.getMessage("exit00",
        "CommonsHTTPSender::invoke"));
    }
  }

  /**
  * Extracts info from message context.
  *
  * @param method Post or get method
  * @param msgContext the message context
  */
  private static boolean addContextInfo(HttpPost method,
    MessageContext msgContext)
    throws AxisFault {

    boolean httpChunkStream = false;

    // Get SOAPAction, default to ""
    String action = msgContext.useSOAPAction()
      ? msgContext.getSOAPActionURI()
      : "";

    if (action == null) {
      action = "";
    }

    Message msg = msgContext.getRequestMessage();

    if (msg != null){

      // First, transfer MIME headers of SOAPMessage to HTTP headers.
      // Some of these might be overridden later.
      MimeHeaders mimeHeaders = msg.getMimeHeaders();
      if (mimeHeaders != null) {
        for (Iterator i = mimeHeaders.getAllHeaders(); i.hasNext(); ) {
          MimeHeader mimeHeader = (MimeHeader) i.next();
          method.addHeader(mimeHeader.getName(),
            mimeHeader.getValue());
        }
      }

      method.setHeader(new BasicHeader(HTTPConstants.HEADER_CONTENT_TYPE,
        msg.getContentType(msgContext.getSOAPConstants())));
    }
    
    method.setHeader(new BasicHeader("Accept","*/*"));

    method.setHeader(new BasicHeader(HTTPConstants.HEADER_SOAP_ACTION,
      "\"" + action + "\""));
    method.setHeader(new BasicHeader(HTTPConstants.HEADER_USER_AGENT, Messages.getMessage("axisUserAgent")));


    // process user defined headers for information.
    Hashtable userHeaderTable =
      (Hashtable) msgContext.getProperty(HTTPConstants.REQUEST_HEADERS);

    if (userHeaderTable != null) {
      for (Iterator e = userHeaderTable.entrySet().iterator();
        e.hasNext();) {
        Map.Entry me = (Map.Entry) e.next();
        Object keyObj = me.getKey();

        if (null == keyObj) {
          continue;
        }
        String key = keyObj.toString().trim();
        String value = me.getValue().toString().trim();

        //if (key.equalsIgnoreCase(HTTPConstants.HEADER_EXPECT) &&
        //  value.equalsIgnoreCase(HTTPConstants.HEADER_EXPECT_100_Continue)) {
        //  HttpProtocolParams.setUseExpectContinue(method.getParams(),true);
        //} else 
        if (key.equalsIgnoreCase(HTTPConstants.HEADER_TRANSFER_ENCODING_CHUNKED)) {
          String val = me.getValue().toString();
          if (null != val)  {
            httpChunkStream = JavaUtils.isTrue(val);
          }
        } else {
          method.addHeader(key, value);
        }
      }
    }
    
    return httpChunkStream;
  }

  private static String getHeader(BackgroundHTTPThread methodThread, String headerName)
    throws IOException, InterruptedException, HttpException {
    String header = methodThread.getFirstHeader(headerName);
    return (header == null) ? null : header.trim();
  }

  private static String getResponseBodyAsString(BackgroundHTTPThread methodThread)
    throws IOException, InterruptedException, HttpException {
    InputStream is = methodThread.getSafeInputStream();
    if (is != null)
    {
      try
      {
        Charset charSet = methodThread.getCharSet();
        if (charSet == null)
          charSet = StandardCharsets.UTF_8;
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
  
  private static class MessageRequestEntity implements HttpEntity {

    private final Message message;
    private final boolean httpChunkStream; //Use HTTP chunking or not.
    private final boolean contentLengthNeeded;

    public MessageRequestEntity(Message message, boolean httpChunkStream, boolean contentLengthNeeded) {
      this.message = message;
      this.httpChunkStream = httpChunkStream;
      this.contentLengthNeeded = contentLengthNeeded;
    }

    @Override
    public boolean isChunked() {
      return httpChunkStream;
    }
    
    @Override
    @Deprecated
    public void consumeContent()
      throws IOException {
      EntityUtils.consume(this);
    }
    
    @Override
    public boolean isRepeatable() {
      return true;
    }

    @Override
    public boolean isStreaming() {
      return false;
    }
    
    @Override
    public InputStream getContent()
      throws IOException, IllegalStateException {
      // MHL
      return null;
    }
    
    @Override
    public void writeTo(OutputStream out)
      throws IOException {
      try {
        this.message.writeTo(out);
      } catch (SOAPException e) {
        throw new IOException(e.getMessage());
      }
    }

    @Override
    public long getContentLength() {
      if (contentLengthNeeded) {
        try {
          return message.getContentLength();
        } catch (Exception e) {
        }
      }
      // Unknown (chunked) length
      return -1L;
    }

    @Override
    public Header getContentType() {
      return null; // a separate header is added
    }

    @Override
    public Header getContentEncoding() {
      return null;
    }
  }

  /** This input stream wraps a background http transaction thread, so that
  * the thread is ended when the stream is closed.
  */
  private static class BackgroundInputStream extends InputStream {
    
    private BackgroundHTTPThread methodThread = null;
    private InputStream xThreadInputStream = null;
    
    /** Construct an http transaction stream.  The stream is driven by a background
    * thread, whose existence is tied to this class.  The sequence of activity that
    * this class expects is as follows:
    * (1) Construct the httpclient and request object and initialize them
    * (2) Construct a background method thread, and start it
    * (3) If the response calls for it, call this constructor, and put the resulting stream
    *    into the message response
    * (4) Otherwise, terminate the background method thread in the standard manner,
    *    being sure NOT
    */
    public BackgroundInputStream(BackgroundHTTPThread methodThread, InputStream xThreadInputStream)
    {
      this.methodThread = methodThread;
      this.xThreadInputStream = xThreadInputStream;
    }
    
    @Override
    public int available()
      throws IOException
    {
      if (xThreadInputStream != null)
        return xThreadInputStream.available();
      return super.available();
    }
    
    @Override
    public void close()
      throws IOException
    {
      try
      {
        if (xThreadInputStream != null)
        {
          xThreadInputStream.close();
          xThreadInputStream = null;
        }
      }
      finally
      {
        if (methodThread != null)
        {
          methodThread.abort();
          try
          {
            methodThread.finishUp();
          }
          catch (InterruptedException e)
          {
            throw new InterruptedIOException(e.getMessage());
          }
          methodThread = null;
        }
      }
    }
    
    @Override
    public void mark(int readlimit)
    {
      if (xThreadInputStream != null)
        xThreadInputStream.mark(readlimit);
      else
        super.mark(readlimit);
    }
    
    @Override
    public void reset()
      throws IOException
    {
      if (xThreadInputStream != null)
        xThreadInputStream.reset();
      else
        super.reset();
    }
    
    @Override
    public boolean markSupported()
    {
      if (xThreadInputStream != null)
        return xThreadInputStream.markSupported();
      return super.markSupported();
    }
    
    @Override
    public long skip(long n)
      throws IOException
    {
      if (xThreadInputStream != null)
        return xThreadInputStream.skip(n);
      return super.skip(n);
    }
    
    @Override
    public int read(byte[] b, int off, int len)
      throws IOException
    {
      if (xThreadInputStream != null)
        return xThreadInputStream.read(b,off,len);
      return super.read(b,off,len);
    }

    @Override
    public int read(byte[] b)
      throws IOException
    {
      if (xThreadInputStream != null)
        return xThreadInputStream.read(b);
      return super.read(b);
    }
    
    @Override
    public int read()
      throws IOException
    {
      if (xThreadInputStream != null)
        return xThreadInputStream.read();
      return -1;
    }
    
  }

  /** This thread does the actual socket communication with the server.
  * It's set up so that it can be abandoned at shutdown time.
  *
  * The way it works is as follows:
  * - it starts the transaction
  * - it receives the response, and saves that for the calling class to inspect
  * - it transfers the data part to an input stream provided to the calling class
  * - it shuts the connection down
  *
  * If there is an error, the sequence is aborted, and an exception is recorded
  * for the calling class to examine.
  *
  * The calling class basically accepts the sequence above.  It starts the
  * thread, and tries to get a response code.  If instead an exception is seen,
  * the exception is thrown up the stack.
  */
  protected static class BackgroundHTTPThread extends Thread
  {
    /** Client and method, all preconfigured */
    protected final HttpClient httpClient;
    protected final HttpRequestBase executeMethod;
    
    protected HttpResponse response = null;
    protected Throwable responseException = null;
    protected XThreadInputStream threadStream = null;
    protected InputStream bodyStream = null;
    protected Charset charSet = null;
    protected boolean streamCreated = false;
    protected Throwable streamException = null;
    protected boolean abortThread = false;

    protected Throwable shutdownException = null;

    protected Throwable generalException = null;
    
    public BackgroundHTTPThread(HttpClient httpClient, HttpRequestBase executeMethod)
    {
      super();
      setDaemon(true);
      this.httpClient = httpClient;
      this.executeMethod = executeMethod;
    }

    public void run()
    {
      try
      {
        try
        {
          // Call the execute method appropriately
          synchronized (this)
          {
            if (!abortThread)
            {
              try
              {
                response = httpClient.execute(executeMethod);
              }
              catch (java.net.SocketTimeoutException e)
              {
                responseException = e;
              }
              catch (ConnectTimeoutException e)
              {
                responseException = e;
              }
              catch (InterruptedIOException e)
              {
                throw e;
              }
              catch (Throwable e)
              {
                responseException = e;
              }
              this.notifyAll();
            }
          }
          
          // Start the transfer of the content
          if (responseException == null)
          {
            synchronized (this)
            {
              if (!abortThread)
              {
                try
                {
                  HttpEntity entity = response.getEntity();
                  bodyStream = entity.getContent();
                  if (bodyStream != null)
                  {
                    threadStream = new XThreadInputStream(bodyStream);
                    try
                    {
                      ContentType ct = ContentType.get(entity);
                      if (ct == null)
                        charSet = null;
                      else
                        charSet = ct.getCharset();
                    }
                    catch (ParseException e)
                    {
                      charSet = null;
                    }
                  }
                  streamCreated = true;
                }
                catch (java.net.SocketTimeoutException e)
                {
                  streamException = e;
                }
                catch (ConnectTimeoutException e)
                {
                  streamException = e;
                }
                catch (InterruptedIOException e)
                {
                  throw e;
                }
                catch (Throwable e)
                {
                  streamException = e;
                }
                this.notifyAll();
              }
            }
          }
          
          if (responseException == null && streamException == null)
          {
            if (threadStream != null)
            {
              // Stuff the content until we are done
              threadStream.stuffQueue();
            }
          }
          
        }
        finally
        {
          if (bodyStream != null)
          {
            try
            {
              bodyStream.close();
            }
            catch (IOException e)
            {
            }
            bodyStream = null;
          }
          synchronized (this)
          {
            try
            {
              executeMethod.abort();
            }
            catch (Throwable e)
            {
              shutdownException = e;
            }
            this.notifyAll();
          }
        }
      }
      catch (Throwable e)
      {
        // We catch exceptions here that should ONLY be InterruptedExceptions, as a result of the thread being aborted.
        this.generalException = e;
      }
    }

    public int getResponseCode()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait until the response object is there
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
            return response.getStatusLine().getStatusCode();
          wait();
        }
      }
    }

    public String getResponseStatus()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait until the response object is there
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
            return response.getStatusLine().toString();
          wait();
        }
      }
    }

    public Map<String,List<String>> getResponseHeaders()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait for the response object to appear
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
          {
            Header[] headers = response.getAllHeaders();
            Map<String,List<String>> rval = new HashMap<String,List<String>>();
            int i = 0;
            while (i < headers.length)
            {
              Header h = headers[i++];
              String name = h.getName();
              String value = h.getValue();
              List<String> values = rval.get(name);
              if (values == null)
              {
                values = new ArrayList<String>();
                rval.put(name,values);
              }
              values.add(value);
            }
            return rval;
          }
          wait();
        }
      }

    }
    
    public String getFirstHeader(String headerName)
      throws InterruptedException, IOException, HttpException
    {
      // Must wait for the response object to appear
      while (true)
      {
        synchronized (this)
        {
          checkException(responseException);
          if (response != null)
          {
            Header h = response.getFirstHeader(headerName);
            if (h == null)
              return null;
            return h.getValue();
          }
          wait();
        }
      }
    }

    public InputStream getSafeInputStream()
      throws InterruptedException, IOException, HttpException
    {
      // Must wait until stream is created, or until we note an exception was thrown.
      while (true)
      {
        synchronized (this)
        {
          if (responseException != null)
            throw new IllegalStateException("Check for response before getting stream");
          checkException(streamException);
          if (streamCreated)
            return threadStream;
          wait();
        }
      }
    }
    
    public Charset getCharSet()
      throws InterruptedException, IOException, HttpException
    {
      while (true)
      {
        synchronized (this)
        {
          if (responseException != null)
            throw new IllegalStateException("Check for response before getting charset");
          checkException(streamException);
          if (streamCreated)
            return charSet;
          wait();
        }
      }
    }
    
    public void abort()
    {
      // This will be called during the finally
      // block in the case where all is well (and
      // the stream completed) and in the case where
      // there were exceptions.
      synchronized (this)
      {
        if (streamCreated)
        {
          if (threadStream != null)
            threadStream.abort();
        }
        abortThread = true;
      }
    }
    
    public void finishUp()
      throws InterruptedException
    {
      join();
    }
    
    protected synchronized void checkException(Throwable exception)
      throws IOException, HttpException
    {
      if (exception != null)
      {
        Throwable e = exception;
        if (e instanceof IOException)
          throw (IOException)e;
        else if (e instanceof HttpException)
          throw (HttpException)e;
        else if (e instanceof RuntimeException)
          throw (RuntimeException)e;
        else if (e instanceof Error)
          throw (Error)e;
        else
          throw new RuntimeException("Unhandled exception of type: "+e.getClass().getName(),e);
      }
    }

  }

}

