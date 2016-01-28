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
package org.apache.manifoldcf.agents.transformation.opennlp;

import java.io.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputCheckActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.manifoldcf.agents.system.ManifoldCF;
import org.apache.manifoldcf.agents.transformation.BaseTransformationConnector;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;

public class OpenNlpExtractor extends BaseTransformationConnector {
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  private static final String EDIT_SPECIFICATION_OPENNLP_HTML = "editSpecification_OpenNLP.html";
  private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";

  protected static int maximumExtractionCharacters = 524288;
  
  // Meta-data fields added by this connector
  private static final String PERSONS = "ner_people";
  private static final String LOCATIONS = "ner_locations";
  private static final String ORGANIZATIONS = "ner_organizations";

  protected static final String ACTIVITY_EXTRACT = "extract";

  protected static final String[] activitiesList = new String[] { ACTIVITY_EXTRACT };

  protected final File fileDirectory = ManifoldCF.getFileProperty(ManifoldCF.fileResourcesProperty);

  /** We handle up to 64K in memory; after that we go to disk. */
  protected static final long inMemoryMaximumFile = 65536;

  
  /**
   * Return a list of activities that this connector generates. The connector
   * does NOT need to be connected before this method is called.
   * 
   * @return the set of activities.
   */
  @Override
  public String[] getActivitiesList() {
    return activitiesList;
  }

  /**
   * Get a pipeline version string, given a pipeline specification object. The
   * version string is used to uniquely describe the pertinent details of the
   * specification and the configuration, to allow the Connector Framework to
   * determine whether a document will need to be processed again. Note that
   * the contents of any document cannot be considered by this method; only
   * configuration and specification information can be considered.
   * 
   * This method presumes that the underlying connector object has been
   * configured.
   * 
   * @param spec
   *            is the current pipeline specification object for this
   *            connection for the job that is doing the crawling.
   * @return a string, of unlimited length, which uniquely describes
   *         configuration and specification in such a way that if two such
   *         strings are equal, nothing that affects how or whether the
   *         document is indexed will be different.
   */
  @Override
  public VersionContext getPipelineDescription(Specification os) throws ManifoldCFException, ServiceInterruption {
    SpecPacker sp = new SpecPacker(os);
    return new VersionContext(sp.toPackedString(), params, os);
  }

