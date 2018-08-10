package org.apache.manifoldcf.agents.transformation.htmlextractor;

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

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;


import org.apache.manifoldcf.agents.transformation.htmlextractor.exception.RegexException;

import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class HtmlExtractor extends org.apache.manifoldcf.agents.transformation.BaseTransformationConnector
{

  public static final String _rcsid = "@(#)$Id$";

  protected static final String ACTIVITY_PROCESS = "process";

  protected static final String[] activitiesList = new String[]{ACTIVITY_PROCESS};

  /**
   * Forward to the javascript to check the specification parameters for the job
   */
  private static final String EDIT_CONFIGURATION_JS = "editConfiguration.js";

  private static final String VIEW_CONFIGURATION_HTML = "viewConfiguration.html";
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";
  private static final String EDIT_SPECIFICATION_HTML_EXTRACTOR_HTML = "editSpecification_HTML_Extractor.html";



  protected static final int HTML_STRIP_NONE = 0;
  protected static final int HTML_STRIP_ALL = 1;

  protected static int html_strip_usage = HTML_STRIP_ALL;

  public static final String NODE_KEEPMETADATA = "striphtml";
  public static final String NODE_FILTEREMPTY = "filterEmpty";
  public static final String ATTRIBUTE_SOURCE = "source";
  public static final String ATTRIBUTE_TARGET = "target";
  public static final String ATTRIBUTE_VALUE = "value";

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
   *@param activities is the handle to an object that the implementer of a pipeline connector may use to perform operations, such as logging processing activity,
   * or sending a modified document to the next stage in the pipeline.
   *@return the document status (accepted or permanently rejected).
   *@throws IOException only if there's a stream error reading the document data.
   */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
      throws ManifoldCFException, ServiceInterruption, IOException
  {
    long startTime = System.currentTimeMillis();
    String resultCode = "OK";
    String description = null;
    Long length = null;

    final SpecPacker sp = new SpecPacker(pipelineDescription.getSpecification());


    Logging.connectors.debug("Processing by HTML Extractor");
    if (!(document.getMimeType().startsWith("text/html")) || (document.getMimeType().startsWith("application/xhtml+xml"))){
      Logging.connectors.debug("no processing, mime type not html");
      resultCode = "NO HTML";

    }

    else {
      try
      {
        Logging.connectors.debug("Document recognized as HTML - processing");
        long binaryLength = document.getBinaryLength();


        length =  new Long(binaryLength);

        /*
        DestinationStorage ds;
        if (document.getBinaryLength() <= inMemoryMaximumFile)
        {
          ds = new MemoryDestinationStorage((int)document.getBinaryLength());
        }
        else
        {
          ds = new FileDestinationStorage();
        }
        try
            {
              OutputStream os = ds.getOutputStream();
         */


        //TODO
        /* Add an option to keep HTML markup of the extracted text or not - 
         * in case for example of processing by Tika after this transformation connector
         * 
         */
        Hashtable<String,String> metadataExtracted = new Hashtable<String,String>();
        
        metadataExtracted = JsoupProcessing.extractTextAndMetadataHtmlDocument(document.getBinaryStream(),sp.includeFilters.get(0), sp.excludeFilters, sp.striphtml);
        InputStream newStream = new ByteArrayInputStream(metadataExtracted.get("extractedDoc").getBytes(StandardCharsets.UTF_8));
        int lenghtNewStream = newStream.available();
        document.setBinary(newStream, lenghtNewStream);
        Iterator<Entry<String, String>> it;
        Map.Entry<String,String> entry;

        it = metadataExtracted.entrySet().iterator();
        while (it.hasNext()) {
          entry = it.next();
          if (entry.getKey()!="extractedDoc")
            document.addField("jsoup_"+entry.getKey(), entry.getValue());

        }

        return activities.sendDocument(documentURI,document);
      }
      catch (ServiceInterruption e)
      {
        resultCode = "SERVICEINTERRUPTION";
        description = e.getMessage();
        throw e;
      }
      catch (ManifoldCFException e)
      {
        resultCode = "EXCEPTION";
        description = e.getMessage();
        throw e;
      }
      catch (IOException e)
      {
        resultCode = "IOEXCEPTION";
        description = e.getMessage();
        throw e;
      }

      catch (Exception e)
      {

        resultCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        description = e.getMessage();
      }
      finally
      {
        activities.recordActivity(new Long(startTime), ACTIVITY_PROCESS, length, documentURI,
            resultCode, description);
      }


    }

    return activities.sendDocument(documentURI,document);
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

    private void handleIOException(IOException e) {
      // TODO Auto-generated method stub

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
    protected static int handleIOException(IOException e)
        throws ManifoldCFException
    {
      // IOException reading from our local storage...
      if (e instanceof InterruptedIOException)
        throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      throw new ManifoldCFException(e.getMessage(),e);
    }

  }
  /**
   * Test if there is at least one regular expression that match with the
   * provided sting
   *
   * @param regexList
   *          the list of regular expressions
   * @param str
   *          the string to test
   * @return the first matching regex found or null if no matching regex
   */
  private String matchingRegex(final List<String> regexList, final String str) throws RegexException {
    for (final String regex : regexList) {
      try {
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
          return regex;
        }
      } catch (final PatternSyntaxException e) {
        throw new RegexException(regex, "Invalid regular expression");
      }
    }
    return null;
  }







  /**
   * Output the configuration header section. This method is called in the head
   * section of the connector's configuration page. Its purpose is to add the
   * required tabs to the list, and to output any javascript methods that might
   * be needed by the configuration editing HTML.
   *
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @param tabsArray
   *          is an array of tab names. Add to this array any tab names that are
   *          specific to the connector.
   */
  @Override
  public void outputConfigurationHeader(final IThreadContext threadContext, final IHTTPOutput out, final Locale locale,
      final ConfigParams parameters, final List<String> tabsArray) throws ManifoldCFException, IOException {

    Messages.outputResourceWithVelocity(out, locale, EDIT_CONFIGURATION_JS, null);
  }

  /**
   * Output the configuration body section. This method is called in the body
   * section of the connector's configuration page. Its purpose is to present
   * the required form elements for editing. The coder can presume that the HTML
   * that is output from this configuration will be within appropriate <html>,
   * <body>, and <form> tags. The name of the form is "editconnection".
   *
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @param tabName
   *          is the current tab name.
   */
  @Override
  public void outputConfigurationBody(final IThreadContext threadContext, final IHTTPOutput out, final Locale locale,
      final ConfigParams parameters, final String tabName) throws ManifoldCFException, IOException {
    final Map<String, Object> velocityContext = new HashMap<>();
    velocityContext.put("TabName", tabName);

  }

  /**
   * Process a configuration post. This method is called at the start of the
   * connector's configuration page, whenever there is a possibility that form
   * data for a connection has been posted. Its purpose is to gather form
   * information and modify the configuration parameters accordingly. The name
   * of the posted form is "editconnection".
   *
   * @param threadContext
   *          is the local thread context.
   * @param variableContext
   *          is the set of variables available from the post, including binary
   *          file post information.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   * @return null if all is well, or a string error message if there is an error
   *         that should prevent saving of the connection (and cause a
   *         redirection to an error page).
   */
  @Override
  public String processConfigurationPost(final IThreadContext threadContext, final IPostParameters variableContext,
      final Locale locale, final ConfigParams parameters) throws ManifoldCFException {


    return null;
  }

  /**
   * View configuration. This method is called in the body section of the
   * connector's view configuration page. Its purpose is to present the
   * connection information to the user. The coder can presume that the HTML
   * that is output from this configuration will be within appropriate <html>
   * and <body> tags.
   *
   * @param threadContext
   *          is the local thread context.
   * @param out
   *          is the output to which any HTML should be sent.
   * @param parameters
   *          are the configuration parameters, as they currently exist, for
   *          this connection being configured.
   */
  @Override
  public void viewConfiguration(final IThreadContext threadContext, final IHTTPOutput out, final Locale locale,
      final ConfigParams parameters) throws ManifoldCFException, IOException {
    final Map<String, Object> velocityContext = new HashMap<>();
    Messages.outputResourceWithVelocity(out, locale, VIEW_CONFIGURATION_HTML, velocityContext);
  }

  protected static void fillInHtmlExtractorSpecification(final Map<String, Object> paramMap, final Specification os) {

    final List<String> includeFilters = new ArrayList<String>();
    final List<String> excludeFilters = new ArrayList<String>();


    String striphtmlValue = "true";


    // Fill in context


    for (int i = 0; i < os.getChildCount(); i++) {
      final SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(HtmlExtractorConfig.NODE_INCLUDEFILTER)) {
        final String includeFilter = sn.getAttributeValue(HtmlExtractorConfig.ATTRIBUTE_REGEX);
        if (includeFilter != null) {
          includeFilters.add(includeFilter);
        }
      } else if (sn.getType().equals(HtmlExtractorConfig.NODE_EXCLUDEFILTER)) {
        final String excludeFilter = sn.getAttributeValue(HtmlExtractorConfig.ATTRIBUTE_REGEX);
        if (excludeFilter != null) {
          excludeFilters.add(excludeFilter);
        }


      } else if (sn.getType().equals(NODE_KEEPMETADATA))
      {
        striphtmlValue = sn.getAttributeValue(ATTRIBUTE_VALUE);
      }

    
  }



  paramMap.put("INCLUDEFILTERS", includeFilters);
  paramMap.put("EXCLUDEFILTERS", excludeFilters);
  paramMap.put("HTMLTAGUSAGE", html_strip_usage);
  paramMap.put("STRIPHTML",striphtmlValue);

}

