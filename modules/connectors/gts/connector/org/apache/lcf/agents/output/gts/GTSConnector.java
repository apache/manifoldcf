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
package org.apache.lcf.agents.output.gts;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.agents.system.Logging;

// POIFS stuff
import org.apache.poi.poifs.eventfilesystem.POIFSReader;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderListener;
import org.apache.poi.poifs.eventfilesystem.POIFSReaderEvent;
import org.apache.poi.poifs.filesystem.POIFSDocumentPath;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hpsf.PropertySetFactory;
import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.hpsf.NoPropertySetStreamException;
import org.apache.poi.hpsf.MarkUnsupportedException;
import org.apache.poi.hpsf.UnexpectedPropertySetTypeException;

import java.util.*;
import java.io.*;

/** This is the output connector for the MetaCarta appliance.  It establishes a notion of
* collection(s) a document is ingested into, as well as the idea of a document template for the
* output.
*/
public class GTSConnector extends org.apache.lcf.agents.output.BaseOutputConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Activities we log

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  // These are the document types the fingerprinter understands
  protected static final int DT_UNKNOWN = -1;
  protected static final int DT_COMPOUND_DOC = 0;
  protected static final int DT_MSWORD = 1;
  protected static final int DT_MSEXCEL = 2;
  protected static final int DT_MSPOWERPOINT = 3;
  protected static final int DT_MSOUTLOOK = 4;
  protected static final int DT_TEXT = 5;
  protected static final int DT_ZERO = 6;
  protected static final int DT_PDF = 7;

  /** Local data */
  protected HttpPoster poster = null;

  /** Constructor.
  */
  public GTSConnector()
  {
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  public String[] getActivitiesList()
  {
    return new String[]{INGEST_ACTIVITY,REMOVE_ACTIVITY};
  }

  /** Return the path for the UI interface JSP elements.
  * This method should return the name of the folder, under the <webapp>/output/
  * area, where the appropriate JSP's can be found.  The name should NOT have a slash in it.
  *@return the folder part
  */
  public String getJSPFolder()
  {
    return "gts";
  }

  /** Connect.
  *@param configParameters is the set of configuration parameters, which
  * in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
  * out of the ini file.)
  */
  public void connect(ConfigParams configParameters)
  {
    super.connect(configParameters);
  }

  /** Close the connection.  Call this before discarding the connection.
  */
  public void disconnect()
    throws LCFException
  {
    poster = null;
    super.disconnect();
  }

  /** Set up a session */
  protected void getSession()
    throws LCFException
  {
    if (poster == null)
    {
      String ingestURI = params.getParameter(GTSConfig.PARAM_INGESTURI);
      if (ingestURI == null)
        throw new LCFException("Missing parameter '"+GTSConfig.PARAM_INGESTURI+"'");
      String userID = params.getParameter(GTSConfig.PARAM_USERID);
      String password = params.getObfuscatedParameter(GTSConfig.PARAM_PASSWORD);
      String realm = params.getParameter(GTSConfig.PARAM_REALM);
      poster = new HttpPoster(realm,userID,password,ingestURI);
    }
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  public String check()
    throws LCFException
  {
    try
    {
      getSession();
      poster.checkPost();
      return super.check();
    }
    catch (ServiceInterruption e)
    {
      return "Transient error: "+e.getMessage();
    }
  }

  /** Pre-determine whether a document (passed here as a File object) is indexable by this connector.  This method is used by participating
  * repository connectors to help reduce the number of unmanageable documents that are passed to this output connector in advance of an
  * actual transfer.  This hook is provided mainly to support search engines that only handle a small set of accepted file types.
  *@param localFile is the local file to check.
  *@return true if the file is indexable.
  */
  public boolean checkDocumentIndexable(File localFile)
    throws LCFException, ServiceInterruption
  {
    if (!super.checkDocumentIndexable(localFile))
      return false;
    int docType = fingerprint(localFile);
    return (docType == DT_TEXT ||
      docType == DT_MSWORD ||
      docType == DT_MSEXCEL ||
      docType == DT_PDF ||
      docType == DT_MSPOWERPOINT);
  }

  /** Get an output version string, given an output specification.  The output version string is used to uniquely describe the pertinent details of
  * the output specification and the configuration, to allow the Connector Framework to determine whether a document will need to be output again.
  * Note that the contents of the document cannot be considered by this method, and that a different version string (defined in IRepositoryConnector)
  * is used to describe the version of the actual document.
  *
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param spec is the current output specification for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal,
  * the document will not need to be sent again to the output data store.
  */
  public String getOutputDescription(OutputSpecification spec)
    throws LCFException
  {
    // The information we want in this string is:
    // (1) the collection name(s), in sorted order.
    // (2) the document template
    // (3) the ingest URI

    ArrayList collectionList = new ArrayList();
    String documentTemplate = "";
    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals(GTSConfig.NODE_COLLECTION))
      {
        collectionList.add(sn.getAttributeValue(GTSConfig.ATTRIBUTE_VALUE));
      }
      else if (sn.getType().equals(GTSConfig.NODE_DOCUMENTTEMPLATE))
      {
        documentTemplate = sn.getAttributeValue(GTSConfig.ATTRIBUTE_VALUE);
      }
    }

    String[] sortArray = new String[collectionList.size()];
    i = 0;
    while (i < sortArray.length)
    {
      sortArray[i] = (String)collectionList.get(i);
      i++;
    }
    java.util.Arrays.sort(sortArray);

    // Get the config info too.  This will be constant for any given connector instance, so we don't have to worry about it changing
    // out from under us.
    String ingestURI = params.getParameter(GTSConfig.PARAM_INGESTURI);

    // Now, construct the appropriate string
    StringBuffer sb = new StringBuffer();
    packList(sb,sortArray,'+');
    pack(sb,documentTemplate,'+');
    // From here on down, unpacking is unnecessary.
    sb.append(ingestURI);

    return sb.toString();
  }

  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  * The OutputSpecification is *not* provided to this method, because the goal is consistency, and if output is done it must be consistent with the
  * output description, since that was what was partly used to determine if output should be taking place.  So it may be necessary for this method to decode
  * an output description string in order to determine what should be done.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  *@return the document status (accepted or permanently rejected).
  */
  public int addOrReplaceDocument(String documentURI, String outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws LCFException, ServiceInterruption
  {
    // Establish a session
    getSession();

    // Unpack what we need from the output description.  This consists of the collection names, plus the document template.
    ArrayList collections = new ArrayList();
    StringBuffer documentTemplateBuffer = new StringBuffer();
    int startPosition = unpackList(collections,outputDescription,0,'+');
    startPosition = unpack(documentTemplateBuffer,outputDescription,startPosition,'+');

    String[] collectionArray = new String[collections.size()];
    int i = 0;
    while (i < collectionArray.length)
    {
      collectionArray[i] = (String)collections.get(i);
      i++;
    }

    // Now, go off and call the ingest API.
    if (poster.indexPost(documentURI,collectionArray,documentTemplateBuffer.toString(),authorityNameString,document,activities))
      return DOCUMENTSTATUS_ACCEPTED;
    return DOCUMENTSTATUS_REJECTED;
  }

  /** Remove a document using the connector.
  * Note that the last outputDescription is included, since it may be necessary for the connector to use such information to know how to properly remove the document.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the last description string that was constructed for this document by the getOutputDescription() method above.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  */
  public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
    throws LCFException, ServiceInterruption
  {
    // Establish a session
    getSession();

    // Call the ingestion API.
    poster.deletePost(documentURI,activities);
  }

  // Protected methods

  /** Stuffer for packing a single string with an end delimiter */
  protected static void pack(StringBuffer output, String value, char delimiter)
  {
    int i = 0;
    while (i < value.length())
    {
      char x = value.charAt(i++);
      if (x == '\\' || x == delimiter)
        output.append('\\');
      output.append(x);
    }
    output.append(delimiter);
  }

  /** Unstuffer for the above. */
  protected static int unpack(StringBuffer sb, String value, int startPosition, char delimiter)
  {
    while (startPosition < value.length())
    {
      char x = value.charAt(startPosition++);
      if (x == '\\')
      {
        if (startPosition < value.length())
          x = value.charAt(startPosition++);
      }
      else if (x == delimiter)
        break;
      sb.append(x);
    }
    return startPosition;
  }

  /** Stuffer for packing lists of fixed length */
  protected static void packFixedList(StringBuffer output, String[] values, char delimiter)
  {
    int i = 0;
    while (i < values.length)
    {
      pack(output,values[i++],delimiter);
    }
  }

  /** Unstuffer for unpacking lists of fixed length */
  protected static int unpackFixedList(String[] output, String value, int startPosition, char delimiter)
  {
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (i < output.length)
    {
      sb.setLength(0);
      startPosition = unpack(sb,value,startPosition,delimiter);
      output[i++] = sb.toString();
    }
    return startPosition;
  }

  /** Stuffer for packing lists of variable length */
  protected static void packList(StringBuffer output, ArrayList values, char delimiter)
  {
    pack(output,Integer.toString(values.size()),delimiter);
    int i = 0;
    while (i < values.size())
    {
      pack(output,values.get(i++).toString(),delimiter);
    }
  }

  /** Another stuffer for packing lists of variable length */
  protected static void packList(StringBuffer output, String[] values, char delimiter)
  {
    pack(output,Integer.toString(values.length),delimiter);
    int i = 0;
    while (i < values.length)
    {
      pack(output,values[i++],delimiter);
    }
  }

  /** Unstuffer for unpacking lists of variable length.
  *@param output is the array into which to write the unpacked result.
  *@param value is the value to unpack.
  *@param startPosition is the place to start the unpack.
  *@param delimiter is the character to use between values.
  *@return the next position beyond the end of the list.
  */
  protected static int unpackList(ArrayList output, String value, int startPosition, char delimiter)
  {
    StringBuffer sb = new StringBuffer();
    startPosition = unpack(sb,value,startPosition,delimiter);
    try
    {
      int count = Integer.parseInt(sb.toString());
      int i = 0;
      while (i < count)
      {
        sb.setLength(0);
        startPosition = unpack(sb,value,startPosition,delimiter);
        output.add(sb.toString());
        i++;
      }
    }
    catch (NumberFormatException e)
    {
    }
    return startPosition;
  }

  /** Fingerprint a file!
  * Pass in the name of the (local) temporary file that we should be looking at.
  * This method will read it as needed until the file has been identified (or found
  * to remain "unknown").
  * The code here has been lifted algorithmically from products/ShareCrawler/Fingerprinter.pas.
  */
  protected static int fingerprint(File file)
    throws LCFException
  {
    try
    {
      // Look at the first 4K
      byte[] byteBuffer = new byte[4096];
      int amt;

      // Open file for reading.
      InputStream is = new FileInputStream(file);
      try
      {
        amt = 0;
        while (amt < byteBuffer.length)
        {
          int incr = is.read(byteBuffer,amt,byteBuffer.length-amt);
          if (incr == -1)
            break;
          amt += incr;
        }
      }
      finally
      {
        is.close();
      }

      if (amt == 0)
        return DT_ZERO;

      if (isText(byteBuffer,amt))
      {
        // Treat as ASCII text
        // We don't need to distinguish between the various flavors (e.g. HTML,
        // XML, RTF, or plain TEXT, because GTS will eat them all regardless.
        // Since it's a bit dicey to figure out the encoding, we'll just presume
        // it's something that GTS will understand.
        return DT_TEXT;
      }

      // Treat it as binary

      // Is it PDF?  Does it begin with "%PDF-"?
      if (byteBuffer[0] == (byte)0x25 && byteBuffer[1] == (byte)0x50 && byteBuffer[2] == (byte)0x44 && byteBuffer[3] == (byte)0x46)
        return DT_PDF;

      // Is it a compound document? Does it begin with 0xD0CF11E0A1B11AE1?
      if (Logging.ingest.isDebugEnabled())
        Logging.ingest.debug("GTS: Document begins with: "+hexprint(byteBuffer[0])+hexprint(byteBuffer[1])+
        hexprint(byteBuffer[2])+hexprint(byteBuffer[3])+hexprint(byteBuffer[4])+hexprint(byteBuffer[5])+
        hexprint(byteBuffer[6])+hexprint(byteBuffer[7]));
      if (byteBuffer[0] == (byte)0xd0 && byteBuffer[1] == (byte)0xcf && byteBuffer[2] == (byte)0x11 && byteBuffer[3] == (byte)0xe0 &&
        byteBuffer[4] == (byte)0xa1 && byteBuffer[5] == (byte)0xb1 && byteBuffer[6] == (byte)0x1a && byteBuffer[7] == (byte)0xe1)
      {
        Logging.ingest.debug("GTS: Compound document signature detected");
        // Figure out what kind of compound document it is.
        String appName = getAppName(file);
        if (appName == null)
          return DT_UNKNOWN;
        else
        {
          if (Logging.ingest.isDebugEnabled())
            Logging.ingest.debug("GTS: Appname is '"+appName+"'");
        }
        return recognizeApp(appName);
      }

      return DT_UNKNOWN;
    }
    catch (java.net.SocketTimeoutException e)
    {
      return DT_UNKNOWN;
    }
    catch (InterruptedIOException e)
    {
      throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
    }
    catch (IOException e)
    {
      // An I/O error indicates that the type is unknown.
      return DT_UNKNOWN;
    }
    catch (IllegalArgumentException e)
    {
      // Another POI error, means unknown document type
      return DT_UNKNOWN;
    }
    catch (IllegalStateException e)
    {
      // Another POI error, means unknown document type
      return DT_UNKNOWN;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      // This means that poi couldn't find the bytes it was expecting, so just treat it as unknown
      return DT_UNKNOWN;
    }
    catch (ClassCastException e)
    {
      // This means that poi had an internal error
      return DT_UNKNOWN;
    }
    catch (OutOfMemoryError e)
    {
      // POI seems to throw this for some kinds of corrupt documents.
      // I'm not sure this is the right thing to do but it's the best I
      // can at the moment, until I get some documents from Norway that
      // demonstrate the problem.
      return DT_UNKNOWN;
    }
  }

  /** Get a binary document's APPNAME field, or return null if the document
  * does not seem to be an OLE compound document.
  */
  protected static String getAppName(File documentPath)
    throws LCFException
  {
    try
    {
      InputStream is = new FileInputStream(documentPath);
      try
      {
        // Use POIFS to traverse the file
        POIFSReader reader = new POIFSReader();
        ReaderListener listener = new ReaderListener();
        reader.registerListener(listener,"\u0005SummaryInformation");
        reader.read(is);
        if (Logging.ingest.isDebugEnabled())
          Logging.ingest.debug("GTS: Done finding appname for '"+documentPath.toString()+"'");
        return listener.getAppName();
      }
      finally
      {
        is.close();
      }
    }
    catch (java.net.SocketTimeoutException e)
    {
      return null;
    }
    catch (InterruptedIOException e)
    {
      throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
    }
    catch (Throwable e)
    {
      // We should eat all errors.  Also, even though our policy is to stop the crawler on out-of-memory errors, in this case we will
      // not do that, because there's no "collateral damage" that can result from a fingerprinting failure.  No locks can be dropped, and
      // we cannot screw up the database driver.
      // Any collateral damage that we *do* need to stop for should manifest itself in another thread.

      // The exception effectively means that we cannot identify the document.
      return null;
    }
  }

  /** Translate a string application name to one of the kinds of documents
  * we care about.
  */
  protected static int recognizeApp(String appName)
  {
    appName = appName.toUpperCase();
    if (appName.indexOf("MICROSOFT WORD") != -1)
      return DT_MSWORD;
    if (appName.indexOf("MICROSOFT OFFICE WORD") != -1)
      return DT_MSWORD;
    if (appName.indexOf("MICROSOFT EXCEL") != -1)
      return DT_MSEXCEL;
    if (appName.indexOf("MICROSOFT POWERPOINT") != -1)
      return DT_MSPOWERPOINT;
    if (appName.indexOf("MICROSOFT OFFICE POWERPOINT") != -1)
      return DT_MSPOWERPOINT;
    if (appName.indexOf("MICROSOFT OUTLOOK") != -1)
      return DT_MSOUTLOOK;
    return DT_COMPOUND_DOC;
  }

  /** Test to see if a document is text or not.  The first n bytes are passed
  * in, and this code returns "true" if it thinks they represent text.  The code
  * has been lifted algorithmically from products/Sharecrawler/Fingerprinter.pas,
  * which was based on "perldoc -f -T".
  */
  protected static boolean isText(byte[] beginChunk, int chunkLength)
  {
    if (chunkLength == 0)
      return true;
    int i = 0;
    int count = 0;
    while (i < chunkLength)
    {
      byte x = beginChunk[i++];
      if (x == 0)
        return false;
      if (isStrange(x))
        count++;
    }
    return ((double)count)/((double)chunkLength) < 0.30;
  }

  /** Check if character is not typical ASCII. */
  protected static boolean isStrange(byte x)
  {
    return (x > 127 || x < 32) && (!isWhiteSpace(x));
  }

  /** Check if a byte is a whitespace character. */
  protected static boolean isWhiteSpace(byte x)
  {
    return (x == 0x09 || x == 0x0a || x == 0x0d || x == 0x20);
  }

  protected static String hexprint(byte x)
  {
    StringBuffer sb = new StringBuffer();
    sb.append(nibbleprint(0x0f & (((int)x)>>4))).append(nibbleprint(0x0f & ((int)x)));
    return sb.toString();
  }

  protected static char nibbleprint(int x)
  {
    if (x >= 10)
      return (char)(x - 10 + 'a');
    return (char)(x + '0');
  }

  /** Reader listener object that extracts the app name */
  protected static class ReaderListener implements POIFSReaderListener
  {
    protected String appName = null;

    /** Constructor. */
    public ReaderListener()
    {
    }

    /** Get the app name.
    */
    public String getAppName()
    {
      return appName;
    }

    /** Process an "event" from POIFS - which is basically just the fact that we saw what we
    * said we wanted to see, namely the SummaryInfo stream.
    */
    public void processPOIFSReaderEvent(POIFSReaderEvent event)
    {
      // Catch exceptions
      try
      {
        InputStream is = event.getStream();
        try
        {
          PropertySet ps = PropertySetFactory.create(is);
          if (!(ps instanceof SummaryInformation))
          {
            appName = null;
            return;
          }
          appName = ((SummaryInformation)ps).getApplicationName();
        }
        finally
        {
          is.close();
        }

      }
      catch (NoPropertySetStreamException e)
      {
        // This means we couldn't figure out what the application was
        appName = null;
        return;
      }
      catch (MarkUnsupportedException e)
      {
        // Bad code; need to suport mark operation.
        Logging.ingest.error("Need to feed a stream that supports mark(): "+e.getMessage(),e);
        appName = null;
        return;
      }
      catch (java.io.UnsupportedEncodingException e)
      {
        // Bad code; need to support encoding properly
        Logging.ingest.error("Need to support encoding: "+e.getMessage(),e);
        appName = null;
        return;
      }
      catch (IOException e)
      {
        appName = null;
        return;
      }
    }
  }

}
