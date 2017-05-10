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
package org.apache.manifoldcf.agents.transformation.contentlimiter;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.util.*;

public class ContentLimiter extends org.apache.manifoldcf.agents.transformation.BaseTransformationConnector {

  /** Forward to the javascript to check the specification parameters for the job */
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  
  private static final String EDIT_SPECIFICATION_CONTENT_HTML = "editSpecification_Content.html";
  
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";

  protected static final String ACTIVITY_LIMIT = "limit";

  protected static final String[] activitiesList = new String[]{ACTIVITY_LIMIT};
  
  /** We handle up to 64K in memory; after that we go to disk. */
  protected static final long inMemoryMaximumFile = 65536;
  
  /** Return a list of activities that this connector generates.
  * The connector does NOT need to be connected before this method is called.
  *@return the set of activities.
  */
  @Override
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** Constructor.
   */
  public ContentLimiter(){
  }
  
  /** Get an output version string, given an output specification.  The output version string is used to uniquely describe the pertinent details of
  * the output specification and the configuration, to allow the Connector Framework to determine whether a document will need to be output again.
  * Note that the contents of the document cannot be considered by this method, and that a different version string (defined in IRepositoryConnector)
  * is used to describe the version of the actual document.
  *
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param os is the current output specification for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes output configuration and specification in such a way that if two such strings are equal,
  * the document will not need to be sent again to the output data store.
  */
  @Override
  public VersionContext getPipelineDescription(Specification os)
    throws ManifoldCFException, ServiceInterruption
  {
    SpecPacker sp = new SpecPacker(os);
    return new VersionContext(sp.toPackedString(),params,os);
  }

  /** Detect if a mime type is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
  * unusable documents that will be passed to this output connector.
  *@param outputDescription is the document's output version.
  *@param mimeType is the mime type of the document.
  *@return true if the mime type is indexable by this connector.
  */
  @Override
  public boolean checkMimeTypeIndexable(VersionContext outputDescription, String mimeType, IOutputCheckActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    return activities.checkMimeTypeIndexable(mimeType);
  }

  @Override
  public boolean checkLengthIndexable(VersionContext outputDescription, long length, IOutputCheckActivity activities)
    throws ManifoldCFException, ServiceInterruption {
    final SpecPacker sp = new SpecPacker(outputDescription.getSpecification());
    return activities.checkLengthIndexable(Math.min(length, sp.lengthCutoff));
  }
  
  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  *@return the document status (accepted or permanently rejected).
  */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException
  {
    final SpecPacker sp = new SpecPacker(outputDescription.getSpecification());
    
    InputStream is = null;
    DestinationStorage ds = null;
    try {
      RepositoryDocument finalDocument;
      long length;
      long startTime = System.currentTimeMillis();
      String resultCode = "OK";
      String description = null;
      
      if(document.getBinaryLength() > sp.lengthCutoff) {
          
        if (document.getBinaryLength() <= inMemoryMaximumFile)
        {
          ds = new MemoryDestinationStorage((int)document.getBinaryLength());
        }
        else
        {
          ds = new FileDestinationStorage();
        }
        
        // Create a copy of Repository Document
        finalDocument = document.duplicate();
        
        InputStream docIs = document.getBinaryStream();
        try {
          IOUtils.copyLarge(docIs, ds.getOutputStream(), 0L, sp.lengthCutoff);
          
          // Get new stream length
          length = ds.getBinaryLength();
          is = ds.getInputStream();
          finalDocument.setBinary(is,length);
          resultCode = "TRUNCATEDOK";
        } catch(IOException e) {
          resultCode = "TRUNCATEDERROR";
          description = e.getMessage();
          return DOCUMENTSTATUS_REJECTED;
        } finally {
          docIs.close();
        }
      } else {
        finalDocument = document;
        length = document.getBinaryLength();
      }
      
      activities.recordActivity(new Long(startTime), ACTIVITY_LIMIT, length, documentURI,
                resultCode, description);
      return activities.sendDocument(documentURI, finalDocument);
    } finally {
      if(is != null) {
        is.close();
      }
      if(ds != null) {
        ds.close();
      }
    }
  }
  