/**
 * Output the specification header section. This method is called in the head
 * section of a job page which has selected a pipeline connection of the
 * current type. Its purpose is to add the required tabs to the list, and to
 * output any javascript methods that might be needed by the job editing HTML.
 *
 * @param out
 *          is the output to which any HTML should be sent.
 * @param locale
 * @param os
 *          is the current pipeline specification for this connection.
 * @param connectionSequenceNumber
 *          is the unique number of this connection within the job.
 * @param tabsArray
 *          is an array of tab names. Add to this array any tab names that are
 *          specific to the connector.
 */
@Override
public void outputSpecificationHeader(final IHTTPOutput out, final Locale locale, final Specification os,
    final int connectionSequenceNumber, final List<String> tabsArray) throws ManifoldCFException, IOException {
  final Map<String, Object> paramMap = new HashMap<>();
  paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

  tabsArray.add(Messages.getString(locale, "HtmlExtractorTransformationConnector.HtmlExtractorTabName"));

  // Fill in the specification header map, using data from all tabs.
  fillInHtmlExtractorSpecification(paramMap, os);

  Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS, paramMap);
}

/**
 * Output the specification body section. This method is called in the body
 * section of a job page which has selected a pipeline connection of the
 * current type. Its purpose is to present the required form elements for
 * editing. The coder can presume that the HTML that is output from this
 * configuration will be within appropriate <html>, <body>, and <form> tags.
 * The name of the form is "editjob".
 *
 * @param out
 *          is the output to which any HTML should be sent.
 * @param locale
 *          is the preferred local of the output.
 * @param os
 *          is the current pipeline specification for this job.
 * @param connectionSequenceNumber
 *          is the unique number of this connection within the job.
 * @param actualSequenceNumber
 *          is the connection within the job that has currently been selected.
 * @param tabName
 *          is the current tab name.
 */
