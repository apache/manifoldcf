/* $Id: HttpPoster.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.agents.output.gts;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.common.Base64;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.*;
import javax.net.ssl.*;

import org.apache.log4j.*;

/**
* Posts an input stream to the GTS
*
*/
public class HttpPoster
{
  public static final String _rcsid = "@(#)$Id: HttpPoster.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Ingestion buffer size property. */
  public static String ingestBufferSizeProperty = "org.apache.manifoldcf.ingest.buffersize";
  public static String ingestCredentialsRealm = "org.apache.manifoldcf.ingest.credentialrealm";
  public static String ingestResponseRetryCount = "org.apache.manifoldcf.ingest.responseretrycount";
  public static String ingestResponseRetryInterval = "org.apache.manifoldcf.ingest.retryinterval";
  public static String ingestRescheduleInterval = "org.apache.manifoldcf.ingest.rescheduleinterval";
  public static String ingestURIProperty = "org.apache.manifoldcf.ingest.uri";
  public static String ingestUserProperty = "org.apache.manifoldcf.ingest.user";
  public static String ingestPasswordProperty = "org.apache.manifoldcf.ingest.password";
  public static String ingestMaxConnectionsProperty = "org.apache.manifoldcf.ingest.maxconnections";

  // Chunk size for base64-encoded headers
  protected final static int HEADER_CHUNK = 4096;

  private String encodedCredentials = null;
  private String realm = null;
  private String postURI = null;
  private URL url = null;
  private URL deleteURL = null;
  private URL infoURL = null;
  private String host = null;
  private int port = 80;
  private String protocol = null;

  /** Default buffer size */
  private final int buffersize;
  /** Size coefficient */
  private static double sizeCoefficient = 0.0005;    // 20 ms additional timeout per 2000 bytes, pulled out of my butt
  /** the number of times we should poll for the response */
  private final int responseRetries;
  /** how long we should wait before checking for a new stream */
  private final long responseRetryWait;
  /** How long to wait before retrying a failed ingestion */
  private final long interruptionRetryTime;

