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
  public static final String ATTR_PARAMETER = "parameter";
  public static final String ATTR_VALUE = "value";
  
  // Templates
  
  private static final String VIEW_SPEC = "viewSpecification.html";
  private static final String EDIT_SPEC_HEADER = "editSpecification.js";
  private static final String EDIT_SPEC_FORCED_METADATA = "editSpecification_ForcedMetadata.html";

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
  public String getPipelineDescription(Specification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    // Pull out the forced metadata, and pack them.
    // We *could* use XML or JSON, but then we'd have to parse it later, and that's far more expensive than what we actually are going to do.
    // Plus, these must be ordered to make strings comparable.
    Map<String,Set<String>> parameters = new HashMap<String,Set<String>>();
    for (int i = 0; i < spec.getChildCount(); i++)
    {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(NODE_PAIR))
      {
        String parameter = sn.getAttributeValue(ATTR_PARAMETER);
        String value = sn.getAttributeValue(ATTR_VALUE);
        Set<String> params = parameters.get(parameter);
        if (params == null)
        {
          params = new HashSet<String>();
          parameters.put(parameter,params);
        }
        params.add(value);
      }
    }
    
    // Construct the string
    StringBuilder sb = new StringBuilder();
    // Get the keys and sort them
    String[] keys = new String[parameters.size()];
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
  public int addOrReplaceDocumentWithException(String documentURI, String pipelineDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException
  {
    // Unpack the forced metadata and add it to the document
    int index = 0;
    List<String> keys = new ArrayList<String>();
    index = unpackList(keys,pipelineDescription,index,'+');
    // For each key, unpack its list of values
    for (String key : keys)
    {
      List<String> values = new ArrayList<String>();
      index = unpackList(values,pipelineDescription,index,'+');
      String[] valueArray = (String[])values.toArray(new String[0]);
      // Go through the value list and modify the repository document.
      // This blows away existing values for the fields, if any.
      // NOTE WELL: Upstream callers who set Reader metadata values (or anything that needs to be closed)
      // are responsible for closing those resources, whether or not they remain in the RepositoryDocument
      // object after indexing is done!!
      document.addField(key,valueArray);
    }
    // Finally, send the modified repository document onward to the next pipeline stage.
    // If we'd done anything to the stream, we'd have needed to create a new RepositoryDocument object and copied the
    // data into it, and closed the new stream after sendDocument() was called.
    return activities.sendDocument(documentURI,document,authorityNameString);
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
    tabsArray.add(Messages.getString(locale, "ForcedMetadata.ForcedMetadata"));

    Map<String, Object> paramMap = new HashMap<String, Object>();
    paramMap.put("SeqNum",Integer.toString(connectionSequenceNumber));

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
    paramMap.put("TabName", tabName);
    paramMap.put("SeqNum",Integer.toString(connectionSequenceNumber));
    paramMap.put("SelectedNum",Integer.toString(actualSequenceNumber));

    fillInForcedMetadataTab(paramMap, os);
    Messages.outputResourceWithVelocity(out,locale,EDIT_SPEC_FORCED_METADATA,paramMap);
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
    String prefix = "s"+connectionSequenceNumber+"_";
    String forcedCount = variableContext.getParameter(prefix+"forcedmetadata_count");
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
        String op = variableContext.getParameter(prefix+"forcedmetadata_"+j+"_op");
        if (op != null && op.equals("Delete"))
          continue;
        String paramName = variableContext.getParameter(prefix+"forcedmetadata_"+j+"_name");
        String paramValue = variableContext.getParameter(prefix+"forcedmetadata_"+j+"_value");
        SpecificationNode sn = new SpecificationNode(NODE_PAIR);
        sn.setAttribute(ATTR_PARAMETER,paramName);
        sn.setAttribute(ATTR_VALUE,paramValue);
        os.addChild(os.getChildCount(),sn);
      }
      // Look for add operation
      String addOp = variableContext.getParameter(prefix+"forcedmetadata_op");
      if (addOp != null && addOp.equals("Add"))
      {
        String paramName = variableContext.getParameter(prefix+"forcedmetadata_name");
        String paramValue = variableContext.getParameter(prefix+"forcedmetadata_value");
        SpecificationNode sn = new SpecificationNode(NODE_PAIR);
        sn.setAttribute(ATTR_PARAMETER,paramName);
        sn.setAttribute(ATTR_VALUE,paramValue);
        os.addChild(os.getChildCount(),sn);
      }
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
    paramMap.put("SeqNum",Integer.toString(connectionSequenceNumber));
    
    // Fill in the map with data from all tabs
    fillInForcedMetadataTab(paramMap, os);

    Messages.outputResourceWithVelocity(out,locale,VIEW_SPEC,paramMap);
  }

  protected void fillInForcedMetadataTab(Map<String,Object> paramMap, Specification os)
  {
    // First, sort everything
    Map<String,Set<String>> params = new HashMap<String,Set<String>>();
    for (int i = 0; i < os.getChildCount(); i++)
    {
      SpecificationNode sn = os.getChild(i);
      if (sn.getType().equals(NODE_PAIR))
      {
        String parameter = sn.getAttributeValue(ATTR_PARAMETER);
        String value = sn.getAttributeValue(ATTR_VALUE);
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
    
    paramMap.put("Parameters",pObject);
  }

}


