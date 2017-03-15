/* $Id: GTSConnector.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.Logging;

import org.apache.manifoldcf.connectorcommon.interfaces.*;

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
public class GTSConnector extends org.apache.manifoldcf.agents.output.BaseOutputConnector
{
  public static final String _rcsid = "@(#)$Id: GTSConnector.java 988245 2010-08-23 18:39:35Z kwright $";

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
  @Override
  public String[] getActivitiesList()
  {
    return new String[]{INGEST_ACTIVITY,REMOVE_ACTIVITY};
  }

  /** Connect.
  *@param configParameters is the set of configuration parameters, which
  * in this case describe the target appliance, basic auth configuration, etc.  (This formerly came
  * out of the ini file.)
  */
  @Override
  public void connect(ConfigParams configParameters)
  {
    super.connect(configParameters);
  }

  /** Close the connection.  Call this before discarding the connection.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    poster = null;
    super.disconnect();
  }

  /** Set up a session */
  protected void getSession()
    throws ManifoldCFException
  {
    if (poster == null)
    {
      String ingestURI = params.getParameter(GTSConfig.PARAM_INGESTURI);
      if (ingestURI == null)
        throw new ManifoldCFException("Missing parameter '"+GTSConfig.PARAM_INGESTURI+"'");
      String userID = params.getParameter(GTSConfig.PARAM_USERID);
      String password = params.getObfuscatedParameter(GTSConfig.PARAM_PASSWORD);
      String realm = params.getParameter(GTSConfig.PARAM_REALM);
      poster = new HttpPoster(currentContext,realm,userID,password,ingestURI);
    }
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check()
    throws ManifoldCFException
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

  protected static final String[] ingestableMimeTypeArray = new String[]
  {
    "application/excel",
      "application/powerpoint",
      "application/ppt",
      "application/rtf",
      "application/xls",
      "text/html",
      "text/rtf",
      "text/pdf",
      "application/x-excel",
      "application/x-msexcel",
      "application/x-mspowerpoint",
      "application/x-msword-doc",
      "application/x-msword",
      "application/x-word",
      "Application/pdf",
      "text/xml",
      "no-type",
      "text/plain",
      "application/pdf",
      "application/x-rtf",
      "application/vnd.ms-excel",
      "application/vnd.ms-pps",
      "application/vnd.ms-powerpoint",
      "application/vnd.ms-word",
      "application/msword",
      "application/msexcel",
      "application/mspowerpoint",
      "application/ms-powerpoint",
      "application/ms-word",
      "application/ms-excel",
      "Adobe",
      "application/Vnd.Ms-Excel",
      "vnd.ms-powerpoint",
      "application/x-pdf",
      "winword",
      "text/richtext",
      "Text",
      "Text/html",
      "application/MSWORD",
      "application/PDF",
      "application/MSEXCEL",
      "application/MSPOWERPOINT"
  };

  protected static final Map ingestableMimeTypeMap = new HashMap();
  static
  {
    int i = 0;
    while (i < ingestableMimeTypeArray.length)
    {
      String type = ingestableMimeTypeArray[i++];
      ingestableMimeTypeMap.put(type,type);
    }
  }

  /** Detect if a mime type is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
  * unusable documents that will be passed to this output connector.
  *@param mimeType is the mime type of the document.
  *@return true if the mime type is indexable by this connector.
  */
  @Override
  public boolean checkMimeTypeIndexable(VersionContext outputDescription, String mimeType, IOutputCheckActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    return (ingestableMimeTypeMap.get(mimeType) != null);
  }

  /** Pre-determine whether a document (passed here as a File object) is indexable by this connector.  This method is used by participating
  * repository connectors to help reduce the number of unmanageable documents that are passed to this output connector in advance of an
  * actual transfer.  This hook is provided mainly to support search engines that only handle a small set of accepted file types.
  *@param localFile is the local file to check.
  *@return true if the file is indexable.
  */
  @Override
  public boolean checkDocumentIndexable(VersionContext outputDescription, File localFile, IOutputCheckActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
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
  @Override
  public VersionContext getPipelineDescription(Specification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    List<String> collectionList = new ArrayList<String>();
    String documentTemplate = "";
    for (int i = 0; i < spec.getChildCount(); i++)
    {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(GTSConfig.NODE_COLLECTION))
      {
        collectionList.add(sn.getAttributeValue(GTSConfig.ATTRIBUTE_VALUE));
      }
      else if (sn.getType().equals(GTSConfig.NODE_DOCUMENTTEMPLATE))
      {
        documentTemplate = sn.getAttributeValue(GTSConfig.ATTRIBUTE_VALUE);
      }
    }

    // Get the config info too.  This will be constant for any given connector instance, so we don't have to worry about it changing
    // out from under us.
    String ingestURI = params.getParameter(GTSConfig.PARAM_INGESTURI);

    // Now, construct the appropriate string
    // The information we want in this string is:
    // (1) the collection name(s), in sorted order.
    // (2) the document template
    // (3) the ingest URI

    String[] sortArray = new String[collectionList.size()];
    int j = 0;
    for (String collection : collectionList)
    {
      sortArray[j++] = collection;
    }
    java.util.Arrays.sort(sortArray);

    StringBuilder sb = new StringBuilder();
    packList(sb,sortArray,'+');
    pack(sb,documentTemplate,'+');
    // From here on down, unpacking is unnecessary.
    sb.append(ingestURI);

    return new VersionContext(sb.toString(),params,spec);
  }

  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param pipelineDescription includes the description string that was constructed for this document by the getOutputDescription() method.
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
    // Grab the information we need to index
    Specification spec = pipelineDescription.getSpecification();
    List<String> collectionList = new ArrayList<String>();
    String documentTemplate = "";
    for (int i = 0; i < spec.getChildCount(); i++)
    {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals(GTSConfig.NODE_COLLECTION))
      {
        collectionList.add(sn.getAttributeValue(GTSConfig.ATTRIBUTE_VALUE));
      }
      else if (sn.getType().equals(GTSConfig.NODE_DOCUMENTTEMPLATE))
      {
        documentTemplate = sn.getAttributeValue(GTSConfig.ATTRIBUTE_VALUE);
      }
    }

    // Establish a session
    getSession();

    // Now, go off and call the ingest API.
    if (poster.indexPost(documentURI,collectionList,documentTemplate,authorityNameString,document,activities))
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
  @Override
  public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // Establish a session
    getSession();

    // Call the ingestion API.
    poster.deletePost(documentURI,activities);
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing output specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"GTSConnector.Appliance"));
    out.print(
"\n"+
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.ingesturi.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"GTSConnector.PleaseSupplyAValidIngestionURI") + "\");\n"+
"    editconnection.ingesturi.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.ingesturi.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"GTSConnector.PleaseSupplyAValidIngestionURI") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"GTSConnector.Appliance") + "\");\n"+
"    editconnection.ingesturi.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );

  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String ingestURI = parameters.getParameter(org.apache.manifoldcf.agents.output.gts.GTSConfig.PARAM_INGESTURI);
    if (ingestURI == null)
      ingestURI = "http://localhost:7031/HTTPIngest";

    String realm = parameters.getParameter(org.apache.manifoldcf.agents.output.gts.GTSConfig.PARAM_REALM);
    if (realm == null)
      realm = "";

    String userID = parameters.getParameter(org.apache.manifoldcf.agents.output.gts.GTSConfig.PARAM_USERID);
    if (userID == null)
      userID = "";
		
    String password = parameters.getObfuscatedParameter(org.apache.manifoldcf.agents.output.gts.GTSConfig.PARAM_PASSWORD);
    if (password == null)
      password = "";
		
    // "Appliance" tab
    if (tabName.equals(Messages.getString(locale,"GTSConnector.Appliance")))
    {
      out.print(
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.IngestURI") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"ingesturi\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(ingestURI)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.Realm") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"realm\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(realm)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.UserID") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"userid\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userID)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.Password") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Appliance tab hiddens
      out.print("\n"+
"<input type=\"hidden\" name=\"ingesturi\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(ingestURI)+"\"/>\n"+
"<input type=\"hidden\" name=\"userid\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userID)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"
      );
    }
  }
  
  /** Process a configuration post.
  * This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    String ingestURI = variableContext.getParameter("ingesturi");
    if (ingestURI != null)
      parameters.setParameter(org.apache.manifoldcf.agents.output.gts.GTSConfig.PARAM_INGESTURI,ingestURI);

    String realm = variableContext.getParameter("realm");
    if (realm != null)
      parameters.setParameter(org.apache.manifoldcf.agents.output.gts.GTSConfig.PARAM_REALM,realm);

    String userID = variableContext.getParameter("userid");
    if (userID != null)
      parameters.setParameter(org.apache.manifoldcf.agents.output.gts.GTSConfig.PARAM_USERID,userID);
		
    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter(org.apache.manifoldcf.agents.output.gts.GTSConfig.PARAM_PASSWORD,password);
    
    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    out.print(
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.Parameters") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"
    );
    
    Iterator iter = parameters.listParameters();
    while (iter.hasNext())
    {
      String param = (String)iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length() && param.substring(param.length()-"password".length()).equalsIgnoreCase("password"))
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=********</nobr><br/>\n"
        );
      }
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=&lt;"+Integer.toString(kmanager.getContents().length)+" certificate(s)&gt;</nobr><br/>\n"
        );
      }
      else
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
        );
      }
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );
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
    String seqPrefix = "s"+connectionSequenceNumber+"_";
    tabsArray.add(Messages.getString(locale,"GTSConnector.GTSCollections"));
    tabsArray.add(Messages.getString(locale,"GTSConnector.GTSTemplate"));
    out.print(
"\n"+
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function "+seqPrefix+"checkSpecification()\n"+
"{\n"+
"  if (editjob."+seqPrefix+"gts_collectionname.value.length > 230)\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"GTSConnector.CollectionNameMustBeLessThanOrEqualToCharacters") + "\");\n"+
"    editjob."+seqPrefix+"gts_collectionname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
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
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    int i = 0;
    String collectionName = null;
    String documentTemplate = null;
    while (i < os.getChildCount())
    {
      SpecificationNode sn = os.getChild(i++);
      if (sn.getType().equals(org.apache.manifoldcf.agents.output.gts.GTSConfig.NODE_COLLECTION))
      {
        collectionName = sn.getAttributeValue(org.apache.manifoldcf.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE);
      }
      else if (sn.getType().equals(org.apache.manifoldcf.agents.output.gts.GTSConfig.NODE_DOCUMENTTEMPLATE))
      {
        documentTemplate = sn.getAttributeValue(org.apache.manifoldcf.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE);
      }
    }
    if (collectionName == null)
      collectionName = "";
    if (documentTemplate == null)
      documentTemplate = "";

    // Collections tab
    if (tabName.equals(Messages.getString(locale,"GTSConnector.GTSCollections")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.CollectionName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\""+seqPrefix+"gts_collectionname\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(collectionName)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for collections
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"gts_collectionname\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(collectionName)+"\"/>\n"
      );
    }

    // Template tab
    if (tabName.equals(Messages.getString(locale,"GTSConnector.GTSTemplate")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.DocumentTemplate") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <textarea rows=\"10\" cols=\"96\" name=\""+seqPrefix+"gts_documenttemplate\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(documentTemplate)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for document template
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"gts_documenttemplate\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(documentTemplate)+"\"/>\n"
      );
    }
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
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    // Collection name
    String collectionName = variableContext.getParameter(seqPrefix+"gts_collectionname");
    if (collectionName != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode sn = os.getChild(i);
        if (sn.getType().equals(org.apache.manifoldcf.agents.output.gts.GTSConfig.NODE_COLLECTION))
          os.removeChild(i);
        else
          i++;
      }
      if (collectionName.length() > 0)
      {
        SpecificationNode newspec = new SpecificationNode(org.apache.manifoldcf.agents.output.gts.GTSConfig.NODE_COLLECTION);
        newspec.setAttribute(org.apache.manifoldcf.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE,collectionName);
        os.addChild(os.getChildCount(),newspec);
      }
    }

    // Document template
    String documentTemplate = variableContext.getParameter(seqPrefix+"gts_documenttemplate");
    if (documentTemplate != null)
    {
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode sn = os.getChild(i);
        if (sn.getType().equals(org.apache.manifoldcf.agents.output.gts.GTSConfig.NODE_DOCUMENTTEMPLATE))
          os.removeChild(i);
        else
          i++;
      }
      SpecificationNode newspec = new SpecificationNode(org.apache.manifoldcf.agents.output.gts.GTSConfig.NODE_DOCUMENTTEMPLATE);
      newspec.setAttribute(org.apache.manifoldcf.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE,documentTemplate);
      os.addChild(os.getChildCount(),newspec);
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
    int i = 0;
    String collectionName = null;
    String documentTemplate = null;
    while (i < os.getChildCount())
    {
      SpecificationNode sn = os.getChild(i++);
      if (sn.getType().equals(org.apache.manifoldcf.agents.output.gts.GTSConfig.NODE_COLLECTION))
      {
        collectionName = sn.getAttributeValue(org.apache.manifoldcf.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE);
      }
      else if (sn.getType().equals(org.apache.manifoldcf.agents.output.gts.GTSConfig.NODE_DOCUMENTTEMPLATE))
      {
        documentTemplate = sn.getAttributeValue(org.apache.manifoldcf.agents.output.gts.GTSConfig.ATTRIBUTE_VALUE);
      }
    }
    if (collectionName == null)
      collectionName = "";
    if (documentTemplate == null)
      documentTemplate = "";

    // Display collections
    out.print(
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.Collection") + "</nobr></td>\n"+
"    <td class=\"value\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(collectionName)+"</td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"GTSConnector.DocumentTemplate") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
    );
    if (documentTemplate == null || documentTemplate.length() == 0)
      out.println("None specified");
    else
    {
      out.print(
"        <textarea name=\"documenttemplate\" cols=\"96\" rows=\"5\" readonly=\"true\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(documentTemplate)+"</textarea>\n"

      );
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  }


  // Protected methods

  /** Fingerprint a file!
  * Pass in the name of the (local) temporary file that we should be looking at.
  * This method will read it as needed until the file has been identified (or found
  * to remain "unknown").
  * The code here has been lifted algorithmically from products/ShareCrawler/Fingerprinter.pas.
  */
  protected static int fingerprint(File file)
    throws ManifoldCFException
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
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
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
    throws ManifoldCFException
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
      throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
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
    appName = appName.toUpperCase(Locale.ROOT);
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
    StringBuilder sb = new StringBuilder();
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
