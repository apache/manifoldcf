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
* $Id: CommonsHTTPSender.java 988245 2010-08-23 18:39:35Z kwright $
*/
package org.apache.manifoldcf.crawler.connectors.sharepoint;

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
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.ProtocolVersion;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHeader;

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
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

/* Class to use httpcomponents to communicate with a SOAP server.
* I've replaced the original rather complicated class with a much simpler one that
* relies on having an HttpClient object passed into the invoke() method.  Since
* the object is already set up, not much needs to be done in here.
*/

public class CommonsHTTPSender extends BasicHandler {

  /** Field log           */
  protected static Log log =
    LogFactory.getLog(CommonsHTTPSender.class.getName());

  /** Properties */
  protected CommonsHTTPClientProperties clientProperties;

  public CommonsHTTPSender() {
    this.clientProperties = CommonsHTTPClientPropertiesFactory.create();
  }

  protected static class ExecuteMethodThread extends Thread
  {
    protected final HttpClient httpClient;
    protected final String targetURL;
    protected final MessageContext msgContext;

    protected Throwable exception = null;
    protected int returnCode = 0;

    public ExecuteMethodThread( HttpClient httpClient, String targetURL, MessageContext msgContext )
    {
      super();
      setDaemon(true);
      this.httpClient = httpClient;
      this.targetURL = targetURL;
      this.msgContext = msgContext;
    }

    public void run()
    {
      try
      {
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
          HttpPost postMethod = new HttpPost(targetURL);
          
          // set false as default, addContetInfo can overwrite
          HttpProtocolParams.setUseExpectContinue(postMethod.getParams(),false);

          Message reqMessage = msgContext.getRequestMessage();
          
          boolean httpChunkStream = addContextInfo(postMethod, msgContext);

          HttpEntity requestEntity = null;
          requestEntity = new MessageRequestEntity(reqMessage, httpChunkStream,
            http10 || !httpChunkStream);
          postMethod.setEntity(requestEntity);
          method = postMethod;
        } else {
          method = new HttpGet(targetURL);
        }
        
        if (http10)
          HttpProtocolParams.setVersion(method.getParams(),new ProtocolVersion("HTTP",1,0));

        // Try block to insure that the connection gets cleaned up
        try
        {
          // Begin the fetch
          HttpResponse response = httpClient.execute(method);

          returnCode = response.getStatusLine().getStatusCode();
          
          String contentType =
            getHeader(response, HTTPConstants.HEADER_CONTENT_TYPE);
          String contentLocation =
            getHeader(response, HTTPConstants.HEADER_CONTENT_LOCATION);
          String contentLength =
            getHeader(response, HTTPConstants.HEADER_CONTENT_LENGTH);

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
            String statusMessage = response.getStatusLine().toString();
            AxisFault fault = new AxisFault("HTTP",
              "(" + returnCode + ")"
            + statusMessage, null,
              null);

            fault.setFaultDetailString(
              Messages.getMessage("return01",
              "" + returnCode,
              getResponseBodyAsString(response)));
            fault.addFaultDetail(Constants.QNAME_FAULTDETAIL_HTTPERRORCODE,
              Integer.toString(returnCode));
            throw fault;
          }

          // Transfer to a temporary file.  If we stream it, we may wind up waiting on the socket outside this thread.
          InputStream releaseConnectionOnCloseStream = new FileBackedInputStream(response.getEntity().getContent());

          Header contentEncoding =
            response.getFirstHeader(HTTPConstants.HEADER_CONTENT_ENCODING);
          if (contentEncoding != null) {
            AxisFault fault = new AxisFault("HTTP",
              "unsupported content-encoding of '"
            + contentEncoding.getValue()
            + "' found", null, null);
            throw fault;
          }

          Message outMsg = new Message(releaseConnectionOnCloseStream,
            false, contentType, contentLocation);
          
          // Transfer HTTP headers of HTTP message to MIME headers of SOAP message
          Header[] responseHeaders = response.getAllHeaders();
          MimeHeaders responseMimeHeaders = outMsg.getMimeHeaders();
          for (int i = 0; i < responseHeaders.length; i++) {
            Header responseHeader = responseHeaders[i];
            responseMimeHeaders.addHeader(responseHeader.getName(),
              responseHeader.getValue());
          }
          outMsg.setMessageType(Message.RESPONSE);
          
          // Put the message in the message context.
          msgContext.setResponseMessage(outMsg);
        }
        finally
        {
          // Consumes and closes the stream, releasing the connection
          method.abort();
        }

      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public int getResponse()
    {
      return returnCode;
    }
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
      HttpClient httpClient = (HttpClient)msgContext.getProperty(SPSProxyHelper.HTTPCLIENT_PROPERTY);

