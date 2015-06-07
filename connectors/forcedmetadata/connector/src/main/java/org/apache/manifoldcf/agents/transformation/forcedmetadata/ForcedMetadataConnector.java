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
package org.apache.manifoldcf.agents.transformation.forcedmetadata;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.util.*;

/** This connector works as a transformation connector, and merely adds specified metadata items.
*
*/
public class ForcedMetadataConnector extends org.apache.manifoldcf.agents.transformation.BaseTransformationConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Nodes and attributes representing parameters and values.
  // There will be node for every parameter/value pair.
  
  public static final String NODE_EXPRESSION = "expression";
  public static final String NODE_PAIR = "pair";
  public static final String ATTRIBUTE_PARAMETER = "parameter";
  public static final String NODE_FIELDMAP = "fieldmap";
  public static final String NODE_KEEPMETADATA = "keepAllMetadata";
  public static final String NODE_FILTEREMPTY = "filterEmpty";
  public static final String ATTRIBUTE_SOURCE = "source";
  public static final String ATTRIBUTE_TARGET = "target";
  public static final String ATTRIBUTE_VALUE = "value";

  // Templates
  
  private static final String VIEW_SPEC = "viewSpecification.html";
  private static final String EDIT_SPEC_HEADER = "editSpecification.js";
  private static final String EDIT_SPEC_EXPRESSIONS = "editSpecification_Expressions.html";

  /** Get a pipeline version string, given a pipeline specification object.  The version string is used to
  * uniquely describe the pertinent details of the specification and the configuration, to allow the Connector 
  * Framework to determine whether a document will need to be processed again.
  * Note that the contents of any document cannot be considered by this method; only configuration and specification information
  * can be considered.
  *
  * This method presumes that the underlying connector object has been configured.
  *@param spec is the current pipeline specification object for this connection for the job that is doing the crawling.
  *@return a string, of unlimited length, which uniquely describes configuration and specification in such a way that
  * if two such strings are equal, nothing that affects how or whether the document is indexed will be different.
  */
  @Override
  public VersionContext getPipelineDescription(Specification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    SpecPacker sp = new SpecPacker(spec);
    return new VersionContext(sp.toPackedString(),params,spec);
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
    // Unpack the forced metadata
    SpecPacker sp = new SpecPacker(pipelineDescription.getSpecification());
    
    // Create a structure that will allow us access to fields without sharing Reader objects
    FieldDataFactory fdf = new FieldDataFactory(document);
    try {
      // We have to create a copy of the Repository Document, since we might be rearranging things
      RepositoryDocument docCopy = document.duplicate();
      // We must explicitly copy all fields, since we can't share references to Reader objects and
      // expect anything to work right
      docCopy.clearFields();
      
      // Clear fields, unless we're supposed to keep what we don't specify
      if (sp.filterEmpty()) {
        if (sp.keepAllMetadata()) {
          // Loop through fields and copy them, filtering empties
          Iterator<String> fields = document.getFields();
          while (fields.hasNext())
          {
            String field = fields.next();
            moveData(docCopy,field,fdf,field,true);
          }
        }
      } else if (sp.keepAllMetadata()) {
        // Copy ALL current fields from old document, but go through FieldDataFactory
        Iterator<String> fields = document.getFields();
        while (fields.hasNext())
        {
          String field = fields.next();
          moveData(docCopy,field,fdf,field,false);
        }
      }
      
      // Iterate through the expressions
      Iterator<String> expressionKeys = sp.getExpressionKeys();
      while (expressionKeys.hasNext()) {
        String expressionKey = expressionKeys.next();
        // Get the set of expressions for the key
        Set<String> values = sp.getExpressionValues(expressionKey);
        IDataSource[] dataSources = new IDataSource[values.size()];
        int k = 0;
        for (String expression : values) {
          dataSources[k++] = processExpression(expression, fdf);
        }
        int totalSize = 0;
        for (IDataSource dataSource : dataSources) {
          if (dataSource != null)
            totalSize += dataSource.getSize();
        }
        if (totalSize == 0) {
          docCopy.removeField(expressionKey);
        } else {
          // Each IDataSource will contribute zero or more results to the final array.  But here's the tricky part:
          // the results all must be of the same type.  If there are any differences, then we have to bash them all to
          // strings first.
          Object[] allValues;
          k = 0;
          if (allDates(dataSources)) {
            allValues = new Date[totalSize];
            for (IDataSource dataSource : dataSources) {
              if (dataSource != null) {
                for (Object o : dataSource.getRawForm()) {
                  allValues[k++] = o;
                }
              }
            }
            docCopy.addField(expressionKey,(Date[])conditionallyRemoveNulls(allValues,sp.filterEmpty()));
          } else if (allReaders(dataSources)) {
            if (sp.filterEmpty())
              allValues = new String[totalSize];
            else
              allValues = new Reader[totalSize];
            for (IDataSource dataSource : dataSources) {
              if (dataSource != null) {
                Object[] sources = sp.filterEmpty()?dataSource.getStringForm():dataSource.getRawForm();
                for (Object o : sources) {
                  allValues[k++] = o;
                }
              }
            }
            if (sp.filterEmpty())
              docCopy.addField(expressionKey,removeEmpties((String[])allValues));
            else
              docCopy.addField(expressionKey,(Reader[])allValues);
          } else {
            allValues = new String[totalSize];
            // Convert to strings throughout
            for (IDataSource dataSource : dataSources) {
              if (dataSource != null) {
                for (Object o : dataSource.getStringForm()) {
                  allValues[k++] = o;
                }
              }
            }
            if (sp.filterEmpty())
              docCopy.addField(expressionKey,removeEmpties((String[])allValues));
            else
              docCopy.addField(expressionKey,(String[])allValues);
          }
        }
      }
      
      // Finally, send the modified repository document onward to the next pipeline stage.
      // If we'd done anything to the stream, we'd have needed to create a new RepositoryDocument object and copied the
      // data into it, and closed the new stream after sendDocument() was called.
      return activities.sendDocument(documentURI,docCopy);

    } finally {
      fdf.close();
    }
  }

  protected static boolean allDates(IDataSource[] dataSources)
    throws IOException, ManifoldCFException {
    for (IDataSource ds : dataSources) {
      if (ds != null && !(ds.getRawForm() instanceof Date[]))
        return false;
    }
    return true;
  }

  protected static boolean allReaders(IDataSource[] dataSources)
    throws IOException, ManifoldCFException {
    for (IDataSource ds : dataSources) {
      if (ds != null && !(ds.getRawForm() instanceof Reader[]))
        return false;
    }
    return true;
  }
  
  protected static void moveData(RepositoryDocument docCopy, String target, FieldDataFactory document, String field, boolean filterEmpty)
    throws ManifoldCFException, IOException
  {
    Object[] fieldData = document.getField(field);
    if (fieldData instanceof Date[])
      docCopy.addField(target,(Date[])conditionallyRemoveNulls(fieldData,filterEmpty));
    else if (fieldData instanceof Reader[])
    {
      // To strip out empty fields, we will need to convert readers to strings
      if (filterEmpty)
        docCopy.addField(target,removeEmpties(document.getFieldAsStrings(field)));
      else
        docCopy.addField(target,(Reader[])fieldData);
    }
    else if (fieldData instanceof String[])
    {
      String[] processedFieldData;
      if (filterEmpty)
        processedFieldData = removeEmpties((String[])fieldData);
      else
        processedFieldData = (String[])fieldData;
      docCopy.addField(target,processedFieldData);
    }
  }

  protected static String[] removeEmpties(String[] input)
  {
    int count = 0;
    for (String s : input)
    {
      if (s != null && s.length() > 0)
        count++;
    }
    if (count == input.length)
      return input;
    
    String[] rval = new String[count];
    count = 0;
    for (String s : input)
    {
      if (s != null && s.length() > 0)
        rval[count++] = s;
    }
    return rval;

  }
  
  protected static Object[] conditionallyRemoveNulls(Object[] input, boolean filterEmpty)
  {
    if (!filterEmpty)
      return input;
    int count = 0;
    for (Object o : input)
    {
      if (o != null)
        count++;
    }
    if (count == input.length)
      return input;
    
    Object[] rval = new Object[count];
    count = 0;
    for (Object o : input)
    {
      if (o != null)
        rval[count++] = o;
    }
    return rval;
  }
  
  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch (inherited from IConnector) is involved in setting up connection configuration information.
  // The second bunch
  // is involved in presenting and editing pipeline specification information for a connection within a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).

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
    // Output specification header
    
    tabsArray.add(Messages.getString(locale, "ForcedMetadata.Expressions"));

    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));

    Messages.outputResourceWithVelocity(out,locale,EDIT_SPEC_HEADER,paramMap);
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
    // Output specification body
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("TABNAME", tabName);
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));
    paramMap.put("SELECTEDNUM",Integer.toString(actualSequenceNumber));

    fillInExpressionsTab(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,EDIT_SPEC_EXPRESSIONS,paramMap);
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
    throws ManifoldCFException
  {
    // Process specification post
    String seqPrefix = "s"+connectionSequenceNumber+"_";
    String expressionCount = variableContext.getParameter(seqPrefix+"expression_count");
    if (expressionCount != null)
    {
      int count = Integer.parseInt(expressionCount);
      // Delete old spec data, including legacy node types we no longer use
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode cn = os.getChild(i);
        if (cn.getType().equals(NODE_EXPRESSION) || cn.getType().equals(NODE_PAIR) || cn.getType().equals(NODE_FIELDMAP))
          os.removeChild(i);
        else
          i++;
      }

      // Now, go through form data
      for (int j = 0; j < count; j++)
      {
        String op = variableContext.getParameter(seqPrefix+"expression_"+j+"_op");
        if (op != null && op.equals("Delete"))
          continue;
        String paramName = variableContext.getParameter(seqPrefix+"expression_"+j+"_name");
        String paramRemove = variableContext.getParameter(seqPrefix+"expression_"+j+"_remove");
        String paramValue = variableContext.getParameter(seqPrefix+"expression_"+j+"_value");
        SpecificationNode sn = new SpecificationNode(NODE_EXPRESSION);
        sn.setAttribute(ATTRIBUTE_PARAMETER,paramName);
        if (!(paramRemove != null && paramRemove.equals("true")))
          sn.setAttribute(ATTRIBUTE_VALUE,paramValue);
        os.addChild(os.getChildCount(),sn);
      }
      // Look for add operation
      String addOp = variableContext.getParameter(seqPrefix+"expression_op");
      if (addOp != null && addOp.equals("Add"))
      {
        String paramName = variableContext.getParameter(seqPrefix+"expression_name");
        String paramRemove = variableContext.getParameter(seqPrefix+"expression_remove");
        String paramValue = variableContext.getParameter(seqPrefix+"expression_value");
        SpecificationNode sn = new SpecificationNode(NODE_EXPRESSION);
        sn.setAttribute(ATTRIBUTE_PARAMETER,paramName);
        if (!(paramRemove != null && paramRemove.equals("true")))
          sn.setAttribute(ATTRIBUTE_VALUE,paramValue);
        os.addChild(os.getChildCount(),sn);
      }

    }
    
    String x = variableContext.getParameter(seqPrefix+"keepallmetadata_present");
    if (x != null && x.length() > 0)
    {
      String keepAll = variableContext.getParameter(seqPrefix+"keepallmetadata");
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
      // Add the new keepallmetadata config parameter 
      os.addChild(os.getChildCount(), node);
    }

    x = variableContext.getParameter(seqPrefix+"filterempty_present");
    if (x != null && x.length() > 0)
    {
      String filterEmpty = variableContext.getParameter(seqPrefix+"filterempty");
      if (filterEmpty == null)
        filterEmpty = "false";
      // About to gather the fieldmapping nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(NODE_FILTEREMPTY))
          os.removeChild(i);
        else
          i++;
      }

      // Gather the keep all metadata parameter to be the last one
      SpecificationNode node = new SpecificationNode(NODE_FILTEREMPTY);
      node.setAttribute(ATTRIBUTE_VALUE, filterEmpty);
      // Add the new keepallmetadata config parameter 
      os.addChild(os.getChildCount(), node);
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
    // View specification
    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SEQNUM",Integer.toString(connectionSequenceNumber));
    
    // Fill in the map with data from all tabs
    fillInExpressionsTab(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,VIEW_SPEC,paramMap);
  }

  protected static void fillInExpressionsTab(Map<String,Object> paramMap, Specification os)
  {
    final Map<String,Set<String>> expressions = new HashMap<String,Set<String>>();
    final Map<String,Set<String>> expressionAdditions = new HashMap<String,Set<String>>();
    final Map<String,Set<String>> additions = new HashMap<String,Set<String>>();
    
    String keepAllMetadataValue = "true";
    String filterEmptyValue = "true";
    for (int i = 0; i < os.getChildCount(); i++)
    {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(NODE_FIELDMAP)) {
        String source = sn.getAttributeValue(ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(ATTRIBUTE_TARGET);
        String targetDisplay;
        
        expressions.put(source,new HashSet<String>());
        if (target != null)
        {
          Set<String> sources = new HashSet<String>();
          sources.add("${"+source+"}");
          expressions.put(target,sources);
        }
      }
      else if (sn.getType().equals(NODE_PAIR))
      {
        String parameter = sn.getAttributeValue(ATTRIBUTE_PARAMETER);
        String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
        // Since the same target is completely superceded by a NODE_PAIR, but NODE_PAIRs
        // are cumulative, I have to build these completely and then post-process them.
        Set<String> addition = additions.get(parameter);
        if (addition == null)
        {
          addition = new HashSet<String>();
          additions.put(parameter,addition);
        }
        addition.add(nonExpressionEscape(value));
      }
      else if (sn.getType().equals(NODE_EXPRESSION))
      {
        String parameter = sn.getAttributeValue(ATTRIBUTE_PARAMETER);
        String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
        if (value == null) {
          expressionAdditions.put(parameter,new HashSet<String>());
        } else {
          Set<String> expressionAddition = expressionAdditions.get(parameter);
          if (expressionAddition == null)
          {
            expressionAddition = new HashSet<String>();
            expressionAdditions.put(parameter,expressionAddition);
          }
          expressionAddition.add(value);
        }
      }
      else if (sn.getType().equals(NODE_KEEPMETADATA))
      {
        keepAllMetadataValue = sn.getAttributeValue(ATTRIBUTE_VALUE);
      }
      else if (sn.getType().equals(NODE_FILTEREMPTY))
      {
        filterEmptyValue = sn.getAttributeValue(ATTRIBUTE_VALUE);
      }
    }
    
    // Postprocessing.
    // Override the moves with the additions
    for (String parameter : additions.keySet())
    {
      expressions.put(parameter,additions.get(parameter));
    }

    // Override all with expression additions
    for (String parameter : expressionAdditions.keySet())
    {
      expressions.put(parameter,expressionAdditions.get(parameter));
    }

    // Problem: how to display case where we want a null source??
    // A: Special value
    List<Map<String,String>> pObject = new ArrayList<Map<String,String>>();
    String[] keys = expressions.keySet().toArray(new String[0]);
    java.util.Arrays.sort(keys);
    
    // Now, build map
    for (String key : keys)
    {
      Set<String> values = expressions.get(key);
      if (values.size() == 0) {
        Map<String,String> record = new HashMap<String,String>();
        record.put("parameter",key);
        record.put("value","");
        record.put("isnull","true");
        pObject.add(record);
      } else {
        String[] valueArray = values.toArray(new String[0]);
        java.util.Arrays.sort(valueArray);
        
        for (String value : valueArray)
        {
          Map<String,String> record = new HashMap<String,String>();
          record.put("parameter",key);
          record.put("value",value);
          record.put("isnull","false");
          pObject.add(record);
        }
      }
    }
    
    paramMap.put("EXPRESSIONS",pObject);
    paramMap.put("KEEPALLMETADATA",keepAllMetadataValue);
    paramMap.put("FILTEREMPTY",filterEmptyValue);
  }
  
  /** This is used to upgrade older constant values to new ones, that won't trigger expression eval.
  */
  protected static String nonExpressionEscape(String input) {
    // Not doing any escaping yet
    return input;
  }

  /** This is used to unescape text that's been escaped to prevent substitution of ${} expressions.
  */
  protected static String nonExpressionUnescape(String input) {
    // Not doing any escaping yet
    return input;
  }
  
  
  protected static IDataSource append(IDataSource currentValues, IDataSource data)
    throws IOException, ManifoldCFException {
    // currentValues and data can either be:
    // Date[], String[], or Reader[].
    // We want to preserve the type in as high a form as possible when we compute the combinations.
    if (currentValues == null)
      return data;
    if (currentValues.getSize() == 0)
      return currentValues;
    // Any combination causes conversion to a string, so if we get here, we can read the inputs all
    // as strings safely.
    String[] currentStrings = currentValues.getStringForm();
    String[] dataStrings = data.getStringForm();
    String[] rval = new String[currentStrings.length * dataStrings.length];
    int rvalIndex = 0;
    for (String currentString : currentStrings) {
      for (String dataString : dataStrings) {
        rval[rvalIndex++] = currentString + dataString;
      }
    }
    return new StringSource(rval);
  }
  
  public static IDataSource processExpression(String expression, FieldDataFactory sourceDocument)
    throws IOException, ManifoldCFException {
    int index = 0;
    IDataSource input = null;
    while (true) {
      // If we're at the end, return the input
      if (index == expression.length())
        return input;
      // Look for next field specification
      int field = expression.indexOf("${",index);
      if (field == -1)
        return append(input, new StringSource(nonExpressionUnescape(expression.substring(index))));
      if (field > 0)
        input = append(input, new StringSource(nonExpressionUnescape(expression.substring(index,field))));
      // Parse the field name, and regular expression (if any)
      StringBuilder fieldNameBuffer = new StringBuilder();
      StringBuilder regExpBuffer = new StringBuilder();
      StringBuilder groupNumberBuffer = new StringBuilder();
      field = parseArgument(expression, field+2, fieldNameBuffer);
      field = parseArgument(expression, field, regExpBuffer);
      field = parseArgument(expression, field, groupNumberBuffer);
      int fieldEnd = parseToEnd(expression, field);
      if (fieldEnd == expression.length()) {
        if (fieldNameBuffer.length() > 0)
          return append(input, new FieldSource(sourceDocument, fieldNameBuffer.toString(), regExpBuffer.toString(), groupNumberBuffer.toString()));
        return input;
      } else {
        if (fieldNameBuffer.length() > 0)
          input = append(input, new FieldSource(sourceDocument, fieldNameBuffer.toString(), regExpBuffer.toString(), groupNumberBuffer.toString()));
        index = fieldEnd;
      }
    }
  }
  
  protected static int parseArgument(final String input, int start, final StringBuilder output) {
    // Parse until we hit the end marker or an unescaped pipe symbol
    while (true) {
      if (input.length() == start)
        return start;
      char theChar = input.charAt(start);
      if (theChar == '}')
        return start;
      start++;
      if (theChar == '|')
        return start;
      if (theChar == '\\') {
        if (input.length() == start)
          return start;
        theChar = input.charAt(start++);
      }
      output.append(theChar);
    }
  }
  
  protected static int parseToEnd(final String input, int start) {
    while (true) {
      if (input.length() == start)
        return start;
      char theChar = input.charAt(start++);
      if (theChar == '}')
        return start;
      if (theChar == '\\') {
        if (input.length() == start)
          return start;
        start++;
      }
    }
  }

  protected static class SpecPacker {
    
    private final boolean keepAllMetadata;
    private final boolean filterEmpty;
    private final Map<String,Set<String>> expressions = new HashMap<String,Set<String>>();

    public SpecPacker(Specification os) {
      boolean keepAllMetadata = true;
      boolean filterEmpty = true;
      final Map<String,Set<String>> additions = new HashMap<String,Set<String>>();
      final Map<String,Set<String>> expressionAdditions = new HashMap<String,Set<String>>();
      for (int i = 0; i < os.getChildCount(); i++) {
        SpecificationNode sn = os.getChild(i);
        
        if(sn.getType().equals(NODE_KEEPMETADATA)) {
          String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
          keepAllMetadata = Boolean.parseBoolean(value);
        } else if (sn.getType().equals(NODE_FILTEREMPTY)) {
          String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
          filterEmpty = Boolean.parseBoolean(value);
        } else if (sn.getType().equals(NODE_FIELDMAP)) {
          String source = sn.getAttributeValue(ATTRIBUTE_SOURCE);
          String target = sn.getAttributeValue(ATTRIBUTE_TARGET);
          
          expressions.put(source,new HashSet<String>());
          // Null target means to remove the *source* from the document.
          if (target != null) {
            Set<String> sources = new HashSet<String>();
            sources.add("${"+source+"}");
            expressions.put(target,sources);
          }
        }
        else if (sn.getType().equals(NODE_PAIR))
        {
          String parameter = sn.getAttributeValue(ATTRIBUTE_PARAMETER);
          String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
          // Since the same target is completely superceded by a NODE_PAIR, but NODE_PAIRs
          // are cumulative, I have to build these completely and then post-process them.
          Set<String> addition = additions.get(parameter);
          if (addition == null)
          {
            addition = new HashSet<String>();
            additions.put(parameter,addition);
          }
          addition.add(nonExpressionEscape(value));
        }
        else if (sn.getType().equals(NODE_EXPRESSION))
        {
          String parameter = sn.getAttributeValue(ATTRIBUTE_PARAMETER);
          String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
          if (value == null) {
            expressionAdditions.put(parameter,new HashSet<String>());
          } else {
            Set<String> expressionAddition = expressionAdditions.get(parameter);
            if (expressionAddition == null)
            {
              expressionAddition = new HashSet<String>();
              expressionAdditions.put(parameter,expressionAddition);
            }
            expressionAddition.add(value);
          }
        }
      }
      
      // Override the moves with the additions
      for (String parameter : additions.keySet())
      {
        expressions.put(parameter,additions.get(parameter));
      }

      // Override all with expression additions
      for (String parameter : expressionAdditions.keySet())
      {
        expressions.put(parameter,expressionAdditions.get(parameter));
      }
      
      this.keepAllMetadata = keepAllMetadata;
      this.filterEmpty = filterEmpty;
    }
    
    public String toPackedString() {
      StringBuilder sb = new StringBuilder();
      int i;
      
      final String[] sortArray = expressions.keySet().toArray(new String[0]);
      java.util.Arrays.sort(sortArray);
      // Pack the list of keys
      packList(sb,sortArray,'+');
      for (String key : sortArray) {
        Set<String> values = expressions.get(key);
        String[] valueArray = values.toArray(new String[0]);
        java.util.Arrays.sort(valueArray);
        packList(sb,valueArray,'+');
      }

      // Keep all metadata
      if (keepAllMetadata)
        sb.append('+');
      else
        sb.append('-');
      
      // Filter empty
      if (filterEmpty)
        sb.append('+');
      else
        sb.append('-');

      return sb.toString();
    }
    
    public Iterator<String> getExpressionKeys()
    {
      return expressions.keySet().iterator();
    }
    
    public Set<String> getExpressionValues(String key)
    {
      return expressions.get(key);
    }
    
    public boolean keepAllMetadata() {
      return keepAllMetadata;
    }
    
    public boolean filterEmpty() {
      return filterEmpty;
    }
  }
  
}