@Override
public void outputSpecificationBody(final IHTTPOutput out, final Locale locale, final Specification os,
    final int connectionSequenceNumber, final int actualSequenceNumber, final String tabName)
        throws ManifoldCFException, IOException {
  final Map<String, Object> paramMap = new HashMap<>();

  // Set the tab name
  paramMap.put("TABNAME", tabName);
  paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));
  paramMap.put("SELECTEDNUM", Integer.toString(actualSequenceNumber));

  // Fill in the field mapping tab data
  fillInHtmlExtractorSpecification(paramMap, os);

  Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_HTML_EXTRACTOR_HTML, paramMap);
}

/**
 * Process a specification post. This method is called at the start of job's
 * edit or view page, whenever there is a possibility that form data for a
 * connection has been posted. Its purpose is to gather form information and
 * modify the transformation specification accordingly. The name of the posted
 * form is "editjob".
 *
 * @param variableContext
 *          contains the post data, including binary file-upload information.
 * @param locale
 *          is the preferred local of the output.
 * @param os
 *          is the current pipeline specification for this job.
 * @param connectionSequenceNumber
 *          is the unique number of this connection within the job.
 * @return null if all is well, or a string error message if there is an error
 *         that should prevent saving of the job (and cause a redirection to
 *         an error page).
 */