  /**
   * Add (or replace) a document in the output data store using the connector.
   * This method presumes that the connector object has been configured, and
   * it is thus able to communicate with the output data store should that be
   * necessary. The OutputSpecification is *not* provided to this method,
   * because the goal is consistency, and if output is done it must be
   * consistent with the output description, since that was what was partly
   * used to determine if output should be taking place. So it may be
   * necessary for this method to decode an output description string in order
   * to determine what should be done.
   * 
   * @param documentURI
   *            is the URI of the document. The URI is presumed to be the
   *            unique identifier which the output data store will use to
   *            process and serve the document. This URI is constructed by the
   *            repository connector which fetches the document, and is thus
   *            universal across all output connectors.
   * @param outputDescription
   *            is the description string that was constructed for this
   *            document by the getOutputDescription() method.
   * @param document
   *            is the document data to be processed (handed to the output
   *            data store).
   * @param authorityNameString
   *            is the name of the authority responsible for authorizing any
   *            access tokens passed in with the repository document. May be
   *            null.
   * @param activities
   *            is the handle to an object that the implementer of a pipeline
   *            connector may use to perform operations, such as logging
   *            processing activity, or sending a modified document to the
   *            next stage in the pipeline.
   * @return the document status (accepted or permanently rejected).
   * @throws IOException
   *             only if there's a stream error reading the document data.
   */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription,
    RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException {
    // assumes use of Tika extractor before using this connector
    Logging.agents.debug("Starting OpenNlp extraction");

    SpecPacker sp = new SpecPacker(pipelineDescription.getSpecification());

    // In order to be able to replay the input stream both for extraction and for downstream use,
    // we need to page through it, some number of characters at a time, and write those into a local buffer.
    // We can do this at the same time we're extracting, if we're clever.
      
    // Set up to spool back the original content, using either memory or disk, whichever makes sense.
    DestinationStorage ds;
    if (document.getBinaryLength() <= inMemoryMaximumFile) {
      ds = new MemoryDestinationStorage((int)document.getBinaryLength());
    } else {
      ds = new FileDestinationStorage();
    }
    
    try {

      // For logging, we'll need all of this
      long startTime = System.currentTimeMillis();
      String resultCode = "OK";
      String description = null;
      Long length = null;

      final MetadataAccumulator ma = new MetadataAccumulator(sp, document.getBinaryLength());
      
      try {

        // Page through document content, saving it aside into destination storage, while also extracting the content
        final OutputStream os = ds.getOutputStream();
        try {
          // We presume that the content is utf-8!!  Thus it has to have been run through the TikaExtractor, or equivalent.
          //
          // We're going to be paging through the input stream by chunks of characters.  Each chunk will then be passed to the
          // output stream (os) via a writer, as well as to the actual code that invokes the nlp sentence extraction.  
          
          // We need an output writer that converts the input into characters.  
          // 
          Writer w = new OutputStreamWriter(os, "utf-8");
          try {
            Reader r = new InputStreamReader(document.getBinaryStream(), "utf-8");
            try {
              // Now, page through!
              // It's too bad we have to convert FROM utf-8 and then back TO utf-8, but that can't be helped.
              char[] characterBuffer = new char[65536];
              while (true) {
                int amt = r.read(characterBuffer);
                if (amt == -1) {
                  break;
                }
                // Write into the copy buffer
                w.write(characterBuffer,0,amt);
                // Also do the processing
                ma.acceptCharacters(characterBuffer,amt);
              }
              // Do not close the reader; the underlying stream will be closed by our caller when the RepositoryDocument is done with
            } catch (IOException e) {
              // These are errors from reading the RepositoryDocument input stream; we handle them accordingly.
              resultCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
              description = e.getMessage();
              throw e;
            }
          } finally {
            w.flush();
          }
        }
        finally
        {
          os.close();
          length = new Long(ds.getBinaryLength());
        }

      }
      finally
      {
        // Log the extraction processing
        activities.recordActivity(new Long(startTime), ACTIVITY_EXTRACT, length, documentURI,
          resultCode, description);
      }
      
      ma.done();
      
      // Parsing complete!
      // Create a copy of Repository Document
      RepositoryDocument docCopy = document.duplicate();
        
      // Get new stream length
      long newBinaryLength = ds.getBinaryLength();
      // Open new input stream
      InputStream is = ds.getInputStream();
      try
      {
        docCopy.setBinary(is,newBinaryLength);

        // add named entity meta-data
        Map<String,Set<String>> nerMap = ma.getMetadata();
        if (!nerMap.isEmpty()) {
          for (Entry<String, Set<String>> entry : nerMap.entrySet()) {
            Set<String> neList = entry.getValue();
            String[] neArray = neList.toArray(new String[0]);
            docCopy.addField(entry.getKey(), neArray);
          }
        }

        // Send new document downstream
        return activities.sendDocument(documentURI,docCopy);
      } finally {
        is.close();
      }
    } finally {
      ds.close();
    }
  }

  private final static Set<String> acceptableMimeTypes = new HashSet<String>();
  static
  {
    acceptableMimeTypes.add("text/plain;charset=utf-8");
    acceptableMimeTypes.add("text/plain;charset=ascii");
    acceptableMimeTypes.add("text/plain;charset=us-ascii");
    acceptableMimeTypes.add("text/plain");
  }

  /** Detect if a mime type is acceptable or not.  This method is used to determine whether it makes sense to fetch a document
  * in the first place.
  *@param pipelineDescription is the document's pipeline version string, for this connection.
  *@param mimeType is the mime type of the document.
  *@param checkActivity is an object including the activities that can be performed by this method.
  *@return true if the mime type can be accepted by this connector.
  */
  @Override
  public boolean checkMimeTypeIndexable(VersionContext pipelineDescription, String mimeType, IOutputCheckActivity checkActivity)
    throws ManifoldCFException, ServiceInterruption
  {
    if (mimeType == null || !acceptableMimeTypes.contains(mimeType.toLowerCase(Locale.ROOT))) {
      return false;
    }
    // Do a downstream check too
    return super.checkMimeTypeIndexable(pipelineDescription, mimeType, checkActivity);
  }

  // ////////////////////////
  // UI Methods
  // ////////////////////////

  /**
   * Obtain the name of the form check javascript method to call.
   * 
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @return the name of the form check javascript method.
   */
  @Override
  public String getFormCheckJavascriptMethodName(int connectionSequenceNumber) {
    return "s" + connectionSequenceNumber + "_checkSpecification";
  }

