/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.manifoldcf.crawler.connectors.csv;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.interfaces.ISeedingActivity;

import java.io.*;
import java.util.*;

public class CSVConnector extends BaseRepositoryConnector {

  private static final Logger LOGGER = LogManager.getLogger(CSVConnector.class.getName());
  private static Level DOCPROCESSLEVEL = Level.forName("DOCPROCESS", 450);
  private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
  private static final String EDIT_SPECIFICATION_CSV_HTML = "editSpecification_CSV.html";
  private static final String VIEW_SPECIFICATION_CSV_HTML = "viewSpecification_CSV.html";

  protected final static String ACTIVITY_READ = "read";
  private static final String DOCUMENT_ID_SEPARATOR = ";;";

  /**
   * Constructor.
   */
  public CSVConnector() {
  }

  @Override
  public int getMaxDocumentRequest() {
    return 20;
  }

  @Override
  public int getConnectorModel() {
    return CSVConnector.MODEL_ADD_CHANGE_DELETE;
  }

  @Override
  public String[] getActivitiesList() {
    return new String[] { ACTIVITY_READ };
  }

  /**
   * For any given document, list the bins that it is a member of.
   */
  @Override
  public String[] getBinNames(final String documentIdentifier) {
    // Return the host name
    return new String[] { "CSV" };
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!
  /**
   * Connect. The configuration parameters are included.
   *
   * @param configParams are the configuration parameters for this connection. Note well: There are no exceptions allowed from this call, since it is expected to mainly establish connection
   *                     parameters.
   */
  @Override
  public void connect(final ConfigParams configParams) {
    super.connect(configParams);

  }

  @Override
  public void disconnect() throws ManifoldCFException {
    super.disconnect();
  }

  @Override
  public String check() throws ManifoldCFException {
    return super.check();
  }

  @Override
  public String addSeedDocuments(final ISeedingActivity activities, final Specification spec, final String lastSeedVersion, final long seedTime, final int jobMode)
      throws ManifoldCFException, ServiceInterruption {

    long startTime;
    if (lastSeedVersion == null) {
      startTime = 0L;
    } else {
      // Unpack seed time from seed version string
      startTime = Long.parseLong(lastSeedVersion);
    }

    final CSVSpecs csvSpecs = new CSVSpecs(spec);
    final Map<String, String[]> csvMap = csvSpecs.getCSVMap();
    for (final String csvPath : csvMap.keySet()) {
      try {
        final long numberOfLines = CSVUtils.getCSVLinesNumber(csvPath);
        for (long i = 1L; i < numberOfLines; i++) {
          final String documentId = getDocumentIdentifier(i, csvPath);
          activities.addSeedDocument(documentId);
        }
      } catch (final IOException e) {
        throw new ManifoldCFException("Could not read CSV file " + csvPath + " : " + e.getMessage(), e);
      }
    }

    return String.valueOf(seedTime);

  }

  @Override
  public void processDocuments(final String[] documentIdentifiers, final IExistingVersions statuses, final Specification spec, final IProcessActivity activities, final int jobMode,
      final boolean usesDefaultAuthority) throws ManifoldCFException, ServiceInterruption {

    // Check if we should abort
    activities.checkJobStillActive();

    final CSVSpecs csvSpecs = new CSVSpecs(spec);

    final long startFetchTime = System.currentTimeMillis();

    final Map<String, List<Long>> linesToReadPerDoc = new HashMap<>();

    for (final String documentIdentifier : documentIdentifiers) {
      LOGGER.log(DOCPROCESSLEVEL, "DOC_PROCESS_START|CSV|" + documentIdentifier);
      final String[] documentIdentifierArr = documentIdentifier.split(DOCUMENT_ID_SEPARATOR);
      final String lineToRead = documentIdentifierArr[0];
      final String docPath = documentIdentifierArr[1];
      if (linesToReadPerDoc.containsKey(docPath)) {
        linesToReadPerDoc.get(docPath).add(Long.parseLong(lineToRead));
      } else {
        final List<Long> linesToRead = new ArrayList<>();
        linesToRead.add(Long.parseLong(lineToRead));
        linesToReadPerDoc.put(docPath, linesToRead);
      }
    }

    for (final String docPath : linesToReadPerDoc.keySet()) {
      final String[] docLabels = csvSpecs.CSVMap.get(docPath);

      if (docLabels == null){
        for (final Long lineToRead : linesToReadPerDoc.get(docPath)){
          String documentIdentifier = getDocumentIdentifier(lineToRead, docPath);
          activities.deleteDocument(documentIdentifier);
        }
      } else {
        try {
          processToIngestDocument(csvSpecs ,docPath, linesToReadPerDoc, activities, startFetchTime);
        } catch (final IOException e) {
          String errorCode = "KO";
          String description = "Unable to read file " + docPath + " : " + e.getMessage();
          // Rebuild documentIdentifier (MCF id)
          final String documentIdentifier = getDocumentIdentifier(linesToReadPerDoc.get(docPath).get(0), docPath);
          activities.recordActivity(startFetchTime, ACTIVITY_READ, 0L, documentIdentifier, errorCode, description, null);
          LOGGER.error(description, e);
        }
      }
    }

  }

  private String getDocumentIdentifier(long lineToRead, String docPath) {
    return lineToRead + DOCUMENT_ID_SEPARATOR + docPath;
  }

  private void processToIngestDocument(final CSVSpecs csvSpecs, final String docPath, final Map<String,List<Long>> linesToReadPerDoc, final IProcessActivity activities, final long startFetchTime)
          throws ManifoldCFException, IOException, ServiceInterruption {
    final String[] docLabels = csvSpecs.CSVMap.get(docPath);
    final Long[] linesToRead = linesToReadPerDoc.get(docPath).toArray(new Long[0]);
    // Sort lines to read so we can sequentially read the file
    Arrays.sort(linesToRead);
    final File csvFile = new File(docPath);
    long cptLine = 0;
    // init cptLinesToRead
    int cptLinesToRead = 0;

    final String versionString = "";

    // init lineToRead
    long lineToRead = linesToRead[0];

    try (FileReader fr = new FileReader(csvFile); BufferedReader br = new BufferedReader(fr);) {
      String line = null;
      while ((line = br.readLine()) != null) {
        if (cptLine < lineToRead) {
          cptLine++;
          continue;
        }

        // Rebuild documentIdentifier (MCF id)
        final String documentIdentifier = getDocumentIdentifier(lineToRead, docPath);
        String ingestId = String.valueOf(lineToRead);
        final RepositoryDocument rd = new RepositoryDocument();
        byte[] contentBytes = null;
        final String[] values = line.split(csvSpecs.getSeparator());
        for (int i = 0; i < values.length; i++) {
          final String value = values[i];
          final String label = docLabels[i];
          if (label.contentEquals(csvSpecs.getContentColumnLabel())) {
            contentBytes = value.getBytes();
          } else {
            if (label.contentEquals(csvSpecs.getIdColumnLabel())) {
              ingestId = value;
            }
            rd.addField(label, value);
          }
        }

        if (versionString.length() == 0 || activities.checkDocumentNeedsReindexing(documentIdentifier, versionString)) {
          // Ingest document
          try (ByteArrayInputStream inputStream = new ByteArrayInputStream(contentBytes)) {
            rd.setBinary(inputStream, contentBytes.length);
            activities.ingestDocumentWithException(documentIdentifier, versionString, ingestId, rd);
            String errorCode = "OK";
            activities.recordActivity(startFetchTime, ACTIVITY_READ, (long) contentBytes.length, documentIdentifier, errorCode, StringUtils.EMPTY, null);
          } finally {
            LOGGER.log(DOCPROCESSLEVEL, "DOC_PROCESS_END|CSV|" + documentIdentifier);
          }
        }

        // We just read a line to read, so search for the next line to read
        cptLinesToRead++;
        // If there is still line to read, then set lineToRead with the new value, otherwise we read all wanted lines so we can close the stream;
        if (cptLinesToRead < linesToRead.length) {
          lineToRead = linesToRead[cptLinesToRead];
        } else {
          // We have read all the linesToRead so we can stop reading the stream
          break;
        }
        cptLine++;
      }
    }

  }

  @Override
  public String processSpecificationPost(final IPostParameters variableContext, final Locale locale, final Specification os, final int connectionSequenceNumber) throws ManifoldCFException {
    final String seqPrefix = "s" + connectionSequenceNumber + "_";

    String x;

    x = variableContext.getParameter(seqPrefix + "filepath_count");
    if (x != null && x.length() > 0) {
      // About to gather the filepath nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount()) {
        final SpecificationNode node = os.getChild(i);
        if (node.getType().equals(CSVConfig.NODE_FILEPATH)) {
          os.removeChild(i);
        } else {
          i++;
        }
      }
      final int count = Integer.parseInt(x);
      i = 0;
      while (i < count) {
        final String prefix = seqPrefix + "filepath_";
        final String suffix = "_" + Integer.toString(i);
        final String op = variableContext.getParameter(prefix + "op" + suffix);
        if (op == null || !op.equals("Delete")) {
          // Gather the includefilters etc.
          final String value = variableContext.getParameter(prefix + CSVConfig.ATTRIBUTE_VALUE + suffix);
          final SpecificationNode node = new SpecificationNode(CSVConfig.NODE_FILEPATH);
          node.setAttribute(CSVConfig.ATTRIBUTE_VALUE, value);
          os.addChild(os.getChildCount(), node);
        }
        i++;
      }

      final String addop = variableContext.getParameter(seqPrefix + "filepath_op");
      if (addop != null && addop.equals("Add")) {
        final String regex = variableContext.getParameter(seqPrefix + "filepath_value");
        final SpecificationNode node = new SpecificationNode(CSVConfig.NODE_FILEPATH);
        node.setAttribute(CSVConfig.ATTRIBUTE_VALUE, regex);
        os.addChild(os.getChildCount(), node);
      }
    }

    x = variableContext.getParameter(seqPrefix + CSVConfig.NODE_ID_COLUMN);
    if (x != null) {
      // Delete id column entry
      int i = 0;
      while (i < os.getChildCount()) {
        final SpecificationNode sn = os.getChild(i);
        if (sn.getType().equals(CSVConfig.NODE_ID_COLUMN)) {
          os.removeChild(i);
        } else {
          i++;
        }
      }
      if (x.length() > 0) {
        final SpecificationNode node = new SpecificationNode(CSVConfig.NODE_ID_COLUMN);
        node.setAttribute(CSVConfig.ATTRIBUTE_VALUE, x);
        os.addChild(os.getChildCount(), node);
      }
    }

    x = variableContext.getParameter(seqPrefix + CSVConfig.NODE_CONTENT_COLUMN);
    if (x != null) {
      // Delete content column entry
      int i = 0;
      while (i < os.getChildCount()) {
        final SpecificationNode sn = os.getChild(i);
        if (sn.getType().equals(CSVConfig.NODE_CONTENT_COLUMN)) {
          os.removeChild(i);
        } else {
          i++;
        }
      }
      if (x.length() > 0) {
        final SpecificationNode node = new SpecificationNode(CSVConfig.NODE_CONTENT_COLUMN);
        node.setAttribute(CSVConfig.ATTRIBUTE_VALUE, x);
        os.addChild(os.getChildCount(), node);
      }
    }

    x = variableContext.getParameter(seqPrefix + CSVConfig.NODE_SEPARATOR);
    if (x != null) {
      // Delete separator entry
      int i = 0;
      while (i < os.getChildCount()) {
        final SpecificationNode sn = os.getChild(i);
        if (sn.getType().equals(CSVConfig.NODE_SEPARATOR)) {
          os.removeChild(i);
        } else {
          i++;
        }
      }
      if (x.length() > 0) {
        final SpecificationNode node = new SpecificationNode(CSVConfig.NODE_SEPARATOR);
        node.setAttribute(CSVConfig.ATTRIBUTE_VALUE, x);
        os.addChild(os.getChildCount(), node);
      }
    }

    return null;
  }