  /** This is the secure socket factory we will use.  I'm presuming it's thread-safe, but
  * if not, synchronization blocks are in order when it's used. */
  protected static javax.net.ssl.SSLSocketFactory secureSocketFactory = null;
  static
  {
    try
    {
      secureSocketFactory = getSecureSocketFactory();
    }
    catch (ManifoldCFException e)
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
  * @param postURI the uri to post the request to
  */
  public HttpPoster(IThreadContext threadContext, String realm, String userID, String password, String postURI)
    throws ManifoldCFException
  {
    if (userID != null && userID.length() > 0 && password != null)
    {
      this.encodedCredentials = new org.apache.manifoldcf.core.common.Base64().encodeByteArray((userID+":"+password).getBytes(StandardCharsets.UTF_8));
      this.realm = realm;
    }
    this.postURI = postURI;

    // Create a URL to GTS
    try
    {
      url = new URL(postURI);
      deleteURL = new URL(postURI+"?DELETE");
      infoURL = new URL(postURI+"?STATUS");
    }
    catch (MalformedURLException murl)
    {
      throw new ManifoldCFException("Bad url",murl);
    }

    // set the port
    port = url.getPort();
    host = url.getHost();
    protocol = url.getProtocol();
    if (port == -1)
    {
      if (protocol.equalsIgnoreCase("https"))
        port = 443;
      else
        port = 80;
    }

    buffersize = LockManagerFactory.getIntProperty(threadContext,ingestBufferSizeProperty,32768);
    responseRetries = LockManagerFactory.getIntProperty(threadContext,ingestResponseRetryCount,9000);
    responseRetryWait = LockManagerFactory.getIntProperty(threadContext,ingestResponseRetryInterval,20);
    interruptionRetryTime = LockManagerFactory.getIntProperty(threadContext,ingestRescheduleInterval,60000);
  }

  /**
  * Post the input stream to ingest
  * @param documentURI is the document's uri.
  * @param document is the document structure to ingest.
  * @return true if the ingestion was successful, or false if the ingestion is illegal.
  * @throws ManifoldCFException, ServiceInterruption
  */
  public boolean indexPost(String documentURI,
    List<String> collections, String documentTemplate, String authorityNameString,
    RepositoryDocument document, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    StringBuilder aclXml = new StringBuilder();
    Iterator<String> securityTypeIterator = document.securityTypesIterator();
    String[] shareAcls = null;
    String[] shareDenyAcls = null;
    String[] documentAcls = null;
    String[] documentDenyAcls = null;
    String[] parentAcls = null;
    String[] parentDenyAcls = null;
    while (securityTypeIterator.hasNext())
    {
      String securityType = securityTypeIterator.next();
      if (securityType.equals(RepositoryDocument.SECURITY_TYPE_SHARE))
      {
        shareAcls = document.getSecurityACL(securityType);
        shareDenyAcls = document.getSecurityDenyACL(securityType);
      }
      else if (securityType.equals(RepositoryDocument.SECURITY_TYPE_DOCUMENT))
      {
        documentAcls = document.getSecurityACL(securityType);
        documentDenyAcls = document.getSecurityDenyACL(securityType);
      }
      else if (securityType.equals(RepositoryDocument.SECURITY_TYPE_PARENT))
      {
        parentAcls = document.getSecurityACL(securityType);
        parentDenyAcls = document.getSecurityDenyACL(securityType);
      }
      else
        // Can't accept the document, because we don't know how to secure it
        activities.recordActivity(null,GTSConnector.INGEST_ACTIVITY,null,documentURI,activities.UNKNOWN_SECURITY,"Rejected document that has security info which GTS does not recognize: '"+ securityType + "'");
        return false;
    }

    writeACLs(aclXml,"share",shareAcls,shareDenyAcls,authorityNameString,activities);
    writeACLs(aclXml,"directory",parentAcls,parentDenyAcls,authorityNameString,activities);
    writeACLs(aclXml,"file",documentAcls,documentDenyAcls,authorityNameString,activities);

    if (aclXml.length() > 0)
      aclXml.append("</document-acl>");
    String aclXmlString = aclXml.toString();

    if (Logging.ingest.isDebugEnabled())
      Logging.ingest.debug("indexPost(): '" + documentURI + "'");

    // This flag keeps track of whether we read anything from the input stream yet.
    // If not, we can retry here.  If so, we have to reschedule.
    boolean readFromDocumentStreamYet = false;
    int ioErrorRetry = 3;

    while (true)
    {
      try
      {
        IngestThread t = new IngestThread(documentURI,aclXmlString,collections,documentTemplate,document);
        try
        {
          t.start();
          t.join();

          // Log the activity, if any, regardless of any exception
          if (t.getActivityCode() != null)
            activities.recordActivity(t.getActivityStart(),GTSConnector.INGEST_ACTIVITY,t.getActivityBytes(),documentURI,t.getException().getClass().getSimpleName().toUpperCase(Locale.ROOT),t.getActivityDetails());

          readFromDocumentStreamYet = (readFromDocumentStreamYet || t.getReadFromDocumentStreamYet());

          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof ServiceInterruption)
              throw (ServiceInterruption)thr;
            if (thr instanceof ManifoldCFException)
              throw (ManifoldCFException)thr;
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
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
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
        ManifoldCF.sleep(10000L);
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
      }
      ioErrorRetry--;

      // Go back around again!

    }

  }

  /** Write acls into a StringBuilder */
  protected static void writeACLs(StringBuilder aclXml, String type, String[] acl, String[] denyAcl, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException
  {
    if (acl != null && acl.length > 0 || denyAcl != null && denyAcl.length > 0)
    {
      if (aclXml.length() == 0)
        aclXml.append("<document-acl>");
      aclXml.append("<acl scope=\"").append(type).append("\">");
      if (acl != null)
      {
        for (int i=0; i < acl.length; i++)
        {
          if (Logging.ingest.isDebugEnabled())
            Logging.ingest.debug("Adding "+type+" ACL: " + acl[i]);
          aclXml.append("<allow>");
          aclXml.append(activities.qualifyAccessToken(authorityNameString,acl[i]));
          aclXml.append("</allow>");
        }
      }
      if (denyAcl != null)
      {
        for (int i=0; i < denyAcl.length; i++)
        {
          if (Logging.ingest.isDebugEnabled())
            Logging.ingest.debug("Adding "+type+" deny ACL: " + denyAcl[i]);
          aclXml.append("<deny>");
          aclXml.append(activities.qualifyAccessToken(authorityNameString,denyAcl[i]));
          aclXml.append("</deny>");
        }
      }
      aclXml.append("</acl>");
    }
  }

  /** Post a check request.
  */
  public void checkPost()
    throws ManifoldCFException, ServiceInterruption
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
            if (thr instanceof ManifoldCFException)
              throw (ManifoldCFException)thr;
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
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
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
        ManifoldCF.sleep(10000L);
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }
      ioErrorRetry--;

    }

  }

  /** Post a delete request.
  *@param documentURI is the document's URI.
  */
  public void deletePost(String documentURI, IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
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
            activities.recordActivity(t.getActivityStart(),GTSConnector.REMOVE_ACTIVITY,null,documentURI,t.getException().getClass().getSimpleName().toUpperCase(Locale.ROOT),t.getActivityDetails());

          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof ServiceInterruption)
              throw (ServiceInterruption)thr;
            if (thr instanceof ManifoldCFException)
              throw (ManifoldCFException)thr;
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
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),ManifoldCFException.INTERRUPTED);
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
        ManifoldCF.sleep(10000L);
      }
      catch (InterruptedException e)
      {
        throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
      }

      ioErrorRetry--;

    }

  }

  /**
  * Get the response code of the post
  * @param stream the stream the response is going to come from
  * @return the response string
  * @throws ManifoldCFException
  */
  protected String getResponse(BufferedReader stream) throws ManifoldCFException, ServiceInterruption
  {
    Logging.ingest.debug("Waiting for response stream");
    StringBuilder res = new StringBuilder();
    try
    {
      // Stream.ready() always returns false for secure sockets :-(.  So
      // we have to rely on socket timeouts to interrupt us if the server goes down.

      while (true)
      {
        int i = stream.read();
        if (i == -1)
          break;
        res.append((char) i);
      }
      Logging.ingest.debug("Read of response stream complete");
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
      throw new ManifoldCFException("Interrupted",ManifoldCFException.INTERRUPTED);
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
      return "HTTP/1.0 400 Connection Reset";

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

    return res.toString();
  }

  /** Write credentials to output */
  protected void writeCredentials(OutputStream out)
    throws IOException
  {
    // Apply credentials if present
    if (encodedCredentials != null)
    {
      Logging.ingest.debug("Applying credentials");
      byte[] tmp = ("Authorization: Basic " + encodedCredentials + "\r\n").getBytes(StandardCharsets.UTF_8);
      out.write(tmp, 0, tmp.length);

      tmp = ("WWW-Authenticate: Basic realm=\"" + ((realm != null) ? realm : "") + "\"\r\n").getBytes(StandardCharsets.UTF_8);
      out.write(tmp, 0, tmp.length);
    }
  }

  /** Encode for metadata.
  *@param inputString is the input string.
  *@return output, encoded.
  */
  protected static String metadataEncode(String inputString)
  {
    StringBuilder rval = new StringBuilder();
    int i = 0;
    while (i < inputString.length())
    {
      char x = inputString.charAt(i++);
      // Certain characters must simply be skipped, because they are illegal in header fields.
      if (x >= ' ' && x <= (char)127)
      {
        if (x == '\\' || x == ',')
          rval.append('\\');
        rval.append(x);
      }
    }
    return rval.toString();
  }

  /** Build a secure socket factory based on no keystore and a lax trust manager.
  * This allows use of SSL for privacy but not identification. */
  protected static javax.net.ssl.SSLSocketFactory getSecureSocketFactory()
    throws ManifoldCFException
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
      throw new ManifoldCFException("No such algorithm",e);
    }
    catch (java.security.KeyManagementException e)
    {
      throw new ManifoldCFException("Key management exception",e);
    }
  }

  /** Create a socket in a manner consistent with all of our specified parameters.
  */
  protected Socket createSocket(long responseRetryCount)
    throws IOException, ManifoldCFException
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
        throw new ManifoldCFException("Couldn't set up SSL connection to ingestion API: "+e.getMessage(),e);
      }
    }
    else
      socket = new Socket(host, port);

    // Calculate the timeout we want
    long timeoutMilliseconds = responseRetryWait * responseRetryCount;
    socket.setSoTimeout((int)timeoutMilliseconds);

    return socket;
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

  /** Killable thread that does ingestions.
  * Java 1.5 stopped permitting thread interruptions to abort socket waits.  As a result, it is impossible to get threads to shutdown cleanly that are doing
  * such waits.  So, the places where this happens are segregated in their own threads so that they can be just abandoned.
  *
  * This thread does a single document ingestion.
  */
  protected class IngestThread extends java.lang.Thread
  {
    protected String documentURI;
    protected String aclXmlString;
    protected List<String> collections;
    protected String documentTemplate;
    protected RepositoryDocument document;

    protected Long activityStart = null;
    protected Long activityBytes = null;
    protected String activityCode = null;
    protected String activityDetails = null;
    protected Throwable exception = null;
    protected boolean readFromDocumentStreamYet = false;
    protected boolean rval = false;

    public IngestThread(String documentURI, String aclXmlString, List<String> collections, String documentTemplate, RepositoryDocument document)
    {
      super();
      setDaemon(true);
      this.documentURI = documentURI;
      this.aclXmlString = aclXmlString;
      this.collections = collections;
      this.documentTemplate = documentTemplate;
      this.document = document;
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

            InputStreamReader isr = new InputStreamReader(socket.getInputStream(),"ASCII");
            try
            {
              BufferedReader in = new BufferedReader(isr);
              try
              {
                OutputStream out = socket.getOutputStream();
                try
                {
                  // Create the output stream to GTS
                  String uri = url.getFile();
                  if (uri.length() == 0)
                    uri = "/";
                  byte[] tmp = ("POST " + uri + " HTTP/1.0\r\n").getBytes(StandardCharsets.UTF_8);
                  out.write(tmp, 0, tmp.length);

                  // Set all the headers
                  tmp = ("Document-URI: " + documentURI + "\r\n").getBytes(StandardCharsets.UTF_8);
                  out.write(tmp, 0, tmp.length);

                  writeCredentials(out);

                  // Apply ACL if present
                  if (aclXmlString.length() > 0)
                  {

                    String encodedACL = new Base64().encodeByteArray(aclXmlString.getBytes(StandardCharsets.UTF_8));

                    // Break into chunks - 4K each - 'cause otherwise we blow up the ingester.
                    int index = 0;
                    while (true)
                    {
                      if (index + HEADER_CHUNK >= encodedACL.length())
                      {
                        tmp = ("Document-ACL: " + encodedACL.substring(index) + "\r\n").getBytes(StandardCharsets.UTF_8);
                        out.write(tmp, 0, tmp.length);
                        break;
                      }
                      tmp = ("Document-ACL: " + encodedACL.substring(index,index + HEADER_CHUNK) + "\r\n").getBytes(StandardCharsets.UTF_8);
                      out.write(tmp, 0, tmp.length);
                      index += HEADER_CHUNK;
                    }
                  }

                  // Do the collections
                  if (collections != null)
                  {
                    for (String collectionName : collections)
                    {
                      String encodedValue = metadataEncode(collectionName);
                      //System.out.println("collection metadata: collection_name = '"+encodedValue+"'");
                      tmp = ("Document-Metadata: collection_name="+encodedValue+"\r\n").getBytes(StandardCharsets.UTF_8);
                      out.write(tmp, 0, tmp.length);
                    }
                  }

                  // Do the document template
                  if (documentTemplate != null && documentTemplate.length() > 0)
                  {
                    String encodedTemplate = new Base64().encodeByteArray(documentTemplate.getBytes(StandardCharsets.UTF_8));
                    // Break into chunks - 4K each - 'cause otherwise we blow up the ingester.
                    int index = 0;
                    while (true)
                    {
                      if (index + HEADER_CHUNK >= encodedTemplate.length())
                      {
                        tmp = ("Document-Template: " + encodedTemplate.substring(index) + "\r\n").getBytes(StandardCharsets.UTF_8);
                        out.write(tmp, 0, tmp.length);
                        break;
                      }
                      tmp = ("Document-Template: " + encodedTemplate.substring(index,index + HEADER_CHUNK) + "\r\n").getBytes(StandardCharsets.UTF_8);
                      out.write(tmp, 0, tmp.length);
                      index += HEADER_CHUNK;
                    }
                  }

                  // Write all the metadata, if any
                  Iterator<String> iter = document.getFields();
                  while (iter.hasNext())
                  {
                    String fieldName = iter.next();
                    String[] values = document.getFieldAsStrings(fieldName);
                    // We only handle strings right now!!!
                    int k = 0;
                    while (k < values.length)
                    {
                      String value = (String)values[k++];

                      String encodedValue = metadataEncode(value);
                      //System.out.println("Metadata: Name = '"+fieldName+"', value = '"+encodedValue+"'");
                      tmp = ("Document-Metadata: "+ fieldName+"="+encodedValue+"\r\n").getBytes(StandardCharsets.UTF_8);
                      out.write(tmp, 0, tmp.length);
                    }
                  }

                  tmp = ("Content-length: " + new Long(length).toString() + "\r\n\n").getBytes(StandardCharsets.UTF_8);
                  out.write(tmp, 0, tmp.length);

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
                      // We need to decide whether to throw a service interruption or metacarta exception, based on what went wrong.
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

                  later = System.currentTimeMillis();
                  if (Logging.ingest.isDebugEnabled())
                    Logging.ingest.debug("Total bytes posted: " + new Long(total).toString() + ", total time: " + (later - now));

                  out.flush();

                  // Now, process response
                  String res;
                  try
                  {
                    res = getResponse(in);
                  }
                  catch (ServiceInterruption si)
                  {
                    activityStart = new Long(now);
                    activityCode = "-2";
                    activityDetails = si.getMessage();
                    throw si;
                  }

                  if (Logging.ingest.isDebugEnabled())
                    Logging.ingest.debug("Response code from ingest: '" + res + "'");

                  CodeDetails cd = new CodeDetails(res);

                  activityStart = new Long(now);
                  activityBytes = new Long(length);
                  activityCode = cd.getCode();
                  activityDetails = cd.getDetails();

                  int codeValue = cd.getCodeValue();

                  // A negative number means http error of some kind.
                  if (codeValue < 0)
                    throw new ManifoldCFException("Http protocol error");

                  // 200 means everything went OK
                  if (codeValue == 200)
                  {
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
                  // 3) Something is wrong with the setup, e.g. bad credentials.  In this case we chuck a ManifoldCFException,
                  //    since this will abort the current activity entirely.

                  if (codeValue == 401)
                    throw new ManifoldCFException("Bad credentials for ingestion",ManifoldCFException.SETUP_ERROR);

                  if (codeValue >= 400 && codeValue < 500)
                  {
                    rval = false;
                    return;
                  }

                  // If this continues, we should indeed abort the job.  Retries should not go on indefinitely either; 2 hours is plenty
                  long currentTime = System.currentTimeMillis();
                  throw new ServiceInterruption("Error "+Integer.toString(codeValue)+" from ingestion request; ingestion will be retried again later",
                    new ManifoldCFException("Ingestion HTTP error code "+Integer.toString(codeValue)),
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
              isr.close();
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
          //
          if (ioe.getClass().getName().equals("java.net.SocketException") && ioe.getMessage().toLowerCase(Locale.ROOT).indexOf("broken pipe") != -1)
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
            InputStreamReader isr = new InputStreamReader(socket.getInputStream(),"ASCII");
            try
            {
              BufferedReader in = new BufferedReader(isr);
              try
              {
                OutputStream out = socket.getOutputStream();
                try
                {
                  long startTime = System.currentTimeMillis();
                  // Create the output stream to GTS
                  byte[] tmp = ("POST " + deleteURL.getFile() + " HTTP/1.0\r\n").getBytes(StandardCharsets.UTF_8);
                  out.write(tmp, 0, tmp.length);

                  // Set all the headers
                  tmp = ("Document-URI: " + documentURI + "\r\n").getBytes(StandardCharsets.UTF_8);
                  out.write(tmp, 0, tmp.length);

                  writeCredentials(out);

                  tmp = ("Content-length: 0\r\n\n").getBytes(StandardCharsets.UTF_8);
                  out.write(tmp, 0, tmp.length);

                  if (Logging.ingest.isDebugEnabled())
                    Logging.ingest.debug("Delete posted");

                  out.flush();

                  String res;
                  try
                  {
                    res = getResponse(in);
                  }
                  catch (ServiceInterruption si)
                  {
                    activityStart = new Long(startTime);
                    activityCode = "-2";
                    activityDetails = si.getMessage();
                    throw si;
                  }

                  if (Logging.ingest.isDebugEnabled())
                    Logging.ingest.debug("Response code from delete: '" + res + "'");

                  CodeDetails cd = new CodeDetails(res);

                  activityStart = new Long(startTime);
                  activityCode = cd.getCode();
                  activityDetails = cd.getDetails();

                  int codeValue = cd.getCodeValue();

                  if (codeValue < 0)
                    throw new ManifoldCFException("Http protocol error");

                  // 200 means everything went OK
                  if (codeValue == 200)
                    return;

                  // We ignore everything in the range from 400-500 now
                  if (codeValue == 401)
                    throw new ManifoldCFException("Bad credentials for ingestion",ManifoldCFException.SETUP_ERROR);

                  if (codeValue >= 400 && codeValue < 500)
                    return;

                  // Anything else means the document didn't delete.  Throw the error.
                  throw new ManifoldCFException("Error deleting document: '"+res+"'");
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
              isr.close();
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
            InputStreamReader isr = new InputStreamReader(socket.getInputStream(),"ASCII");
            try
            {
              BufferedReader in = new BufferedReader(isr);
              try
              {
                OutputStream out = socket.getOutputStream();
                try
                {
                  // Create the output stream to GTS
                  byte[] tmp = ("GET " + infoURL.getFile() + " HTTP/1.0\r\n").getBytes(StandardCharsets.UTF_8);
                  out.write(tmp, 0, tmp.length);

                  writeCredentials(out);

                  tmp = ("Content-length: 0\r\n\n").getBytes(StandardCharsets.UTF_8);
                  out.write(tmp, 0, tmp.length);

                  if (Logging.ingest.isDebugEnabled())
                    Logging.ingest.debug("Status request posted");

                  out.flush();

                  String res = getResponse(in);

                  if (Logging.ingest.isDebugEnabled())
                    Logging.ingest.debug("Response code from delete: '" + res + "'");

                  CodeDetails cd = new CodeDetails(res);

                  int codeValue = cd.getCodeValue();
                  if (codeValue < 0)
                    throw new ManifoldCFException("Http protocol error");

                  // 200 means everything went OK
                  if (codeValue == 200)
                    return;

                  // We ignore everything in the range from 400-500 now
                  if (codeValue == 401)
                    throw new ManifoldCFException("Bad credentials for ingestion",ManifoldCFException.SETUP_ERROR);

                  // Anything else means the info request failed.
                  throw new ManifoldCFException("Error connecting to MetaCarta ingestion API: '"+res+"'");
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
              isr.close();
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

    public CodeDetails(String res)
    {
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

  }

}