  protected static void fillInContentSpecificationMap(Map<String,Object> paramMap, Specification os)
  {
    String maxContentLength = ContentLimiterConfig.MAXLENGTH_DEFAULT;
    for (int i = 0; i < os.getChildCount(); i++)
    {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(ContentLimiterConfig.NODE_MAXLENGTH)) {
        maxContentLength = sn.getAttributeValue(ContentLimiterConfig.ATTRIBUTE_VALUE);
      }
    }
    paramMap.put("MAXCONTENTLENGTH",maxContentLength);
  }
  
  /** Obtain the name of the form check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form check javascript method.
  */
  @Override
  public String getFormCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "s"+connectionSequenceNumber+"_checkSpecification";
  }

  /** Obtain the name of the form presave check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form presave check javascript method.
  */
  @Override
  public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "s"+connectionSequenceNumber+"_checkSpecificationForSave";
  }

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a pipeline connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this connection.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));

    tabsArray.add(Messages.getString(locale, "ContentLimiter.ContentTabName"));

    // Fill in the specification header map, using data from all tabs.
    fillInContentSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_JS,paramMap);
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a pipeline connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    // Set the tab name
    paramMap.put("TABNAME", tabName);
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));
    paramMap.put("SELECTEDNUM",Integer.toString(actualSequenceNumber));

    // Fill in the field mapping tab data
    fillInContentSpecificationMap(paramMap, os);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPECIFICATION_CONTENT_HTML,paramMap);
  }

  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the transformation specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the preferred local of the output.
  *@param os is the current pipeline specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException {
    String seqPrefix = "s"+connectionSequenceNumber+"_";
    
    String x;

    x = variableContext.getParameter(seqPrefix+"maxcontentlength");
    if (x != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(ContentLimiterConfig.NODE_MAXLENGTH))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode sn = new SpecificationNode(ContentLimiterConfig.NODE_MAXLENGTH);
      sn.setAttribute(ContentLimiterConfig.ATTRIBUTE_VALUE,x);
      os.addChild(os.getChildCount(),sn);
    }
    
    return null;
  }
  

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the pipeline specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the preferred local of the output.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param os is the current pipeline specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException
  {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));

    // Fill in the map with data from all tabs
    fillInContentSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,VIEW_SPECIFICATION_HTML,paramMap);
    
  }
  
  protected static Set<String> fillSet(String input) {
    Set<String> rval = new HashSet<String>();
    try
    {
      StringReader sr = new StringReader(input);
      BufferedReader br = new BufferedReader(sr);
      String line = null;
      while ((line = br.readLine()) != null)
      {
        line = line.trim();
        if (line.equals("*"))
          rval = null;
        else if (rval != null && line.length() > 0)
          rval.add(line.toLowerCase(Locale.ROOT));
      }
    }
    catch (IOException e)
    {
      // Should never happen
      throw new RuntimeException("IO exception reading strings: "+e.getMessage(),e);
    }
    return rval;
  }
  
  protected static int handleIOException(IOException e)
		    throws ManifoldCFException
  {
    // IOException reading from our local storage...
    if (e instanceof InterruptedIOException)
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    throw new ManifoldCFException(e.getMessage(),e);
  }
  
  protected static interface DestinationStorage
  {
    /** Get the output stream to write to.  Caller should explicitly close this stream when done writing.
    */
    public OutputStream getOutputStream()
      throws ManifoldCFException;
    
    /** Get new binary length.
    */
    public long getBinaryLength()
      throws ManifoldCFException;

    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
    */
    public InputStream getInputStream()
      throws ManifoldCFException;
    
    /** Close the object and clean up everything.
    * This should be called when the data is no longer needed.
    */
    public void close()
      throws ManifoldCFException;
  }
  
  protected static class FileDestinationStorage implements DestinationStorage
  {
    protected final File outputFile;
    protected final OutputStream outputStream;

    public FileDestinationStorage()
      throws ManifoldCFException
    {
      File outputFile;
      OutputStream outputStream;
      try
      {
        outputFile = File.createTempFile("mcftika","tmp");
        outputStream = new FileOutputStream(outputFile);
      }
      catch (IOException e)
      {
        handleIOException(e);
        outputFile = null;
        outputStream = null;
      }
      this.outputFile = outputFile;
      this.outputStream = outputStream;
    }
    
    @Override
    public OutputStream getOutputStream()
      throws ManifoldCFException
    {
      return outputStream;
    }
    
    /** Get new binary length.
    */
    @Override
    public long getBinaryLength()
      throws ManifoldCFException
    {
      return outputFile.length();
    }

    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
    */
    @Override
    public InputStream getInputStream()
      throws ManifoldCFException
    {
      try
      {
        return new FileInputStream(outputFile);
      }
      catch (IOException e)
      {
        handleIOException(e);
        return null;
      }
    }
    
    /** Close the object and clean up everything.
    * This should be called when the data is no longer needed.
    */
    @Override
    public void close()
      throws ManifoldCFException
    {
      outputFile.delete();
    }

  }
  
  protected static class MemoryDestinationStorage implements DestinationStorage
  {
    protected final ByteArrayOutputStream outputStream;
    
    public MemoryDestinationStorage(int sizeHint)
    {
      outputStream = new ByteArrayOutputStream(sizeHint);
    }
    
    @Override
    public OutputStream getOutputStream()
      throws ManifoldCFException
    {
      return outputStream;
    }

    /** Get new binary length.
    */
    @Override
    public long getBinaryLength()
      throws ManifoldCFException
    {
      return outputStream.size();
    }
    
    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
    */
    @Override
    public InputStream getInputStream()
      throws ManifoldCFException
    {
      return new ByteArrayInputStream(outputStream.toByteArray());
    }
    
    /** Close the object and clean up everything.
    * This should be called when the data is no longer needed.
    */
    public void close()
      throws ManifoldCFException
    {
    }

  }
  
  protected static class SpecPacker {
    
    
    private Long lengthCutoff;
    
    public SpecPacker(Specification os) {
      for (int i = 0; i < os.getChildCount(); i++) {
        SpecificationNode sn = os.getChild(i);
        
        if (sn.getType().equals(ContentLimiterConfig.NODE_MAXLENGTH)) {
          String value = sn.getAttributeValue(ContentLimiterConfig.ATTRIBUTE_VALUE);
          lengthCutoff = new Long(value);
        }
      }
    }
    
    public String toPackedString() {
      StringBuilder sb = new StringBuilder();
      
      // Max length
      if (lengthCutoff == null)
        sb.append('-');
      else {
        sb.append('+');
        pack(sb,lengthCutoff.toString(),'+');
      }

      return sb.toString();
    }
    
  }
  
}
