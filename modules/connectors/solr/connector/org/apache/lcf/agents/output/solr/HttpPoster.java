/* $Id$ */

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
package org.apache.lcf.agents.output.solr;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.core.common.Base64;
import org.apache.lcf.core.common.XMLDoc;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.agents.system.*;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.*;
import javax.net.ssl.*;

import org.apache.log4j.*;

/**
* Posts an input stream to SOLR
*
* @author James Sablatura, modified by Karl Wright
*/
public class HttpPoster
{
  public static final String _rcsid = "@(#)$Id$";

  // Chunk size for base64-encoded headers
  protected final static int HEADER_CHUNK = 4096;

  private String protocol;
  private String host;
  private int port;
  private String encodedCredentials;
  private String realm;
  private String postUpdateAction;
  private String postRemoveAction;
  private String postStatusAction;
  private String allowAttributeName;
  private String denyAttributeName;

  private static final String LITERAL = "literal.";
  private static final String NOTHING = "__NOTHING__";
    
  private int buffersize = 32768;  // default buffer size
  double sizeCoefficient = 0.0005;    // 20 ms additional timeout per 2000 bytes, pulled out of my butt
  /** the number of times we should poll for the response */
  int responseRetries = 9000;         // Long basic wait: 3 minutes.  This will also be added to by a term based on the size of the request.
  /** how long we should wait before checking for a new stream */
  long responseRetryWait = 20L;
  /** How long to wait before retrying a failed ingestion */
  long interruptionRetryTime = 60000L;