@Override
public String processSpecificationPost(final IPostParameters variableContext, final Locale locale,
    final Specification os, final int connectionSequenceNumber) throws ManifoldCFException {


  final String seqPrefix = "s" + connectionSequenceNumber + "_";

  String x;

  // Include filters
  x = variableContext.getParameter(seqPrefix + "includefilter_count");
  if (x != null && x.length() > 0) {
    // About to gather the includefilter nodes, so get rid of the old ones.
    int i = 0;
    while (i < os.getChildCount()) {
      final SpecificationNode node = os.getChild(i);
      if (node.getType().equals(HtmlExtractorConfig.NODE_INCLUDEFILTER)) {
        os.removeChild(i);
      } else {
        i++;
      }
    }
    final int count = Integer.parseInt(x);
    i = 0;
    while (i < count) {
      final String prefix = seqPrefix + "includefilter_";
      final String suffix = "_" + Integer.toString(i);
      final String op = variableContext.getParameter(prefix + "op" + suffix);
      if (op == null || !op.equals("Delete")) {
        // Gather the includefilters etc.
        final String regex = variableContext.getParameter(prefix + HtmlExtractorConfig.ATTRIBUTE_REGEX + suffix);
        final SpecificationNode node = new SpecificationNode(HtmlExtractorConfig.NODE_INCLUDEFILTER);
        node.setAttribute(HtmlExtractorConfig.ATTRIBUTE_REGEX, regex);
        os.addChild(os.getChildCount(), node);
      }
      i++;
    }

    final String addop = variableContext.getParameter(seqPrefix + "includefilter_op");
    if (addop != null && addop.equals("Add")) {
      final String regex = variableContext.getParameter(seqPrefix + "includefilter_regex");
      final SpecificationNode node = new SpecificationNode(HtmlExtractorConfig.NODE_INCLUDEFILTER);
      node.setAttribute(HtmlExtractorConfig.ATTRIBUTE_REGEX, regex);
      os.addChild(os.getChildCount(), node);
    }
  }

  // Exclude filters
  x = variableContext.getParameter(seqPrefix + "excludefilter_count");
  if (x != null && x.length() > 0) {
    // About to gather the excludefilter nodes, so get rid of the old ones.
    int i = 0;
    while (i < os.getChildCount()) {
      final SpecificationNode node = os.getChild(i);
      if (node.getType().equals(HtmlExtractorConfig.NODE_EXCLUDEFILTER)) {
        os.removeChild(i);
      } else {
        i++;
      }
    }
    final int count = Integer.parseInt(x);
    i = 0;
    while (i < count) {
      final String prefix = seqPrefix + "excludefilter_";
      final String suffix = "_" + Integer.toString(i);
      final String op = variableContext.getParameter(prefix + "op" + suffix);
      if (op == null || !op.equals("Delete")) {
        // Gather the excludefilters etc.
        final String regex = variableContext.getParameter(prefix + HtmlExtractorConfig.ATTRIBUTE_REGEX + suffix);
        final SpecificationNode node = new SpecificationNode(HtmlExtractorConfig.NODE_EXCLUDEFILTER);
        node.setAttribute(HtmlExtractorConfig.ATTRIBUTE_REGEX, regex);
        os.addChild(os.getChildCount(), node);
      }
      i++;
    }

    final String addop = variableContext.getParameter(seqPrefix + "excludefilter_op");
    if (addop != null && addop.equals("Add")) {
      final String regex = variableContext.getParameter(seqPrefix + "excludefilter_regex");
      final SpecificationNode node = new SpecificationNode(HtmlExtractorConfig.NODE_EXCLUDEFILTER);
      node.setAttribute(HtmlExtractorConfig.ATTRIBUTE_REGEX, regex);
      os.addChild(os.getChildCount(), node);
    }
  }

  x = variableContext.getParameter(seqPrefix+"striphtml_present");
    if (x != null && x.length() > 0)
    {
      String keepAll = variableContext.getParameter(seqPrefix+"striphtml");
      if (keepAll == null)
        keepAll = "false";
      // About to gather the fieldmapping nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(NODE_KEEPMETADATA))
          os.removeChild(i);
        else
          i++;
      }

      // Gather the keep all metadata parameter to be the last one
      SpecificationNode node = new SpecificationNode(NODE_KEEPMETADATA);
      node.setAttribute(ATTRIBUTE_VALUE, keepAll);
      // Add the new striphtml config parameter 
      os.addChild(os.getChildCount(), node);
    }


  return null;
}