  /**
   * Obtain the name of the form presave check javascript method to call.
   * 
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @return the name of the form presave check javascript method.
   */
  @Override
  public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber) {
    return "s" + connectionSequenceNumber + "_checkSpecificationForSave";
  }

  /**
   * Output the specification header section. This method is called in the
   * head section of a job page which has selected an output connection of the
   * current type. Its purpose is to add the required tabs to the list, and to
   * output any javascript methods that might be needed by the job editing
   * HTML.
   * 
   * @param out
   *            is the output to which any HTML should be sent.
   * @param locale
   *            is the preferred local of the output.
   * @param os
   *            is the current output specification for this job.
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @param tabsArray
   *            is an array of tab names. Add to this array any tab names that
   *            are specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
      int connectionSequenceNumber, List<String> tabsArray) throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

    tabsArray.add(Messages.getString(locale, "OpenNlpExtractor.OpenNLPTabName"));

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS, paramMap);
  }

  /**
   * Output the specification body section. This method is called in the body
   * section of a job page which has selected an output connection of the
   * current type. Its purpose is to present the required form elements for
   * editing. The coder can presume that the HTML that is output from this
   * configuration will be within appropriate <html>, <body>, and <form> tags.
   * The name of the form is "editjob".
   * 
   * @param out
   *            is the output to which any HTML should be sent.
   * @param locale
   *            is the preferred local of the output.
   * @param os
   *            is the current output specification for this job.
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @param actualSequenceNumber
   *            is the connection within the job that has currently been
   *            selected.
   * @param tabName
   *            is the current tab name.
   */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os, int connectionSequenceNumber,
      int actualSequenceNumber, String tabName) throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();

    paramMap.put("TABNAME", tabName);
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));
    paramMap.put("SELECTEDNUM", Integer.toString(actualSequenceNumber));

    fillInOpenNLPSpecificationMap(paramMap, os);
    setUpOpenNLPSpecificationMap(paramMap);

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_OPENNLP_HTML, paramMap);
  }

  /**
   * Process a specification post. This method is called at the start of job's
   * edit or view page, whenever there is a possibility that form data for a
   * connection has been posted. Its purpose is to gather form information and
   * modify the output specification accordingly. The name of the posted form
   * is "editjob".
   * 
   * @param variableContext
   *            contains the post data, including binary file-upload
   *            information.
   * @param locale
   *            is the preferred local of the output.
   * @param os
   *            is the current output specification for this job.
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @return null if all is well, or a string error message if there is an
   *         error that should prevent saving of the job (and cause a
   *         redirection to an error page).
   */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
      int connectionSequenceNumber) throws ManifoldCFException {
    String seqPrefix = "s" + connectionSequenceNumber + "_";

    SpecificationNode node = new SpecificationNode(OpenNlpExtractorConfig.NODE_SMODEL_PATH);
    String smodelPath = variableContext.getParameter(seqPrefix + "smodelpath");
    if (smodelPath != null) {
      node.setAttribute(OpenNlpExtractorConfig.ATTRIBUTE_VALUE, smodelPath);
    } else {
      node.setAttribute(OpenNlpExtractorConfig.ATTRIBUTE_VALUE, "");
    }
    os.addChild(os.getChildCount(), node);

    node = new SpecificationNode(OpenNlpExtractorConfig.NODE_TMODEL_PATH);
    String tmodelPath = variableContext.getParameter(seqPrefix + "tmodelpath");
    if (tmodelPath != null) {
      node.setAttribute(OpenNlpExtractorConfig.ATTRIBUTE_VALUE, tmodelPath);
    } else {
      node.setAttribute(OpenNlpExtractorConfig.ATTRIBUTE_VALUE, "");
    }
    os.addChild(os.getChildCount(), node);

    String modelCount = variableContext.getParameter(seqPrefix+"model_count");
    if (modelCount != null)
    {
      int count = Integer.parseInt(modelCount);
      // Delete old spec data, including legacy node types we no longer use
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode cn = os.getChild(i);
        if (cn.getType().equals(OpenNlpExtractorConfig.NODE_FINDERMODEL))
          os.removeChild(i);
        else
          i++;
      }

      // Now, go through form data
      for (int j = 0; j < count; j++)
      {
        String op = variableContext.getParameter(seqPrefix+"model_"+j+"_op");
        if (op != null && op.equals("Delete"))
          continue;
        String paramName = variableContext.getParameter(seqPrefix+"model_"+j+"_parametername");
        String modelFile = variableContext.getParameter(seqPrefix+"model_"+j+"_modelfile");
        SpecificationNode sn = new SpecificationNode(OpenNlpExtractorConfig.NODE_FINDERMODEL);
        sn.setAttribute(OpenNlpExtractorConfig.ATTRIBUTE_PARAMETERNAME,paramName);
        sn.setAttribute(OpenNlpExtractorConfig.ATTRIBUTE_MODELFILE,modelFile);
        os.addChild(os.getChildCount(),sn);
      }
      // Look for add operation
      String addOp = variableContext.getParameter(seqPrefix+"model_op");
      if (addOp != null && addOp.equals("Add"))
      {
        String paramName = variableContext.getParameter(seqPrefix+"model_parametername");
        String modelFile = variableContext.getParameter(seqPrefix+"model_modelfile");
        SpecificationNode sn = new SpecificationNode(OpenNlpExtractorConfig.NODE_FINDERMODEL);
        sn.setAttribute(OpenNlpExtractorConfig.ATTRIBUTE_PARAMETERNAME,paramName);
        sn.setAttribute(OpenNlpExtractorConfig.ATTRIBUTE_MODELFILE,modelFile);
        os.addChild(os.getChildCount(),sn);
      }

    }

    return null;
  }

  /**
   * View specification. This method is called in the body section of a job's
   * view page. Its purpose is to present the output specification information
   * to the user. The coder can presume that the HTML that is output from this
   * configuration will be within appropriate <html> and <body> tags.
   * 
   * @param out
   *            is the output to which any HTML should be sent.
   * @param locale
   *            is the preferred local of the output.
   * @param connectionSequenceNumber
   *            is the unique number of this connection within the job.
   * @param os
   *            is the current output specification for this job.
   */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification os, int connectionSequenceNumber)
      throws ManifoldCFException, IOException {
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

    fillInOpenNLPSpecificationMap(paramMap, os);
    Messages.outputResourceWithVelocity(out, locale, VIEW_SPECIFICATION_HTML, paramMap);
  }

  protected void setUpOpenNLPSpecificationMap(Map<String, Object> paramMap)
    throws ManifoldCFException {
    final String[] fileNames = getModelList();
    paramMap.put("FILENAMES", fileNames);
  }
  
  protected static void fillInOpenNLPSpecificationMap(Map<String, Object> paramMap, Specification os) {
    String sModelPath = "";
    String tModelPath = "";
    final List<Map<String,String>> finderModels = new ArrayList<>();
    
    for (int i = 0; i < os.getChildCount(); i++) {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(OpenNlpExtractorConfig.NODE_SMODEL_PATH)) {
        sModelPath = sn.getAttributeValue(OpenNlpExtractorConfig.ATTRIBUTE_VALUE);
        if (sModelPath == null) {
          sModelPath = "";
        }
      } else if (sn.getType().equals(OpenNlpExtractorConfig.NODE_TMODEL_PATH)) {
        tModelPath = sn.getAttributeValue(OpenNlpExtractorConfig.ATTRIBUTE_VALUE);
        if (tModelPath == null) {
          tModelPath = "";
        }
      } else if (sn.getType().equals(OpenNlpExtractorConfig.NODE_FINDERMODEL)) {
        final String parameterName = sn.getAttributeValue(OpenNlpExtractorConfig.ATTRIBUTE_PARAMETERNAME);
        final String modelFile = sn.getAttributeValue(OpenNlpExtractorConfig.ATTRIBUTE_MODELFILE);
        final Map<String,String> modelRecord = new HashMap<>();
        modelRecord.put("parametername", parameterName);
        modelRecord.put("modelfile", modelFile);
        finderModels.add(modelRecord);
      }

    }
    paramMap.put("SMODELPATH", sModelPath);
    paramMap.put("TMODELPATH", tModelPath);
    paramMap.put("MODELS", finderModels);
  }

  protected static int handleIOException(IOException e)
    throws ManifoldCFException
  {
    // IOException reading from our local storage...
    if (e instanceof InterruptedIOException)
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    throw new ManifoldCFException(e.getMessage(),e);
  }

  protected String[] getModelList() throws ManifoldCFException {
    if (fileDirectory == null) {
      return new String[0];
    }
    final String[] files = fileDirectory.list(new FileFilter());
    // Sort it!!
    java.util.Arrays.sort(files);
    return files;
  }
  
  protected static class FileFilter implements FilenameFilter {
    @Override
    public boolean accept(final File dir, final String name) {
      return new File(dir, name).isFile();
    }
  }
  
  /** An instance of this class receives characters in 64K chunks, and needs to accumulate
  * extracted metadata that this transformer will pass down.
  */
  protected class MetadataAccumulator {

    char[] characterBuffer = null;
    int bufferPointer = 0;
    
    final int bufferSize;
    
    final SentenceDetector sentenceDetector;
    final Tokenizer tokenizer;
    final Map<String,NameFinderME> finders = new HashMap<>();
    final Map<String,Set<String>> tokenLists = new HashMap<>();
   
    public MetadataAccumulator(final SpecPacker sp,
      final long bytesize)
      throws ManifoldCFException {
      try {
        sentenceDetector = OpenNlpExtractorConfig.sentenceDetector(new File(fileDirectory,sp.getSModelPath()));
        tokenizer = OpenNlpExtractorConfig.tokenizer(new File(fileDirectory,sp.getTModelPath()));
        final Map<String,String> finderFiles = sp.getFinderModels();
        for (String paramName : finderFiles.keySet()) {
          finders.put(paramName, OpenNlpExtractorConfig.finder(new File(fileDirectory,finderFiles.get(paramName))));
        }
      } catch (IOException e) {
        throw new ManifoldCFException(e.getMessage(), e);
      }
      if (bytesize > maximumExtractionCharacters) {
        bufferSize = maximumExtractionCharacters;
      } else {
        bufferSize = (int)bytesize;
      }
    }
    
    /** Accept characters, including actual count.
    */
    public void acceptCharacters(final char[] buffer, int amt) {
      if (characterBuffer == null) {
        characterBuffer = new char[bufferSize];
      }
      int copyAmt;
      if (amt > bufferSize - bufferPointer) {
        copyAmt = bufferSize - bufferPointer;
      } else {
        copyAmt = amt;
      }
      int sourcePtr = 0;
      while (copyAmt > 0) {
        characterBuffer[bufferPointer++] = buffer[sourcePtr++];
        copyAmt--;
      }
    }

    public void done() {
      if (bufferPointer == 0 || characterBuffer == null) {
        return;
      }
      
      // Make a string freom the character array
      final String textContent = new String(characterBuffer, 0, bufferPointer);

      // Break into sentences, tokens, and then people, locations, and organizations
      String[] sentences = sentenceDetector.sentDetect(textContent);
      for (String sentence : sentences) {
        String[] tokens = tokenizer.tokenize(sentence);

        for (String parameterName : finders.keySet()) {
          Set<String> stringSet = tokenLists.get(parameterName);
          if (stringSet == null) {
            stringSet = new HashSet<String>();
            tokenLists.put(parameterName, stringSet);
          }
          
          Span[] spans = finders.get(parameterName).find(tokens);
          stringSet.addAll(Arrays.asList(Span.spansToStrings(spans, tokens)));
        }
      }
    }
    
    public Map<String,Set<String>> getMetadata() {
      return tokenLists;
    }
    
  }
  
  protected static interface DestinationStorage {
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
  
  protected static class FileDestinationStorage implements DestinationStorage {
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
  
  protected static class MemoryDestinationStorage implements DestinationStorage {
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

    private final String sModelPath;
    private final String tModelPath;
    private final Map<String, String> models = new TreeMap<>();

    public SpecPacker(Specification os) {
      String sModelPath = null;
      String tModelPath = null;

      for (int i = 0; i < os.getChildCount(); i++) {
        SpecificationNode sn = os.getChild(i);

        if (sn.getType().equals(OpenNlpExtractorConfig.NODE_SMODEL_PATH)) {
          sModelPath = sn.getAttributeValue(OpenNlpExtractorConfig.ATTRIBUTE_VALUE);
        }
        if (sn.getType().equals(OpenNlpExtractorConfig.NODE_TMODEL_PATH)) {
          tModelPath = sn.getAttributeValue(OpenNlpExtractorConfig.ATTRIBUTE_VALUE);
        }
        if (sn.getType().equals(OpenNlpExtractorConfig.NODE_FINDERMODEL)) {
          final String parameterName = sn.getAttributeValue(OpenNlpExtractorConfig.ATTRIBUTE_PARAMETERNAME);
          final String modelFile = sn.getAttributeValue(OpenNlpExtractorConfig.ATTRIBUTE_MODELFILE);
          models.put(parameterName, modelFile);
        }

      }
      this.sModelPath = sModelPath;
      this.tModelPath = tModelPath;
    }

    public String toPackedString() {
      StringBuilder sb = new StringBuilder();

      // extract nouns
      if (sModelPath != null)
        sb.append(sModelPath);
      sb.append(",");
      if (tModelPath != null)
        sb.append(tModelPath);
      sb.append("[");
      for (String parameterName : models.keySet()) {
        sb.append(parameterName).append("=").append(models.get(parameterName)).append(",");
      }

      return sb.toString();
    }

    public String getSModelPath() {
      return sModelPath;
    }

    public String getTModelPath() {
      return tModelPath;
    }

    public Map<String, String> getFinderModels() {
      return models;
    }

  }

}