  /** The multipart separator we're going to use.  I was thinking of including a random number, but that would wreck repeatability */
  protected static byte[] separatorBytes = null;
  protected static byte[] endBytes = null;
  protected static byte[] postambleBytes = null;
  protected static byte[] preambleBytes = null;
  static
  {
    try
    {
      String separatorString = "------------------T-H-I-S--I-S--A--S-E-P-A-R-A-T-O-R--399123410141511";
      separatorBytes = (separatorString+"\r\n").getBytes("ASCII");
      endBytes = ("--"+separatorString+"--\r\n").getBytes("ASCII");
      postambleBytes = "\r\n".getBytes("ASCII");
      preambleBytes = "--".getBytes("ASCII");
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }

  /** This is the secure socket factory we will use.  I'm presuming it's thread-safe, but
  * if not, synchronization blocks are in order when it's used. */
  protected static javax.net.ssl.SSLSocketFactory secureSocketFactory = null;
  static
  {
    try
    {
      secureSocketFactory = getSecureSocketFactory();
    }
    catch (LCFException e)
    {
      // If we can't create, print and fail
      e.printStackTrace();
      System.exit(100);
    }
  }

  /**
  * Initialized the http poster.
  * @param userID is the unencoded user name, or null.
  * @param password is the unencoded password, or null.
  */
  public HttpPoster(String protocol, String server, int port, String webappName, String updatePath, String removePath, String statusPath,
    String realm, String userID, String password, String allowAttributeName, String denyAttributeName)
    throws LCFException
  {
    this.allowAttributeName = allowAttributeName;
    this.denyAttributeName = denyAttributeName;
      
    this.host = server;
    this.port = port;
    this.protocol = protocol;

    if (userID != null && userID.length() > 0 && password != null)
    {
      try
      {
        encodedCredentials = new org.apache.lcf.core.common.Base64().encodeByteArray((userID+":"+password).getBytes("UTF-8"));
      }
      catch (java.io.UnsupportedEncodingException e)
      {
        throw new LCFException("Couldn't convert to utf-8 bytes: "+e.getMessage(),e);
      }
      this.realm = realm;
    }
    else
      encodedCredentials = null;

    String postURI = protocol + "://" + server + ":" + Integer.toString(port);

    if (webappName.length() > 0)
      webappName = "/" + webappName;
    postUpdateAction = webappName + updatePath;
    postRemoveAction = webappName + removePath;
    postStatusAction = webappName + statusPath;

    String x = LCF.getProperty(LCF.ingestBufferSizeProperty);
    if (x != null && x.length() > 0)
      buffersize = new Integer(x).intValue();
    x = LCF.getProperty(LCF.ingestResponseRetryCount);
    if (x != null && x.length() > 0)
      responseRetries = new Integer(x).intValue();
    x = LCF.getProperty(LCF.ingestResponseRetryInterval);
    if (x != null && x.length() > 0)
      responseRetryWait = new Long(x).longValue();
    x = LCF.getProperty(LCF.ingestRescheduleInterval);
    if (x != null && x.length() > 0)
      interruptionRetryTime = new Long(x).longValue();
  }

  
  /**
  * Post the input stream to ingest
  * @param documentURI is the document's uri.
  * @param document is the document structure to ingest.
  * @param arguments are the configuration arguments to pass in the post.  Key is argument name, value is a list of the argument values.
  * @param authorityNameString is the name of the governing authority for this document's acls, or null if none.
  * @param activities is the activities object, so we can report what's happening.
  * @return true if the ingestion was successful, or false if the ingestion is illegal.
  * @throws LCFException, ServiceInterruption
  */
  public boolean indexPost(String documentURI,
    RepositoryDocument document, Map arguments,
    String authorityNameString, IOutputAddActivity activities)
    throws LCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("indexPost(): '" + documentURI + "'");

    // Convert the incoming acls to qualified forms
    String[] shareAcls = convertACL(document.getShareACL(),authorityNameString,activities);
    String[] shareDenyAcls = convertACL(document.getShareDenyACL(),authorityNameString,activities);
    String[] acls = convertACL(document.getACL(),authorityNameString,activities);
    String[] denyAcls = convertACL(document.getDenyACL(),authorityNameString,activities);
    
    // This flag keeps track of whether we read anything from the input stream yet.
    // If not, we can retry here.  If so, we have to reschedule.
    boolean readFromDocumentStreamYet = false;
    int ioErrorRetry = 3;

    while (true)
    {
      try
      {
        IngestThread t = new IngestThread(documentURI,document,arguments,shareAcls,shareDenyAcls,acls,denyAcls);
        try
        {
          t.start();
          t.join();

          // Log the activity, if any, regardless of any exception
          if (t.getActivityCode() != null)
            activities.recordActivity(t.getActivityStart(),SolrConnector.INGEST_ACTIVITY,t.getActivityBytes(),documentURI,t.getActivityCode(),t.getActivityDetails());

          readFromDocumentStreamYet = (readFromDocumentStreamYet || t.getReadFromDocumentStreamYet());

          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof ServiceInterruption)
              throw (ServiceInterruption)thr;
            if (thr instanceof LCFException)
              throw (LCFException)thr;
            if (thr instanceof IOException)
              throw (IOException)thr;
            if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          return t.getRval();
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new LCFException("Interrupted: "+e.getMessage(),LCFException.INTERRUPTED);
        }
      }
      catch (java.net.SocketTimeoutException ioe)
      {
        if (readFromDocumentStreamYet || ioErrorRetry == 0)
        {
          // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("IO error connecting to ingestion API: "+ioe.getMessage()+"; ingestion will be retried again later",
            ioe,
            currentTime + interruptionRetryTime,
            currentTime + 2L * 60L * 60000L,
            -1,
            true);
        }
      }
      catch (IOException ioe)
      {
        if (readFromDocumentStreamYet || ioErrorRetry == 0)
        {
          // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("IO error ingesting document: "+ioe.getMessage()+"; ingestion will be retried again later",
            ioe,
            currentTime + interruptionRetryTime,
            currentTime + 2L * 60L * 60000L,
            -1,
            true);
        }
      }

      // Sleep for a time, and retry
      try
      {
        LCF.sleep(10000L);
      }
      catch (InterruptedException e)
      {
        throw new LCFException("Interrupted: "+e.getMessage(),LCFException.INTERRUPTED);
      }
      ioErrorRetry--;

      // Go back around again!

    }

  }

  /** Post a check request.
  */
  public void checkPost()
    throws LCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("checkPost()");

    int ioErrorRetry = 5;
    while (true)
    {
      // Open a socket to ingest, and to the response stream to get the post result
      try
      {
        StatusThread t = new StatusThread();
        try
        {
          t.start();
          t.join();

          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof ServiceInterruption)
              throw (ServiceInterruption)thr;
            if (thr instanceof LCFException)
              throw (LCFException)thr;
            if (thr instanceof IOException)
              throw (IOException)thr;
            if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          return;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new LCFException("Interrupted: "+e.getMessage(),LCFException.INTERRUPTED);
        }
      }
      catch (IOException ioe)
      {
        if (ioErrorRetry == 0)
        {
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("IO exception checking: "+ioe.getMessage(),
            ioe,
            currentTime + interruptionRetryTime,
            currentTime + 2L * 60L * 60000L,
            -1,
            true);
        }
      }

      // Go back around again!
      // Sleep for a time, and retry
      try
      {
        LCF.sleep(10000L);
      }
      catch (InterruptedException e)
      {
        throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }
      ioErrorRetry--;

    }

  }

  /** Post a delete request.
  *@param documentURI is the document's URI.
  */
  public void deletePost(String documentURI, IOutputRemoveActivity activities)
    throws LCFException, ServiceInterruption
  {
    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("deletePost(): '" + documentURI + "'");

    int ioErrorRetry = 5;
    while (true)
    {
      try
      {
        DeleteThread t = new DeleteThread(documentURI);
        try
        {
          t.start();
          t.join();

          // Log the activity, if any, regardless of any exception
          if (t.getActivityCode() != null)
            activities.recordActivity(t.getActivityStart(),SolrConnector.REMOVE_ACTIVITY,null,documentURI,t.getActivityCode(),t.getActivityDetails());

          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof ServiceInterruption)
              throw (ServiceInterruption)thr;
            if (thr instanceof LCFException)
              throw (LCFException)thr;
            if (thr instanceof IOException)
              throw (IOException)thr;
            if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          return;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new LCFException("Interrupted: "+e.getMessage(),LCFException.INTERRUPTED);
        }
      }
      catch (IOException ioe)
      {
        if (ioErrorRetry == 0)
        {
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("IO exception deleting: "+ioe.getMessage()+"; deletion will be retried again later",
            ioe,
            currentTime + interruptionRetryTime,
            currentTime + 2L * 60L * 60000L,
            -1,
            true);
        }
        // Fall through and recycle
      }

      // Go back around again!
      // Sleep for a time, and retry
      try
      {
        LCF.sleep(10000L);
      }
      catch (InterruptedException e)
      {
        throw new LCFException("Interrupted",LCFException.INTERRUPTED);
      }

      ioErrorRetry--;

    }

  }

  /** Convert an unqualified ACL to qualified form.
  * @param acl is the initial, unqualified ACL.
  * @param authorityNameString is the name of the governing authority for this document's acls, or null if none.
  * @param activities is the activities object, so we can report what's happening.
  * @return the modified ACL.
  */
  protected static String[] convertACL(String[] acl, String authorityNameString, IOutputAddActivity activities)
    throws LCFException
  {
    if (acl != null)
    {
      String[] rval = new String[acl.length];
      int i = 0;
      while (i < rval.length)
      {
        rval[i] = activities.qualifyAccessToken(authorityNameString,acl[i]);
        i++;
      }
      return rval;
    }
    return new String[0];
  }

  /**
  * Read an ascii line from an input stream
  */
  protected static String readLine(InputStream in)
    throws IOException
  {
    ByteBuffer bb = new ByteBuffer();
    while (true)
    {
      int x = in.read();
      if (x == -1)
        throw new IOException("Unexpected EOF");
      if (x == 13)
        continue;
      if (x == 10)
        break;

      bb.append((byte)x);
    }
    return bb.toString("ASCII");
  }

  /**
  * Get the response code of the post
  * @param in the stream the response is going to come from
  * @return the response details.
  * @throws LCFException
  */
  protected CodeDetails getResponse(InputStream in) throws LCFException, ServiceInterruption
  {
    Logging.ingest.debug("Waiting for response stream");

    try
    {
      // Stream.ready() always returns false for secure sockets :-(.  So
      // we have to rely on socket timeouts to interrupt us if the server goes down.
      String responseCode = readLine(in);

      if (Logging.ingest.isDebugEnabled())
        Logging.ingest.debug("Response code from ingest: '" + responseCode + "'");

      // Read the response headers
      String contentType = "text/plain; charset=iso-8859-1";
      while (true)
      {
        String headerLine = readLine(in);
        if (headerLine.length() == 0)
          break;
        // Look for the headers we care about, ignore the rest...
        int spaceIndex = headerLine.indexOf(" ");
        if (spaceIndex != -1)
        {
          String headerName = headerLine.substring(0,spaceIndex);
          String headerValue = headerLine.substring(spaceIndex).trim().toLowerCase();
          if (headerName.toLowerCase().equals("content-type:"))
          {
            contentType = headerValue;
          }
        }
      }

      // Now read the response data.  It's safe to assemble the data in memory.
      int charsetIndex = contentType.indexOf("charset=");
      String charsetName = "iso-8859-1";
      if (charsetIndex != -1)
        charsetName = contentType.substring(charsetIndex+8);

      // Now that we calculated the character set, we're not actually going to use it, since we're looking for XML and xerces would prefer binary.  So, instead, we're going to pass the stream off to xerces.
      XMLDoc doc = null;
      try
      {
        doc = new XMLDoc(in);
      }
      catch (LCFException e)
      {
        // Syntax errors should be eaten; we'll just return a null doc in that case.
        e.printStackTrace();
      }

      Logging.ingest.debug("Read of response stream complete");
      return new CodeDetails(responseCode,doc);
    }
    catch (java.net.SocketTimeoutException e)
    {
      // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Ingestion API socket timeout exception waiting for response code: "+e.getMessage()+"; ingestion will be retried again later",
        e,
        currentTime + interruptionRetryTime,
        currentTime + 2L * 60L * 60000L,
        -1,
        true);
    }
    catch (InterruptedIOException e)
    {
      throw new LCFException("Interrupted",LCFException.INTERRUPTED);
    }
    catch (java.net.ConnectException e)
    {
      // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Timed out connecting to ingestion API: "+e.getMessage()+"; ingestion will be retried again later",
        e,
        currentTime + interruptionRetryTime,
        currentTime + 2L * 60L * 60000L,
        -1,
        true);
    }
    catch (java.net.SocketException e)
    {
      // Return 400 error; likely a connection reset which lost us the response data, so
      // just treat it as something OK.
      return new CodeDetails("HTTP/1.0 400 Connection Reset",null);

    }
    catch (IOException ioe)
    {
      Logging.ingest.warn("IO exception trying to get response from ingestion API: "+ioe.getMessage(),ioe);
      // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("IO exception waiting for response code: "+ioe.getMessage()+"; ingestion will be retried again later",
        ioe,
        currentTime + interruptionRetryTime,
        currentTime + 2L * 60L * 60000L,
        -1,
        true);
    }
  }

  /** Write credentials to output */
  protected void writeCredentials(OutputStream out)
    throws IOException
  {
    // Apply credentials if present
    if (encodedCredentials != null)
    {
      Logging.ingest.debug("Applying credentials");
      byte[] tmp = ("Authorization: Basic " + encodedCredentials + "\r\n").getBytes("UTF-8");
      out.write(tmp, 0, tmp.length);

      tmp = ("WWW-Authenticate: Basic realm=\"" + ((realm != null) ? realm : "") + "\"\r\n").getBytes("UTF-8");
      out.write(tmp, 0, tmp.length);
    }
  }

  /** Build a secure socket factory based on no keystore and a lax trust manager.
  * This allows use of SSL for privacy but not identification. */
  protected static javax.net.ssl.SSLSocketFactory getSecureSocketFactory()
    throws LCFException
  {
    try
    {
      java.security.SecureRandom secureRandom = java.security.SecureRandom.getInstance("SHA1PRNG");

      // Create an SSL context
      javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
      sslContext.init(null,new LaxTrustManager[]{new LaxTrustManager()},secureRandom);
      return sslContext.getSocketFactory();
    }
    catch (java.security.NoSuchAlgorithmException e)
    {
      throw new LCFException("No such algorithm: "+e.getMessage(),e);
    }
    catch (java.security.KeyManagementException e)
    {
      throw new LCFException("Key management exception: "+e.getMessage(),e);
    }
  }

  /** Create a socket in a manner consistent with all of our specified parameters.
  */
  protected Socket createSocket(long responseRetryCount)
    throws IOException, LCFException
  {
    Socket socket;
    if (protocol.equals("https"))
    {
      try
      {
        SocketFactory factory = SSLSocketFactory.getDefault();
        socket = factory.createSocket(host,port);
      }
      catch (InterruptedIOException e)
      {
        throw e;
      }
      catch (IOException e)
      {
        throw new LCFException("Couldn't set up SSL connection to ingestion API: "+e.getMessage(),e);
      }
    }
    else
      socket = new Socket(host, port);

    // Calculate the timeout we want
    long timeoutMilliseconds = responseRetryWait * responseRetryCount;
    socket.setSoTimeout((int)timeoutMilliseconds);

    return socket;
  }

  /** Byte buffer class */
  protected static class ByteBuffer
  {
    byte[] theBuffer;
    int bufferAmt;

    public ByteBuffer()
    {
      createBuffer(64);
    }

    protected void createBuffer(int size)
    {
      theBuffer = new byte[size];
    }

    public void append(byte b)
    {
      if (bufferAmt == theBuffer.length)
      {
        byte[] oldBuffer = theBuffer;
        createBuffer(bufferAmt * 2);
        int i = 0;
        while (i < bufferAmt)
        {
          theBuffer[i] = oldBuffer[i];
          i++;
        }
      }
      theBuffer[bufferAmt++] = b;
    }

    public String toString(String encoding)
      throws java.io.UnsupportedEncodingException
    {
      return new String(theBuffer,0,bufferAmt,encoding);
    }

  }

  /** Our own trust manager, which ignores certificate issues */
  protected static class LaxTrustManager implements X509TrustManager
  {
    /** Does nothing */
    public LaxTrustManager()
    {
    }

    /** Return a list of accepted issuers.  There are none. */
    public java.security.cert.X509Certificate[] getAcceptedIssuers()
    {
      return new java.security.cert.X509Certificate[0];
    }

    /** We have no problem with any clients */
    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType)
      throws java.security.cert.CertificateException
    {
    }

    /** We have no problem with any servers */
    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType)
      throws java.security.cert.CertificateException
    {
    }

  }

  /** Calculate the length of the preamble */
  protected static int lengthPreamble()
    throws IOException
  {
    return preambleBytes.length;
  }

  /** Calculate the length of a boundary */
  protected static int lengthBoundary(String contentType, String name, String fileName)
    throws IOException
  {
    int rval = 0;
    rval += separatorBytes.length;
    String value = "Content-Disposition: form-data";
    if (name != null)
      value += "; name=\""+name+"\"";
    if (fileName != null)
      value += "; filename=\""+fileName+"\"";
    value += "\r\n";
    byte[] tmp = value.getBytes("ASCII");
    rval += tmp.length;
    tmp = ("Content-Type: "+contentType+"\r\n\r\n").getBytes("ASCII");
    rval += tmp.length;
    return rval;
  }

  /** Calculate the length of the postamble */
  protected static int lengthPostamble()
    throws IOException
  {
    return postambleBytes.length;
  }

  /** Calculate the length of a field */
  protected static int lengthField(String fieldName, String fieldValue)
    throws IOException
  {
    int rval = lengthPreamble() + lengthBoundary("text/plain; charset=UTF-8",fieldName,null);
    byte[] tmp = fieldValue.getBytes("UTF-8");
    rval += tmp.length;
    rval += lengthPostamble();
    return rval;
  }

  /** Count the size of an acl level */
  protected int lengthACLs(String aclType, String[] acl, String[] denyAcl)
    throws IOException
  {
    int totalLength = 0;
    String metadataACLName = LITERAL + allowAttributeName + aclType;
    int i = 0;
    while (i < acl.length)
    {
      totalLength += lengthField(metadataACLName,acl[i++]);
    }
    String metadataDenyACLName = LITERAL + denyAttributeName + aclType;
    i = 0;
    while (i < denyAcl.length)
    {
      totalLength += lengthField(metadataDenyACLName,denyAcl[i++]);
    }
    return totalLength;
  }

  /** Write the preamble */
  protected static void writePreamble(OutputStream out)
    throws IOException
  {
    out.write(preambleBytes, 0, preambleBytes.length);
  }

  /** Write a boundary */
  protected static void writeBoundary(OutputStream out, String contentType, String name, String fileName)
    throws IOException
  {
    out.write(separatorBytes, 0, separatorBytes.length);
    String value = "Content-Disposition: form-data";
    if (name != null)
      value += "; name=\""+name+"\"";
    if (fileName != null)
      value += "; filename=\""+fileName+"\"";
    value += "\r\n";
    byte[] tmp = value.getBytes("ASCII");
    out.write(tmp, 0, tmp.length);
    tmp = ("Content-Type: "+contentType+"\r\n\r\n").getBytes("ASCII");
    out.write(tmp, 0, tmp.length);
  }

  /** Write the postamble */
  protected static void writePostamble(OutputStream out)
    throws IOException
  {
    out.write(postambleBytes, 0, postambleBytes.length);
  }

  /** Write a field */
  protected static void writeField(OutputStream out, String fieldName, String fieldValue)
    throws IOException
  {
    writePreamble(out);
    writeBoundary(out,"text/plain; charset=UTF-8",fieldName,null);
    byte[] tmp = fieldValue.getBytes("UTF-8");
    out.write(tmp, 0, tmp.length);
    writePostamble(out);
  }

  
  /** Output an acl level */
  protected void writeACLs(OutputStream out, String aclType, String[] acl, String[] denyAcl)
    throws IOException
  {
    String metadataACLName = LITERAL + allowAttributeName + aclType;
    int i = 0;
    while (i < acl.length)
    {
      writeField(out,metadataACLName,acl[i++]);
    }
    String metadataDenyACLName = LITERAL + denyAttributeName + aclType;
    i = 0;
    while (i < denyAcl.length)
    {
      writeField(out,metadataDenyACLName,denyAcl[i++]);
    }
  }
  
  /** XML encoding */
  protected static String xmlEncode(String input)
  {
    StringBuffer sb = new StringBuffer("<![CDATA[");
    sb.append(input);
    sb.append("]]>");
    return sb.toString();
  }
  
  /** Killable thread that does ingestions.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a single document ingestion.
  */
  protected class IngestThread extends java.lang.Thread
  {
    protected String documentURI;
    protected RepositoryDocument document;
    protected Map arguments;
    protected String[] shareAcls;
    protected String[] shareDenyAcls;
    protected String[] acls;
    protected String[] denyAcls;

    protected Long activityStart = null;
    protected Long activityBytes = null;
    protected String activityCode = null;
    protected String activityDetails = null;
    protected Throwable exception = null;
    protected boolean readFromDocumentStreamYet = false;
    protected boolean rval = false;

    public IngestThread(String documentURI, RepositoryDocument document, Map arguments, String[] shareAcls, String[] shareDenyAcls, String[] acls, String[] denyAcls)
    {
      super();
      setDaemon(true);
      this.documentURI = documentURI;
      this.document = document;
      this.arguments = arguments;
      this.shareAcls = shareAcls;
      this.shareDenyAcls = shareDenyAcls;
      this.acls = acls;
      this.denyAcls = denyAcls;
    }

    public void run()
    {
      long length = document.getBinaryLength();
      InputStream is = document.getBinaryStream();

      try
      {
        // Do the operation!
        long fullStartTime = System.currentTimeMillis();

        // Open a socket to ingest, and to the response stream to get the post result
        try
        {
          // Set up the socket, and the (optional) secure socket.
          long responseRetryCount = responseRetries + (long)((float)length * sizeCoefficient);
          Socket socket = createSocket(responseRetryCount);
          try
          {
            InputStream in = socket.getInputStream();
            try
            {
              OutputStream out = socket.getOutputStream();
              try
              {
                // Create the output stream to SOLR
                byte[] tmp = ("POST " + postUpdateAction + " HTTP/1.0\r\n").getBytes("ASCII");
                out.write(tmp, 0, tmp.length);

                // Set all the headers
                writeCredentials(out);

                // Headers must include the following:
                // Content-Type
                // Content-Length
                // The content-length is calculated using the entire body length, which therefore includes the length of all the metadata fields as well.

                // Come up with a boundary.  Ideally, the boundary should be something that doesn't exist in any of the data.  In practice, that would mean
                // scanning all such data at least twice: once to make sure we avoided all boundary collisions, and a second time to actually output the data.
                // This is such a huge chunk of overhead, I've decided for now to just punt and pick something that's pretty unlikely.


                // Calculate the content length.  To do this, we have to walk through the entire multipart assembly process, but calculate the length rather than output
                // anything.

                int totalLength = 0;
                // Count the id.
                totalLength += lengthField("literal.id",documentURI);
                // Count the acls
                totalLength += lengthACLs("share",shareAcls,shareDenyAcls);
                totalLength += lengthACLs("document",acls,denyAcls);
                // Count the arguments
                Iterator iter = arguments.keySet().iterator();
                while (iter.hasNext())
                {
                  String name = (String)iter.next();
                  List values = (List)arguments.get(name);
                  int j = 0;
                  while (j < values.size())
                  {
                    String value = (String)values.get(j++);
                    totalLength += lengthField(name,value);
                  }
                }
                // Count the metadata.
                iter = document.getFields();
                while (iter.hasNext())
                {
                  String fieldName = (String)iter.next();
                  Object[] values = document.getField(fieldName);
                  // We only handle strings right now!!!
                  int k = 0;
                  while (k < values.length)
                  {
                    String value = (String)values[k++];
                    totalLength += lengthField(fieldName,value);
                  }
                }
                // Count the binary data
                totalLength += lengthPreamble();
                totalLength += lengthBoundary("application/octet-stream","myfile","docname");
                totalLength += length;
                // Count the postamble
                totalLength += lengthPostamble();
                // Count the end marker.
                totalLength += endBytes.length;

                // Now, output the content-length header, and another newline, to start the data.
                tmp = ("Content-Length: "+Integer.toString(totalLength)+"\r\n").getBytes("ASCII");
                out.write(tmp, 0, tmp.length);

                tmp = ("Content-Type: multipart/form-data; boundary=").getBytes("ASCII");
                out.write(tmp, 0, tmp.length);
                out.write(separatorBytes, 0, separatorBytes.length);

                // End of headers.
                tmp = "\r\n".getBytes("ASCII");
                out.write(tmp, 0, tmp.length);

                // Write the id field
                writeField(out,"literal.id",documentURI);

		// Write the access token information
                writeACLs(out,"share",shareAcls,shareDenyAcls);
                writeACLs(out,"document",acls,denyAcls);

                // Write the arguments
                iter = arguments.keySet().iterator();
                while (iter.hasNext())
                {
                  String name = (String)iter.next();
                  List values = (List)arguments.get(name);
                  int j = 0;
                  while (j < values.size())
                  {
                    String value = (String)values.get(j++);
                    writeField(out,name,value);
                  }
                }

                // Write the metadata, each in a field by itself
                iter = document.getFields();
                while (iter.hasNext())
                {
                  String fieldName = (String)iter.next();
                  Object[] values = document.getField(fieldName);
                  // We only handle strings right now!!!
                  int k = 0;
                  while (k < values.length)
                  {
                    String value = (String)values[k++];
                    writeField(out,fieldName,value);
                  }
                }

                // Write the content
                writePreamble(out);

                writeBoundary(out,"application/octet-stream","myfile","docname");

                // Stream the data
                long total = 0;
                long now, later;
                now = System.currentTimeMillis();

                byte[] bytes = new byte[buffersize];

                // Write out the contents of the inputstream to the socket
                while (true)
                {
                  int count;
                  // Specially catch all errors that come from reading the input stream itself.
                  // This will help us segregate errors that come from the stream vs. those that come from the ingestion system.
                  try
                  {
                    count = is.read(bytes);
                  }
                  catch (java.net.SocketTimeoutException ioe)
                  {
                    // We have to catch socket timeout exceptions specially, because they are derived from InterruptedIOException
                    // They are otherwise just like IOExceptions

                    // Log the error
                    Logging.ingest.warn("Error reading data for transmission to Ingestion API: "+ioe.getMessage(),ioe);

                    activityStart = new Long(fullStartTime);
                    activityCode = "-1";
                    activityDetails = "Couldn't read document: "+ioe.getMessage();

                    // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
                    long currentTime = System.currentTimeMillis();
                    throw new ServiceInterruption("IO error reading document for ingestion: "+ioe.getMessage()+"; read will be retried again later",
                      ioe,
                      currentTime + interruptionRetryTime,
                      currentTime + 2L * 60L * 60000L,
                      -1,
                      true);

                  }
                  catch (InterruptedIOException ioe)
                  {
                    // If the transfer was interrupted, it may be because we are shutting down the thread.

                    // Third-party library exceptions derived from InterruptedIOException are possible; if the stream comes from httpclient especially.
                    // If we see one of these, we treat it as "not an interruption".
                    if (!ioe.getClass().getName().equals("java.io.InterruptedIOException"))
                    {
                      // Log the error
                      Logging.ingest.warn("Error reading data for transmission to Ingestion API: "+ioe.getMessage(),ioe);

                      activityStart = new Long(fullStartTime);
                      activityCode = "-1";
                      activityDetails = "Couldn't read document: "+ioe.getMessage();

                      // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
                      long currentTime = System.currentTimeMillis();
                      throw new ServiceInterruption("IO error reading document for ingestion: "+ioe.getMessage()+"; read will be retried again later",
                        ioe,
                        currentTime + interruptionRetryTime,
                        currentTime + 2L * 60L * 60000L,
                        -1,
                        true);
                    }
                    else
                      throw ioe;
                  }
                  catch (IOException ioe)
                  {
                    // We need to decide whether to throw a service interruption or lcf exception, based on what went wrong.
                    // We never retry here; the cause is the repository, so there's not any point.

                    // Log the error
                    Logging.ingest.warn("Error reading data for transmission to Ingestion API: "+ioe.getMessage(),ioe);

                    activityStart = new Long(fullStartTime);
                    activityCode = "-1";
                    activityDetails = "Couldn't read document: "+ioe.getMessage();

                    // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
                    long currentTime = System.currentTimeMillis();
                    throw new ServiceInterruption("IO error reading document for ingestion: "+ioe.getMessage()+"; read will be retried again later",
                      ioe,
                      currentTime + interruptionRetryTime,
                      currentTime + 2L * 60L * 60000L,
                      -1,
                      true);
                  }

                  if (count == -1)
                    break;
                  readFromDocumentStreamYet = true;
                  out.write(bytes,0,count);
                  total += (long)count;
                }

                // Write the postamble
                writePostamble(out);

                // Write the end marker
                out.write(endBytes, 0, endBytes.length);

                out.flush();

                later = System.currentTimeMillis();
                if (Logging.ingest.isDebugEnabled())
                  Logging.ingest.debug("Total bytes posted: " + new Long(total).toString() + ", total time: " + (later - now));

                // Now, process response
                CodeDetails cd;
                try
                {
                  cd = getResponse(in);
                }
                catch (ServiceInterruption si)
                {
                  activityStart = new Long(now);
                  activityCode = "-2";
                  activityDetails = si.getMessage();
                  throw si;
                }


                activityStart = new Long(now);
                activityBytes = new Long(length);
                activityCode = cd.getCode();
                activityDetails = cd.getDetails();

                int codeValue = cd.getCodeValue();

                // A negative number means http error of some kind.
                if (codeValue < 0)
                  throw new LCFException("Http protocol error");

                // 200 means we got a status document back
                if (codeValue == 200)
                {
                  // Look at response XML
                  cd.parseIngestionResponse();
                  rval = true;
                  return;
                }

                // Anything else means the document didn't ingest.
                // There are three possibilities here:
                // 1) The document will NEVER ingest (it's illegal), in which case a 400 or 403 will be returned, and
                // 2) There is a transient error, in which case we will want to try again, after a wait.
                //    If the situation is (2), then we CAN'T retry if we already read any of the stream; therefore
                //    we are forced to throw a "service interrupted" exception, and let the caller reschedule
                //    the ingestion.
                // 3) Something is wrong with the setup, e.g. bad credentials.  In this case we chuck a LCFException,
                //    since this will abort the current activity entirely.

                if (codeValue == 401)
                  throw new LCFException("Bad credentials for ingestion",LCFException.SETUP_ERROR);

                if (codeValue >= 400 && codeValue < 500)
                {
                  rval = false;
                  return;
                }

                // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
                long currentTime = System.currentTimeMillis();
                throw new ServiceInterruption("Error "+Integer.toString(codeValue)+" from ingestion request; ingestion will be retried again later",
                  new LCFException("Ingestion HTTP error code "+Integer.toString(codeValue)),
                  currentTime + interruptionRetryTime,
                  currentTime + 2L * 60L * 60000L,
                  -1,
                  true);
              }
              finally
              {
                out.close();
              }
            }
            finally
            {
              in.close();
            }
          }
          finally
          {
            try
            {
              socket.close();
            }
            catch (InterruptedIOException e)
            {
              throw e;
            }
            catch (IOException e)
            {
              Logging.ingest.debug("Error closing socket: "+e.getMessage(),e);
              // Do NOT rethrow
            }
          }
        }
        catch (UnsupportedEncodingException ioe)
        {
          throw new LCFException("Fatal ingestion error: "+ioe.getMessage(),ioe);
        }
        catch (java.net.SocketTimeoutException ioe)
        {
          // These are just like IO errors, but since they are derived from InterruptedIOException, they have to be caught first.
          // Log the error
          Logging.ingest.warn("Error connecting to ingestion API: "+ioe.getMessage(),ioe);

          activityStart = new Long(fullStartTime);
          activityCode = "-1";
          activityDetails = ioe.getMessage();

          throw ioe;
        }
        catch (InterruptedIOException e)
        {
          return;
        }
        catch (IOException ioe)
        {
          activityStart = new Long(fullStartTime);

          // Intercept "broken pipe" exception, since that seems to be what we get if the ingestion API kills the socket right after a 400 goes out.
          // Basically, we have no choice but to interpret that in the same manner as a 400, since no matter how we do it, it's a race and the 'broken pipe'
          // result is always possible.  So we might as well expect it and treat it properly.
          if (ioe.getClass().getName().equals("java.net.SocketException") && ioe.getMessage().toLowerCase().indexOf("broken pipe") != -1)
          {
            // We've seen what looks like the ingestion interface forcibly closing the socket.
            // We *choose* to interpret this just like a 400 response.  However, we log in the history using a different code,
            // since we really don't know what happened for sure.
            // Record the attempt

            activityCode = "-103";
            activityDetails = "Presuming an ingestion rejection: "+ioe.getMessage();
            rval = false;
            return;
          }

          // Record the attempt
          activityCode = "-1";
          activityDetails = ioe.getMessage();

          // Log the error
          Logging.ingest.warn("Error communicating with Ingestion API: "+ioe.getMessage(),ioe);

          throw ioe;
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

    public Long getActivityStart()
    {
      return activityStart;
    }

    public Long getActivityBytes()
    {
      return activityBytes;
    }

    public String getActivityCode()
    {
      return activityCode;
    }

    public String getActivityDetails()
    {
      return activityDetails;
    }

    public boolean getReadFromDocumentStreamYet()
    {
      return readFromDocumentStreamYet;
    }

    public boolean getRval()
    {
      return rval;
    }
  }

  /** Killable thread that does deletions.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a single document deletion.
  */
  protected class DeleteThread extends java.lang.Thread
  {
    protected String documentURI;

    protected Long activityStart = null;
    protected String activityCode = null;
    protected String activityDetails = null;
    protected Throwable exception = null;

    public DeleteThread(String documentURI)
    {
      super();
      setDaemon(true);
      this.documentURI = documentURI;
    }

    public void run()
    {
      try
      {
        // Do the operation!
        long fullStartTime = System.currentTimeMillis();
        // Open a socket to ingest, and to the response stream to get the post result
        try
        {
          // Set up the socket, and the (optional) secure socket.
          Socket socket = createSocket(responseRetries);
          try
          {
            InputStream in = socket.getInputStream();
            try
            {
              OutputStream out = socket.getOutputStream();
              try
              {
                byte[] requestBytes = ("<delete><id>"+xmlEncode(documentURI)+"</id></delete>").getBytes("UTF-8");
                long startTime = System.currentTimeMillis();
                byte[] tmp = ("POST " + postRemoveAction + " HTTP/1.0\r\n").getBytes("ASCII");
                out.write(tmp, 0, tmp.length);

                // Set all the headers
                writeCredentials(out);
                tmp = ("Content-Length: "+Integer.toString(requestBytes.length)+"\r\n").getBytes("ASCII");
                out.write(tmp, 0, tmp.length);
                tmp = ("Content-Type: text/xml; charset=UTF-8\r\n\r\n").getBytes("ASCII");
                out.write(tmp, 0, tmp.length);

                out.write(requestBytes);

                out.flush();

                if (Logging.ingest.isDebugEnabled())
                  Logging.ingest.debug("Delete posted");

                CodeDetails cd;
                try
                {
                  cd = getResponse(in);
                }
                catch (ServiceInterruption si)
                {
                  activityStart = new Long(startTime);
                  activityCode = "-2";
                  activityDetails = si.getMessage();
                  throw si;
                }

                activityStart = new Long(startTime);
                activityCode = cd.getCode();
                activityDetails = cd.getDetails();

                int codeValue = cd.getCodeValue();

                if (codeValue < 0)
                  throw new LCFException("Http protocol error");

                // 200 means we got an xml document back
                if (codeValue == 200)
                {
                  // Look at response XML
                  cd.parseRemovalResponse();
                  return;
                }

                // We ignore everything in the range from 400-500 now
                if (codeValue == 401)
                  throw new LCFException("Bad credentials for ingestion",LCFException.SETUP_ERROR);

                if (codeValue >= 400 && codeValue < 500)
                  return;

                // Anything else means the document didn't delete.  Throw the error.
                throw new LCFException("Error deleting document: '"+cd.getDescription()+"'");
              }
              finally
              {
                out.close();
              }
            }
            finally
            {
              in.close();
            }
          }
          finally
          {
            try
            {
              socket.close();
            }
            catch (InterruptedIOException e)
            {
              throw e;
            }
            catch (IOException e)
            {
              Logging.ingest.debug("Error closing socket: "+e.getMessage(),e);
              // Do NOT rethrow
            }
          }
        }
        catch (UnsupportedEncodingException ioe)
        {
          throw new LCFException("Fatal ingestion error: "+ioe.getMessage(),ioe);
        }
        catch (InterruptedIOException ioe)
        {
          return;
        }
        catch (IOException ioe)
        {
          // Log the error
          Logging.ingest.warn("Error communicating with Ingestion API: "+ioe.getMessage(),ioe);

          activityStart = new Long(fullStartTime);
          activityCode = "-1";
          activityDetails = ioe.getMessage();

          throw ioe;
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

    public Long getActivityStart()
    {
      return activityStart;
    }

    public String getActivityCode()
    {
      return activityCode;
    }

    public String getActivityDetails()
    {
      return activityDetails;
    }
  }

  /** Killable thread that does a status check.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a status check.
  */
  protected class StatusThread extends java.lang.Thread
  {
    protected Throwable exception = null;

    public StatusThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        // Do the operation!
        // Open a socket to ingest, and to the response stream to get the post result
        try
        {
          // Set up the socket, and the (optional) secure socket.
          Socket socket = createSocket(responseRetries);
          try
          {
            InputStream in = socket.getInputStream();
            try
            {
              OutputStream out = socket.getOutputStream();
              try
              {
                // Create the output stream to GTS
                byte[] tmp = ("GET " + postStatusAction + " HTTP/1.0\r\n").getBytes("ASCII");
                out.write(tmp, 0, tmp.length);

                writeCredentials(out);

                tmp = ("Content-Length: 0\r\n\r\n").getBytes("ASCII");
                out.write(tmp, 0, tmp.length);

                if (Logging.ingest.isDebugEnabled())
                  Logging.ingest.debug("Status request posted");

                out.flush();

                CodeDetails cd = getResponse(in);

                int codeValue = cd.getCodeValue();
                if (codeValue < 0)
                  throw new LCFException("Http protocol error");

                // 200 means everything went OK
                if (codeValue == 200)
                {
                  cd.parseStatusResponse();
                  return;
                }

                // We ignore everything in the range from 400-500 now
                if (codeValue == 401)
                  throw new LCFException("Bad credentials for ingestion",LCFException.SETUP_ERROR);

                // Anything else means the info request failed.
                throw new LCFException("Error connecting to ingestion API: '"+cd.getDescription()+"'");
              }
              finally
              {
                out.close();
              }
            }
            finally
            {
              in.close();
            }
          }
          finally
          {
            try
            {
              socket.close();
            }
            catch (InterruptedIOException e)
            {
              throw e;
            }
            catch (IOException e)
            {
              Logging.ingest.debug("Error closing socket: "+e.getMessage(),e);
              // Do NOT rethrow
            }
          }
        }
        catch (UnsupportedEncodingException ioe)
        {
          throw new LCFException("Fatal ingestion error: "+ioe.getMessage(),ioe);
        }
        catch (InterruptedIOException ioe)
        {
          // Exit the thread.
          return;
        }
        catch (IOException ioe)
        {
          // Log the error
          Logging.ingest.warn("Error communicating with Ingestion API: "+ioe.getMessage(),ioe);
          throw ioe;
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
  }

  /** Code+details paper object */
  protected static class CodeDetails
  {
    protected String code;
    protected int codeValue;
    protected String details;
    protected String res;
    protected XMLDoc returnDoc;

    public CodeDetails(String res, XMLDoc returnDoc)
    {
      this.res = res;
      this.returnDoc = returnDoc;
      codeValue = -100;
      code = "-100";
      details = "Http response was improperly formed";

      int firstSpace = res.indexOf(" ");
      if (firstSpace != -1)
      {
        int secondSpace = res.indexOf(" ", firstSpace + 1);
        if (secondSpace != -1)
        {
          code = res.substring(firstSpace + 1, secondSpace);
          details = res.substring(secondSpace+1).trim();
          try
          {
            codeValue = (int)(new Double(code).doubleValue());
            if (codeValue == 200)
              details = null;
          }
          catch (NumberFormatException e)
          {
            // Fall through and leave codeValue unaltered
          }
        }
      }
    }

    public String getCode()
    {
      return code;
    }

    public int getCodeValue()
    {
      return codeValue;
    }

    public String getDetails()
    {
      return details;
    }

    public XMLDoc getReturnDoc()
    {
      return returnDoc;
    }

    public String getDescription()
      throws LCFException
    {
      return res + "\r\n" + ((returnDoc!=null)?returnDoc.getXML():"");
    }

    public void parseIngestionResponse()
      throws LCFException
    {
      // Look at response XML
      String statusValue = "unknown";
      XMLDoc doc = getReturnDoc();
      if (doc != null)
      {
        if (Logging.ingest.isDebugEnabled())
          Logging.ingest.debug("SOLR: Saw ingestion response document '"+doc.getXML()+"'");
        //Object root = doc.getRoot();
        ArrayList list = doc.processPath("*",null);
        int k = 0;
        while (k < list.size())
        {
          Object listNode = list.get(k++);
          if (doc.getNodeName(listNode).equals("response"))
          {
            ArrayList list2 = doc.processPath("*",listNode);
            int q = 0;
            while (q < list2.size())
            {
              Object respNode = list2.get(q++);
              if (doc.getNodeName(respNode).equals("lst"))
              {
                String lstName = doc.getValue(respNode,"name");
                if (lstName.equals("responseHeader"))
                {
                  ArrayList list3 = doc.processPath("*",respNode);
                  int z = 0;
                  while (z < list3.size())
                  {
                    Object headerNode = list3.get(z++);
                    if (doc.getNodeName(headerNode).equals("int"))
                    {
                      String value = doc.getValue(headerNode,"name");
                      if (value.equals("status"))
                      {
                        statusValue = doc.getData(headerNode).trim();
                      }
                    }
                  }
                }
              }
            }
          }
        }
        if (statusValue.equals("0"))
          return;

        throw new LCFException("Ingestion returned error: "+statusValue);
      }
      else
        throw new LCFException("XML parsing error on response");
    }

    public void parseRemovalResponse()
      throws LCFException
    {
      parseIngestionResponse();
    }

    public void parseStatusResponse()
      throws LCFException
    {
      // Look at response XML
      String statusValue = "unknown";
      XMLDoc doc = getReturnDoc();
      if (doc != null)
      {
        if (Logging.ingest.isDebugEnabled())
          Logging.ingest.debug("SOLR: Saw status response document '"+doc.getXML()+"'");
        //Object root = doc.getRoot();
        ArrayList list = doc.processPath("*",null);
        int k = 0;
        while (k < list.size())
        {
          Object listNode = list.get(k++);
          if (doc.getNodeName(listNode).equals("response"))
          {
            ArrayList list2 = doc.processPath("*",listNode);
            int q = 0;
            while (q < list2.size())
            {
              Object respNode = list2.get(q++);
              if (doc.getNodeName(respNode).equals("str"))
              {
                String value = doc.getValue(respNode,"name");
                if (value.equals("status"))
                {
                  statusValue = doc.getData(respNode).trim();
                }
              }
            }
          }
        }
        if (statusValue.equals("OK"))
          return;

        throw new LCFException("Status error: "+statusValue);
      }
      else
        throw new LCFException("XML parsing error on response");
    }
  }

}

