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
  
  public static final String NODE_PAIR = "pair";
  public static final String ATTRIBUTE_PARAMETER = "parameter";
  public static final String NODE_FIELDMAP = "fieldmap";
  public static final String NODE_KEEPMETADATA = "keepAllMetadata";
  public static final String ATTRIBUTE_SOURCE = "source";
  public static final String ATTRIBUTE_TARGET = "target";
  public static final String ATTRIBUTE_VALUE = "value";

  // Templates
  
  private static final String VIEW_SPEC = "viewSpecification.html";
  private static final String EDIT_SPEC_HEADER = "editSpecification.js";
  private static final String EDIT_SPEC_FORCED_METADATA = "editSpecification_ForcedMetadata.html";
  private static final String EDIT_SPEC_FIELDMAPPING = "editSpecification_FieldMapping.html";

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
    SpecPacker sp = new SpecPacker(pipelineDescription.getVersionString());
    // We have to create a copy of the Repository Document, since we might be rearranging things
    RepositoryDocument docCopy = document.duplicate();
    docCopy.clearFields();
    // Do the mapping first!!
    Iterator<String> fields = document.getFields();
    while (fields.hasNext())
    {
      String field = fields.next();
      Object[] fieldData = document.getField(field);
      String target = sp.getMapping(field);
      if (target != null)
      {
        if (fieldData instanceof Date[])
          docCopy.addField(target,(Date[])fieldData);
        else if (fieldData instanceof Reader[])
          docCopy.addField(target,(Reader[])fieldData);
        else if (fieldData instanceof String[])
          docCopy.addField(target,(String[])fieldData);
      }
      else
      {
        if (sp.keepAllMetadata())
        {
          if (fieldData instanceof Date[])
            docCopy.addField(field,(Date[])fieldData);
          else if (fieldData instanceof Reader[])
            docCopy.addField(field,(Reader[])fieldData);
          else if (fieldData instanceof String[])
            docCopy.addField(field,(String[])fieldData);
        }
      }
    }

    Iterator<String> keys = sp.getParameterKeys();
    while (keys.hasNext())
    {
      String key = keys.next();
      docCopy.addField(key,sp.getParameterValues(key));
    }
    // Finally, send the modified repository document onward to the next pipeline stage.
    // If we'd done anything to the stream, we'd have needed to create a new RepositoryDocument object and copied the
    // data into it, and closed the new stream after sendDocument() was called.
    return activities.sendDocument(documentURI,docCopy,authorityNameString);
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
    
    // Add Forced Metadata to tab array
    tabsArray.add(Messages.getString(locale, "ForcedMetadata.FieldMappingTabName"));
    tabsArray.add(Messages.getString(locale, "ForcedMetadata.ForcedMetadata"));

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

    fillInForcedMetadataTab(paramMap, os);
    fillInFieldMappingSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,EDIT_SPEC_FORCED_METADATA,paramMap);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPEC_FIELDMAPPING,paramMap);
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
    String forcedCount = variableContext.getParameter(seqPrefix+"forcedmetadata_count");
    if (forcedCount != null)
    {
      int count = Integer.parseInt(forcedCount);
      // Delete old spec data
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode cn = os.getChild(i);
        if (cn.getType().equals(NODE_PAIR))
          os.removeChild(i);
        else
          i++;
      }
      // Now, go through form data
      for (int j = 0; j < count; j++)
      {
        String op = variableContext.getParameter(seqPrefix+"forcedmetadata_"+j+"_op");
        if (op != null && op.equals("Delete"))
          continue;
        String paramName = variableContext.getParameter(seqPrefix+"forcedmetadata_"+j+"_name");
        String paramValue = variableContext.getParameter(seqPrefix+"forcedmetadata_"+j+"_value");
        SpecificationNode sn = new SpecificationNode(NODE_PAIR);
        sn.setAttribute(ATTRIBUTE_PARAMETER,paramName);
        sn.setAttribute(ATTRIBUTE_VALUE,paramValue);
        os.addChild(os.getChildCount(),sn);
      }
      // Look for add operation
      String addOp = variableContext.getParameter(seqPrefix+"forcedmetadata_op");
      if (addOp != null && addOp.equals("Add"))
      {
        String paramName = variableContext.getParameter(seqPrefix+"forcedmetadata_name");
        String paramValue = variableContext.getParameter(seqPrefix+"forcedmetadata_value");
        SpecificationNode sn = new SpecificationNode(NODE_PAIR);
        sn.setAttribute(ATTRIBUTE_PARAMETER,paramName);
        sn.setAttribute(ATTRIBUTE_VALUE,paramValue);
        os.addChild(os.getChildCount(),sn);
      }
    }
    
    String x = variableContext.getParameter(seqPrefix+"fieldmapping_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the fieldmapping nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(NODE_FIELDMAP) || node.getType().equals(NODE_KEEPMETADATA))
          os.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = seqPrefix+"fieldmapping_";
        String suffix = "_"+Integer.toString(i);
        String op = variableContext.getParameter(prefix+"op"+suffix);
        if (op == null || !op.equals("Delete"))
        {
          // Gather the fieldmap etc.
          String source = variableContext.getParameter(prefix+"source"+suffix);
          String target = variableContext.getParameter(prefix+"target"+suffix);
          if (target == null)
            target = "";
          SpecificationNode node = new SpecificationNode(NODE_FIELDMAP);
          node.setAttribute(ATTRIBUTE_SOURCE,source);
          node.setAttribute(ATTRIBUTE_TARGET,target);
          os.addChild(os.getChildCount(),node);
        }
        i++;
      }
      
      String addop = variableContext.getParameter(seqPrefix+"fieldmapping_op");
      if (addop != null && addop.equals("Add"))
      {
        String source = variableContext.getParameter(seqPrefix+"fieldmapping_source");
        String target = variableContext.getParameter(seqPrefix+"fieldmapping_target");
        if (target == null)
          target = "";
        SpecificationNode node = new SpecificationNode(NODE_FIELDMAP);
        node.setAttribute(ATTRIBUTE_SOURCE,source);
        node.setAttribute(ATTRIBUTE_TARGET,target);
        os.addChild(os.getChildCount(),node);
      }
      
      // Gather the keep all metadata parameter to be the last one
      SpecificationNode node = new SpecificationNode(NODE_KEEPMETADATA);
      String keepAll = variableContext.getParameter(seqPrefix+"keepallmetadata");
      if (keepAll != null)
      {
        node.setAttribute(ATTRIBUTE_VALUE, keepAll);
      }
      else
      {
        node.setAttribute(ATTRIBUTE_VALUE, "false");
      }
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
    fillInForcedMetadataTab(paramMap, os);
    fillInFieldMappingSpecificationMap(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,VIEW_SPEC,paramMap);
  }

  protected static void fillInFieldMappingSpecificationMap(Map<String,Object> paramMap, Specification os)
  {
    // Prep for field mappings
    List<Map<String,String>> fieldMappings = new ArrayList<Map<String,String>>();
    String keepAllMetadataValue = "true";
    for (int i = 0; i < os.getChildCount(); i++)
    {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(NODE_FIELDMAP)) {
        String source = sn.getAttributeValue(ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(ATTRIBUTE_TARGET);
        String targetDisplay;
        if (target == null)
        {
          target = "";
          targetDisplay = "(remove)";
        }
        else
          targetDisplay = target;
        Map<String,String> fieldMapping = new HashMap<String,String>();
        fieldMapping.put("SOURCE",source);
        fieldMapping.put("TARGET",target);
        fieldMapping.put("TARGETDISPLAY",targetDisplay);
        fieldMappings.add(fieldMapping);
      }
      else if (sn.getType().equals(NODE_KEEPMETADATA))
      {
        keepAllMetadataValue = sn.getAttributeValue(ATTRIBUTE_VALUE);
      }
    }
    paramMap.put("FIELDMAPPINGS",fieldMappings);
    paramMap.put("KEEPALLMETADATA",keepAllMetadataValue);
  }

  protected static void fillInForcedMetadataTab(Map<String,Object> paramMap, Specification os)
  {
    // First, sort everything
    Map<String,Set<String>> params = new HashMap<String,Set<String>>();
    for (int i = 0; i < os.getChildCount(); i++)
    {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(NODE_PAIR))
      {
        String parameter = sn.getAttributeValue(ATTRIBUTE_PARAMETER);
        String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
        Set<String> values = params.get(parameter);
        if (values == null)
        {
          values = new HashSet<String>();
          params.put(parameter,values);
        }
        values.add(value);
      }
    }
    
    // We construct a list of maps from the parameters
    List<Map<String,String>> pObject = new ArrayList<Map<String,String>>();

    String[] keys = new String[params.size()];
    int j = 0;
    for (String key : params.keySet())
    {
      keys[j++] = key;
    }
    java.util.Arrays.sort(keys);
    
    // Now, build map
    for (String key : keys)
    {
      Set<String> values = params.get(key);
      String[] valueArray = new String[values.size()];
      j = 0;
      for (String value : values)
      {
        valueArray[j++] = value;
      }
      java.util.Arrays.sort(valueArray);
      
      for (String value : valueArray)
      {
        Map<String,String> record = new HashMap<String,String>();
        record.put("parameter",key);
        record.put("value",value);
        pObject.add(record);
      }
    }
    
    paramMap.put("PARAMETERS",pObject);
  }

  protected static class SpecPacker {
    
    private final Map<String,String> sourceTargets = new HashMap<String,String>();
    private final boolean keepAllMetadata;
    private final Map<String,Set<String>> parameters = new HashMap<String,Set<String>>();

    public SpecPacker(Specification os) {
      boolean keepAllMetadata = true;
      for (int i = 0; i < os.getChildCount(); i++) {
        SpecificationNode sn = os.getChild(i);
        
        if(sn.getType().equals(NODE_KEEPMETADATA)) {
          String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
          keepAllMetadata = Boolean.parseBoolean(value);
        } else if (sn.getType().equals(NODE_FIELDMAP)) {
          String source = sn.getAttributeValue(ATTRIBUTE_SOURCE);
          String target = sn.getAttributeValue(ATTRIBUTE_TARGET);
          
          if (target == null) {
            target = "";
          }
          sourceTargets.put(source, target);
        }
        else if (sn.getType().equals(NODE_PAIR))
        {
          String parameter = sn.getAttributeValue(ATTRIBUTE_PARAMETER);
          String value = sn.getAttributeValue(ATTRIBUTE_VALUE);
          Set<String> params = parameters.get(parameter);
          if (params == null)
          {
            params = new HashSet<String>();
            parameters.put(parameter,params);
          }
          params.add(value);
        }
      }
      this.keepAllMetadata = keepAllMetadata;
    }
    
    public SpecPacker(String packedString) {
      
      int index = 0;
      
      // Mappings
      final List<String> packedMappings = new ArrayList<String>();
      index = unpackList(packedMappings,packedString,index,'+');
      String[] fixedList = new String[2];
      for (String packedMapping : packedMappings) {
        unpackFixedList(fixedList,packedMapping,0,':');
        sourceTargets.put(fixedList[0], fixedList[1]);
      }
      
      // Keep all metadata
      if (packedString.length() > index)
        keepAllMetadata = (packedString.charAt(index++) == '+');
      else
        keepAllMetadata = true;
      
      List<String> keys = new ArrayList<String>();
      index = unpackList(keys,packedString,index,'+');
      // For each key, unpack its list of values
      for (String key : keys)
      {
        List<String> values = new ArrayList<String>();
        index = unpackList(values,packedString,index,'+');
        Set<String> valueSet = new HashSet<String>();
        for (String value : values)
        {
          valueSet.add(value);
        }
        parameters.put(key,valueSet);
      }

    }
    
    public String toPackedString() {
      StringBuilder sb = new StringBuilder();
      int i;
      
      // Mappings
      final String[] sortArray = new String[sourceTargets.size()];
      i = 0;
      for (String source : sourceTargets.keySet()) {
        sortArray[i++] = source;
      }
      java.util.Arrays.sort(sortArray);
      
      List<String> packedMappings = new ArrayList<String>();
      String[] fixedList = new String[2];
      for (String source : sortArray) {
        String target = sourceTargets.get(source);
        StringBuilder localBuffer = new StringBuilder();
        fixedList[0] = source;
        fixedList[1] = target;
        packFixedList(localBuffer,fixedList,':');
        packedMappings.add(localBuffer.toString());
      }
      packList(sb,packedMappings,'+');

      // Keep all metadata
      if (keepAllMetadata)
        sb.append('+');
      else
        sb.append('-');
      
      // Get the keys and sort them
      final String[] keys = new String[parameters.size()];
      int j = 0;
      for (String key : parameters.keySet())
      {
        keys[j++] = key;
      }
      java.util.Arrays.sort(keys);
      // Pack the list of keys
      packList(sb,keys,'+');
      // Now, go through each key and individually pack the values
      for (String key : keys)
      {
        Set<String> values = parameters.get(key);
        String[] valueArray = new String[values.size()];
        j = 0;
        for (String value : values)
        {
          valueArray[j++] = value;
        }
        java.util.Arrays.sort(valueArray);
        packList(sb,valueArray,'+');
      }

      return sb.toString();
    }
    
    public String getMapping(String source) {
      return sourceTargets.get(source);
    }
    
    public boolean keepAllMetadata() {
      return keepAllMetadata;
    }
    
    public Iterator<String> getParameterKeys()
    {
      return parameters.keySet().iterator();
    }
    
    public String[] getParameterValues(String key)
    {
      Set<String> values = parameters.get(key);
      if (values == null)
        return null;
      String[] rval = new String[values.size()];
      int i = 0;
      for (String value : values)
      {
        rval[i++] = value;
      }
      return rval;
    }
  }

}