      ExecuteMethodThread t = new ExecuteMethodThread(httpClient,targetURL.toString(),msgContext);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else if (thr instanceof Exception)
            throw (Exception)thr;
          else
            throw (Error)thr;
        }
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw e;
      }

      /*
      if (log.isDebugEnabled()) {
        if (null == contentLength) {
          log.debug("\n"
            + Messages.getMessage("no00", "Content-Length"));
          }
          log.debug("\n" + Messages.getMessage("xmlRecd00"));
          log.debug("-----------------------------------------------");
          log.debug(msgContext.getResponseMessage().getSOAPPartAsString());
        }
      }
      */

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

        if (key.equalsIgnoreCase(HTTPConstants.HEADER_EXPECT) &&
          value.equalsIgnoreCase(HTTPConstants.HEADER_EXPECT_100_Continue)) {
          HttpProtocolParams.setUseExpectContinue(method.getParams(),true);
        } else if (key.equalsIgnoreCase(HTTPConstants.HEADER_TRANSFER_ENCODING_CHUNKED)) {
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

  private static String getHeader(HttpResponse response, String headerName) {
    Header header = response.getFirstHeader(headerName);
    return (header == null) ? null : header.getValue().trim();
  }

  private static String getResponseBodyAsString(HttpResponse httpResponse)
    throws IOException {
    HttpEntity entity = httpResponse.getEntity();
    if (entity != null)
    {
      InputStream is = entity.getContent();
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
  
  private static class FileBackedInputStream extends InputStream {
    
    private InputStream fileInputStream = null;
    private File file = null;
    
    public FileBackedInputStream(InputStream is)
      throws IOException
    {
      File readyToOpenFile = null;
      // Create a file and read into it
      File tempFile = File.createTempFile("__shp__",".tmp");
      try
      {
        // Open the output stream
        OutputStream os = new FileOutputStream(tempFile);
        try
        {
          byte[] buffer = new byte[65536];
          while (true)
          {
            int amt = is.read(buffer);
            if (amt == -1)
              break;
            os.write(buffer,0,amt);
          }
        }
        finally
        {
          os.close();
        }
        readyToOpenFile = tempFile;
        tempFile = null;
      }
      finally
      {
        if (tempFile != null)
          tempFile.delete();
      }
      
      try
      {
        fileInputStream = new FileInputStream(file);
        file = readyToOpenFile;
        readyToOpenFile = null;
      }
      finally
      {
        if (readyToOpenFile != null)
          readyToOpenFile.delete();
      }
    }
    
    @Override
    public int available()
      throws IOException
    {
      if (fileInputStream != null)
        return fileInputStream.available();
      return super.available();
    }
    
    @Override
    public void close()
      throws IOException
    {
      IOException exception = null;
      try
      {
        if (fileInputStream != null)
          fileInputStream.close();
      }
      catch (IOException e)
      {
        exception = e;
      }
      fileInputStream = null;
      if (file != null)
        file.delete();
      file = null;
      if (exception != null)
        throw exception;
    }
    
    @Override
    public void mark(int readlimit)
    {
      if (fileInputStream != null)
        fileInputStream.mark(readlimit);
      else
        super.mark(readlimit);
    }
    
    @Override
    public void reset()
      throws IOException
    {
      if (fileInputStream != null)
        fileInputStream.reset();
      else
        super.reset();
    }
    
    @Override
    public boolean markSupported()
    {
      if (fileInputStream != null)
        return fileInputStream.markSupported();
      return super.markSupported();
    }
    
    @Override
    public long skip(long n)
      throws IOException
    {
      if (fileInputStream != null)
        return fileInputStream.skip(n);
      return super.skip(n);
    }
    
    @Override
    public int read(byte[] b, int off, int len)
      throws IOException
    {
      if (fileInputStream != null)
        return fileInputStream.read(b,off,len);
      return super.read(b,off,len);
    }

    @Override
    public int read(byte[] b)
      throws IOException
    {
      if (fileInputStream != null)
        return fileInputStream.read(b);
      return super.read(b);
    }
    
    @Override
    public int read()
      throws IOException
    {
      if (fileInputStream != null)
        return fileInputStream.read();
      return -1;
    }
    
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

}