  /**
   * Output the specification header section. This method is called in the head section of a job page which has selected a pipeline connection of the current type. Its purpose is to add the required
   * tabs to the list, and to output any javascript methods that might be needed by the job editing HTML.
   *
   * @param out                      is the output to which any HTML should be sent.
   * @param locale                   is the preferred local of the output.
   * @param os                       is the current pipeline specification for this connection.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @param tabsArray                is an array of tab names. Add to this array any tab names that are specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(final IHTTPOutput out, final Locale locale, final Specification os, final int connectionSequenceNumber, final List<String> tabsArray)
      throws ManifoldCFException, IOException {
    final Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

    tabsArray.add(Messages.getString(locale, "CSV.CSVTabName"));

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS, paramMap);
  }

  /**
   * Output the specification body section. This method is called in the body section of a job page which has selected a pipeline connection of the current type. Its purpose is to present the required
   * form elements for editing. The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags. The name of the form is
   * "editjob".
   *
   * @param out                      is the output to which any HTML should be sent.
   * @param locale                   is the preferred local of the output.
   * @param os                       is the current pipeline specification for this job.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @param actualSequenceNumber     is the connection within the job that has currently been selected.
   * @param tabName                  is the current tab name.
   */
  @Override
  public void outputSpecificationBody(final IHTTPOutput out, final Locale locale, final Specification os, final int connectionSequenceNumber, final int actualSequenceNumber, final String tabName)
      throws ManifoldCFException, IOException {
    final Map<String, Object> paramMap = new HashMap<>();

    // Set the tab name
    paramMap.put("TABNAME", tabName);
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));
    paramMap.put("SELECTEDNUM", Integer.toString(actualSequenceNumber));

    // Fill in the field mapping tab data
    fillInCSVSpecificationMap(paramMap, os);
    // fillInSecuritySpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_CSV_HTML, paramMap);
  }

  /**
   * View specification. This method is called in the body section of a job's view page. Its purpose is to present the pipeline specification information to the user. The coder can presume that the
   * HTML that is output from this configuration will be within appropriate <html> and <body> tags.
   *
   * @param out                      is the output to which any HTML should be sent.
   * @param locale                   is the preferred local of the output.
   * @param connectionSequenceNumber is the unique number of this connection within the job.
   * @param os                       is the current pipeline specification for this job.
   */

  @Override
  public void viewSpecification(final IHTTPOutput out, final Locale locale, final Specification os, final int connectionSequenceNumber) throws ManifoldCFException, IOException {
    final Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

    // Fill in the map with data from all tabs
    fillInCSVSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out, locale, VIEW_SPECIFICATION_CSV_HTML, paramMap);

  }

  private void fillInCSVSpecificationMap(final Map<String, Object> paramMap, final Specification os) {

    final List<String> filePaths = new ArrayList<>();
    String contentColumn = "content";
    String idColumn = "id";
    String separator = ",";

    for (int i = 0; i < os.getChildCount(); i++) {
      final SpecificationNode sn = os.getChild(i);

      if (sn.getType().equals(CSVConfig.NODE_FILEPATH)) {
        final String includeFileFilter = sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE);
        if (includeFileFilter != null) {
          filePaths.add(includeFileFilter);
        }
      } else if (sn.getType().equals(CSVConfig.NODE_ID_COLUMN)) {
        if (sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE) != null) {
          idColumn = sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE);
        }
      } else if (sn.getType().equals(CSVConfig.NODE_CONTENT_COLUMN)) {
        if (sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE) != null) {
          contentColumn = sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE);
        }
      } else if (sn.getType().equals(CSVConfig.NODE_SEPARATOR)) {
        if (sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE) != null) {
          separator = sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE);
        }
      }
    }

    paramMap.put("FILEPATHS", filePaths);
    paramMap.put("CONTENTCOLUMN", contentColumn);
    paramMap.put("IDCOLUMN", idColumn);
    paramMap.put("SEPARATOR", separator);
  }

  private static class CSVSpecs {

    private final Map<String, String[]> CSVMap = new HashMap<>();
    private String contentColumnLabel = "content";
    private final String idColumnLabel = "id";
    private String separator;

    public CSVSpecs(final Specification os) {

      final List<String> csvFiles = new ArrayList<>();

      for (int i = 0; i < os.getChildCount(); i++) {
        final SpecificationNode sn = os.getChild(i);

        if (sn.getType().equals(CSVConfig.NODE_FILEPATH)) {
          final String value = sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE);
          csvFiles.add(value);
        } else if (sn.getType().equals(CSVConfig.NODE_CONTENT_COLUMN)) {
          contentColumnLabel = sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE);
        } else if (sn.getType().equals(CSVConfig.NODE_SEPARATOR)) {
          separator = sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE);
        } else if (sn.getType().equals(CSVConfig.NODE_ID_COLUMN)) {
          separator = sn.getAttributeValue(CSVConfig.ATTRIBUTE_VALUE);
        }

      }

      for (final String csvFilePath : csvFiles) {
        try {
          final String[] columnsLabel = CSVUtils.getColumnsLabel(csvFilePath, separator);
          CSVMap.put(csvFilePath, columnsLabel);
        } catch (final IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }

    }

    public Map<String, String[]> getCSVMap() {
      return CSVMap;
    }

    public String getContentColumnLabel() {
      return contentColumnLabel;
    }

    public String getIdColumnLabel() {
      return idColumnLabel;
    }

    public String getSeparator() {
      return separator;
    }

  }
}