/**
 * View specification. This method is called in the body section of a job's
 * view page. Its purpose is to present the pipeline specification information
 * to the user. The coder can presume that the HTML that is output from this
 * configuration will be within appropriate <html> and <body> tags.
 *
 * @param out
 *          is the output to which any HTML should be sent.
 * @param locale
 *          is the preferred local of the output.
 * @param connectionSequenceNumber
 *          is the unique number of this connection within the job.
 * @param os
 *          is the current pipeline specification for this job.
 */
@Override
public void viewSpecification(final IHTTPOutput out, final Locale locale, final Specification os,
    final int connectionSequenceNumber) throws ManifoldCFException, IOException {
  final Map<String, Object> paramMap = new HashMap<>();
  paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

  // Fill in the map with data from all tabs
  fillInHtmlExtractorSpecification(paramMap, os);

  Messages.outputResourceWithVelocity(out, locale, VIEW_SPECIFICATION_HTML, paramMap);

}
protected static class SpecPacker {

  private final List<String> includeFilters = new ArrayList<>();
  private final List<String> excludeFilters = new ArrayList<>();
  private final boolean striphtml;

  public SpecPacker(final Specification os) {
    boolean striphtml = true;
    for (int i = 0; i < os.getChildCount(); i++) {
      final SpecificationNode sn = os.getChild(i);

      if (sn.getType().equals(HtmlExtractorConfig.NODE_INCLUDEFILTER)) {
        final String regex = sn.getAttributeValue(HtmlExtractorConfig.ATTRIBUTE_REGEX);
        includeFilters.add(regex);
      }

      if (sn.getType().equals(HtmlExtractorConfig.NODE_EXCLUDEFILTER)) {
        final String regex = sn.getAttributeValue(HtmlExtractorConfig.ATTRIBUTE_REGEX);
        excludeFilters.add(regex);
      }
      if(sn.getType().equals(NODE_KEEPMETADATA)) {
        String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
        striphtml = Boolean.parseBoolean(value);
      }

    }

    if (includeFilters.isEmpty()) {
      includeFilters.add(HtmlExtractorConfig.WHITELIST_DEFAULT);
    }

    this.striphtml = striphtml;
  }

  public String toPackedString() {
    final StringBuilder sb = new StringBuilder();

    packList(sb, includeFilters, '+');
    packList(sb, excludeFilters, '+');
    if (striphtml)
      sb.append('+');
    else
      sb.append('-');

    return sb.toString();
  }

}

}

