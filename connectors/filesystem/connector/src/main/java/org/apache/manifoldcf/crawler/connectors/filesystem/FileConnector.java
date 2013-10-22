/* $Id: FileConnector.java 995085 2010-09-08 15:13:38Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.filesystem;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.core.extmimemap.ExtensionMimeMap;
import java.util.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/** This is the "repository connector" for a file system.  It's a relative of the share crawler, and should have
* comparable basic functionality, with the exception of the ability to use ActiveDirectory and look at other shares.
*/
public class FileConnector extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: FileConnector.java 995085 2010-09-08 15:13:38Z kwright $";

  // Activities that we know about
  protected final static String ACTIVITY_READ = "read document";

  // Relationships we know about
  protected static final String RELATIONSHIP_CHILD = "child";

  // Activities list
  protected static final String[] activitiesList = new String[]{ACTIVITY_READ};

  // Parameters that this connector cares about
  // public final static String ROOTDIRECTORY = "rootdirectory";

  // Local data
  // protected File rootDirectory = null;

  /** Constructor.
  */
  public FileConnector()
  {
  }

  /** Tell the world what model this connector uses for getDocumentIdentifiers().
  * This must return a model value as specified above.
  *@return the model type value.
  */
  @Override
  public int getConnectorModel()
  {
    return MODEL_CHAINED_ADD_CHANGE;
  }

  /** Return the list of relationship types that this connector recognizes.
  *@return the list.
  */
  @Override
  public String[] getRelationshipTypes()
  {
    return new String[]{RELATIONSHIP_CHILD};
  }

  /** List the activities we might report on.
  */
  @Override
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** For any given document, list the bins that it is a member of.
  */
  @Override
  public String[] getBinNames(String documentIdentifier)
  {
/*
    // Note: This code is for testing, so we can see how documents behave when they are in various kinds of bin situations.
    // The testing model is that there are documents belonging to "SLOW", to "FAST", or both to "SLOW" and "FAST" bins.
    // The connector chooses which bins to assign a document to based on the identifier (which is the document's path), so
    // this is something that should NOT be duplicated by other connector implementers.
    if (documentIdentifier.indexOf("/BOTH/") != -1 || (documentIdentifier.indexOf("/SLOW/") != -1 && documentIdentifier.indexOf("/FAST/") != -1))
      return new String[]{"SLOW","FAST"};
    if (documentIdentifier.indexOf("/SLOW/") != -1)
      return new String[]{"SLOW"};
    if (documentIdentifier.indexOf("/FAST/") != -1)
      return new String[]{"FAST"};
*/
    return new String[]{""};
  }

  /** Convert a document identifier to a URI.  The URI is the URI that will be the unique key from
  * the search index, and will be presented to the user as part of the search results.
  *@param filePath is the document filePath.
  *@param repositoryPath is the document repositoryPath.
  *@return the document uri.
  */
  protected static String convertToWGETURI(String path)
    throws ManifoldCFException
  {
    //
    // Note well:  This MUST be a legal URI!!!
    try
    {
      StringBuffer sb = new StringBuffer();
      String[] tmp = path.split("/", 3);
      String scheme = "";
      String host = "";
      String other = "";
      if (tmp.length >= 1)
        scheme = tmp[0];
      else
        scheme = "http";
      if (tmp.length >= 2)
        host = tmp[1];
      else
        host = "localhost";
      if (tmp.length >= 3)
        other = "/" + tmp[2];
      else
        other = "/";
      return new URI(scheme + "://" + host + other).toURL().toString();
    }
    catch (java.net.MalformedURLException e)
    {
      throw new ManifoldCFException("Bad url: "+e.getMessage(),e);
    }
    catch (URISyntaxException e)
    {
      throw new ManifoldCFException("Bad url: "+e.getMessage(),e);
    }
  }
  
  /** Convert a document identifier to a URI.  The URI is the URI that will be the unique key from
  * the search index, and will be presented to the user as part of the search results.
  *@param documentIdentifier is the document identifier.
  *@return the document uri.
  */
  protected static String convertToURI(String documentIdentifier)
    throws ManifoldCFException
  {
    //
    // Note well:  This MUST be a legal URI!!!
    try
    {
      return new File(documentIdentifier).toURI().toURL().toString();
    }
    catch (java.io.IOException e)
    {
      throw new ManifoldCFException("Bad url",e);
    }
  }


  /** Given a document specification, get either a list of starting document identifiers (seeds),
  * or a list of changes (deltas), depending on whether this is a "crawled" connector or not.
  * These document identifiers will be loaded into the job's queue at the beginning of the
  * job's execution.
  * This method can return changes only (because it is provided a time range).  For full
  * recrawls, the start time is always zero.
  * Note that it is always ok to return MORE documents rather than less with this method.
  *@param spec is a document specification (that comes from the job).
  *@param startTime is the beginning of the time range to consider, inclusive.
  *@param endTime is the end of the time range to consider, exclusive.
  *@return the stream of local document identifiers that should be added to the queue.
  */
  @Override
  public IDocumentIdentifierStream getDocumentIdentifiers(DocumentSpecification spec, long startTime, long endTime)
    throws ManifoldCFException
  {
    return new IdentifierStream(spec);
  }


  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is therefore important to perform
  * as little work as possible here.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    int i = 0;
    
    String[] rval = new String[documentIdentifiers.length];
    i = 0;
    while (i < rval.length)
    {
      String documentIdentifier = documentIdentifiers[i];
      File file = new File(documentIdentifier);
      if (file.exists())
      {
        if (file.isDirectory())
        {
          // It's a directory.  The version ID will be the
          // last modified date.
          long lastModified = file.lastModified();
          rval[i] = new Long(lastModified).toString();

          // Signal that we don't have any versioning.
          // rval[i] = "";
        }
        else
        {
          // It's a file
          long fileLength = file.length();
          if (activities.checkLengthIndexable(fileLength))
          {
            // Get the file's modified date.
            long lastModified = file.lastModified();
            
            // Check if the path is to be converted.  We record that info in the version string so that we'll reindex documents whose
            // URI's change.
            String convertPath = findConvertPath(spec, file);
            StringBuilder sb = new StringBuilder();
            if (convertPath != null)
            {
              // Record the path.
              sb.append("+");
              pack(sb,convertPath,'+');
            }
            else
              sb.append("-");
            sb.append(new Long(lastModified).toString()).append(":").append(new Long(fileLength).toString());
            rval[i] = sb.toString();
          }
          else
            rval[i] = null;
        }
      }
      else
        rval[i] = null;
      i++;
    }
    return rval;
  }


  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param spec is the document specification.
  *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
  * should only find other references, and should not actually call the ingestion methods.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
    throws ManifoldCFException, ServiceInterruption
  {
    int i = 0;
    while (i < documentIdentifiers.length)
    {
      String version = versions[i];
      String documentIdentifier = documentIdentifiers[i];
      File file = new File(documentIdentifier);
      if (file.exists())
      {
        if (file.isDirectory())
        {
          // Queue up stuff for directory
          long startTime = System.currentTimeMillis();
          String errorCode = "OK";
          String errorDesc = null;
          String entityReference = documentIdentifier;
          try
          {
            try
            {
              File[] files = file.listFiles();
              if (files != null)
              {
                int j = 0;
                while (j < files.length)
                {
                  File f = files[j++];
                  String canonicalPath = f.getCanonicalPath();
                  if (checkInclude(f,canonicalPath,spec))
                    activities.addDocumentReference(canonicalPath,documentIdentifier,RELATIONSHIP_CHILD);
                }
              }
            }
            catch (IOException e)
            {
              errorCode = "IO ERROR";
              errorDesc = e.getMessage();
              throw new ManifoldCFException("IO Error: "+e.getMessage(),e);
            }
          }
          finally
          {
            activities.recordActivity(new Long(startTime),ACTIVITY_READ,null,entityReference,errorCode,errorDesc,null);
          }
        }
        else
        {
          if (!scanOnly[i])
          {
            // We've already avoided queuing documents that we don't want, based on file specifications.
            // We still need to check based on file data.
            if (checkIngest(file,spec))
            {
              
              /*
               * get filepathtouri value
               */
              String convertPath = null;
              if (version.length() > 0 && version.startsWith("+"))
              {
                StringBuilder unpack = new StringBuilder();
                unpack(unpack, version, 1, '+');
                convertPath = unpack.toString();
              }
              
              long startTime = System.currentTimeMillis();
              String errorCode = "OK";
              String errorDesc = null;
              Long fileLength = null;
              String entityDescription = documentIdentifier;
              try
              {
                // Ingest the document.
                try
                {
                  InputStream is = new FileInputStream(file);
                  try
                  {
                    long fileBytes = file.length();
                    RepositoryDocument data = new RepositoryDocument();
                    data.setBinary(is,fileBytes);
                    String fileName = file.getName();
                    data.setFileName(fileName);
                    data.setMimeType(mapExtensionToMimeType(fileName));
                    data.setModifiedDate(new Date(file.lastModified()));
                    String uri;
                    if (convertPath != null) {
                      // WGET-compatible input; convert back to external URI
                      uri = convertToWGETURI(convertPath);
                      data.addField("uri",uri);
                    } else {
                      uri = convertToURI(documentIdentifier);
                      data.addField("uri",file.toString());
                    }
                    // MHL for other metadata
                    activities.ingestDocument(documentIdentifier,version,uri,data);
                    fileLength = new Long(fileBytes);
                  }
                  finally
                  {
                    is.close();
                  }
                }
                catch (FileNotFoundException e)
                {
                  //skip. throw nothing.
                  Logging.connectors.debug("Skipping file due to " +e.getMessage());
                }
                catch (IOException e)
                {
                  errorCode = "IO ERROR";
                  errorDesc = e.getMessage();
                  throw new ManifoldCFException("IO Error: "+e.getMessage(),e);
                }
              }
              finally
              {
                activities.recordActivity(new Long(startTime),ACTIVITY_READ,fileLength,entityDescription,errorCode,errorDesc,null);
              }
            }
          }
        }
      }
      i++;
    }
  }

  /** This method finds the part of the path that should be converted to a URI.
  * Returns null if the path should not be converted.
  *@param spec is the document specification.
  *@param documentIdentifier is the document identifier.
  *@return the part of the path to be converted, or null.
  */
  protected static String findConvertPath(DocumentSpecification spec, File theFile)
  {
    String fullpath = theFile.getAbsolutePath().replaceAll("\\\\","/");
    for (int j = 0; j < spec.getChildCount(); j++)
    {
      SpecificationNode sn = spec.getChild(j);
      if (sn.getType().equals("startpoint"))
      {
        String path = sn.getAttributeValue("path").replaceAll("\\\\","/");
        String convertToURI = sn.getAttributeValue("converttouri");
        if (path.length() > 0 && convertToURI != null && convertToURI.equals("true"))
        {
          if (!path.endsWith("/"))
            path += "/";
          if (fullpath.startsWith(path))
            return fullpath.substring(path.length());
        }
      }
    }
    return null;
  }

  /** Map an extension to a mime type */
  protected static String mapExtensionToMimeType(String fileName)
  {
    int slashIndex = fileName.lastIndexOf("/");
    if (slashIndex != -1)
      fileName = fileName.substring(slashIndex+1);
    int dotIndex = fileName.lastIndexOf(".");
    if (dotIndex == -1)
      return null;
    return ExtensionMimeMap.mapToMimeType(fileName.substring(dotIndex+1).toLowerCase(java.util.Locale.ROOT));
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing document specification information for a job.  The two kinds of methods are accordingly treated differently,
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
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfigForSave()\n"+
"{\n"+
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
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
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
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext, ConfigParams parameters)
    throws ManifoldCFException
  {
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
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
  }
  
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"FileConnector.Paths"));

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkSpecification()\n"+
"{\n"+
"  // Does nothing right now.\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException
  {
    int i;
    int k;

    // Paths tab
    if (tabName.equals(Messages.getString(locale,"FileConnector.Paths")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"3\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"FileConnector.Paths2") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.RootPath") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.ConvertToURI") + "<br/>" + Messages.getBodyString(locale,"FileConnector.ConvertToURIExample")+ "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.Rules") + "</nobr></td>\n"+
"        </tr>\n"
      );
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String pathDescription = "_"+Integer.toString(k);
          String pathOpName = "specop"+pathDescription;

          String path = sn.getAttributeValue("path");
          String convertToURIString = sn.getAttributeValue("converttouri");

          boolean convertToURI = false;
          if (convertToURIString != null && convertToURIString.equals("true"))
            convertToURI = true;

          out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"            <input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))+"\"/>\n"+
"            <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FileConnector.Delete") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")' alt=\""+Messages.getAttributeString(locale,"FileConnector.DeletePath")+Integer.toString(k)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <input type=\"hidden\" name=\"converttouri"+pathDescription+"\" value=\""+(convertToURI?"true":"false")+"\">\n"+
"            <nobr>\n"+
"              "+(convertToURI?Messages.getBodyString(locale,"FileConnector.Yes"):Messages.getBodyString(locale,"FileConnector.No"))+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <input type=\"hidden\" name=\""+"specchildcount"+pathDescription+"\" value=\""+Integer.toString(sn.getChildCount())+"\"/>\n"+
"            <table class=\"formtable\">\n"+
"              <tr class=\"formheaderrow\">\n"+
"                <td class=\"formcolumnheader\"></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.IncludeExclude") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.FileDirectory") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.Match") + "</nobr></td>\n"+
"              </tr>\n"
          );
          int j = 0;
          while (j < sn.getChildCount())
          {
            SpecificationNode excludeNode = sn.getChild(j);
            String instanceDescription = "_"+Integer.toString(k)+"_"+Integer.toString(j);
            String instanceOpName = "specop" + instanceDescription;

            String nodeFlavor = excludeNode.getType();
            String nodeType = excludeNode.getAttributeValue("type");
            String nodeMatch = excludeNode.getAttributeValue("match");
            out.print(
"              <tr class=\"evenformrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FileConnector.InsertHere") + "\" onClick='Javascript:SpecOp(\"specop"+instanceDescription+"\",\"Insert Here\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")' alt=\""+Messages.getAttributeString(locale,"FileConnector.InsertNewMatchForPath")+Integer.toString(k)+" before position #"+Integer.toString(j)+"\"/>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+"specflavor"+instanceDescription+"\">\n"+
"                      <option value=\"include\">" + Messages.getBodyString(locale,"FileConnector.include") + "</option>\n"+
"                      <option value=\"exclude\">" + Messages.getBodyString(locale,"FileConnector.exclude") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+"spectype"+instanceDescription+"\">\n"+
"                      <option value=\"file\">" + Messages.getBodyString(locale,"FileConnector.File") + "</option>\n"+
"                      <option value=\"directory\">" + Messages.getBodyString(locale,"FileConnector.Directory") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"text\" size=\"10\" name=\""+"specmatch"+instanceDescription+"\" value=\"\"/>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"              </tr>\n"+
"              <tr class=\"oddformrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"hidden\" name=\""+"specop"+instanceDescription+"\" value=\"\"/>\n"+
"                    <input type=\"hidden\" name=\""+"specfl"+instanceDescription+"\" value=\""+nodeFlavor+"\"/>\n"+
"                    <input type=\"hidden\" name=\""+"specty"+instanceDescription+"\" value=\""+nodeType+"\"/>\n"+
"                    <input type=\"hidden\" name=\""+"specma"+instanceDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nodeMatch)+"\"/>\n"+
"                    <a name=\""+"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\">\n"+
"                      <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FileConnector.Delete") + "\" onClick='Javascript:SpecOp(\"specop"+instanceDescription+"\",\"Delete\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\")' alt=\""+Messages.getAttributeString(locale,"FileConnector.DeletePath")+Integer.toString(k)+", match spec #"+Integer.toString(j)+"\"/>\n"+
"                    </a>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+nodeFlavor+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+nodeType+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(nodeMatch)+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"              </tr>\n"
            );
            j++;
          }
          if (j == 0)
          {
            out.print(
"              <tr class=\"formrow\"><td class=\"formcolumnmessage\" colspan=\"4\">" + Messages.getBodyString(locale,"FileConnector.NoRulesDefined") + "</td></tr>\n"
            );
          }
          out.print(
"              <tr class=\"formrow\"><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"              <tr class=\"formrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <a name=\""+"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\">\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FileConnector.Add") + "\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Add\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")' alt=\""+Messages.getAttributeString(locale,"FileConnector.AddNewMatchForPath")+Integer.toString(k)+"\"/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+"specflavor"+pathDescription+"\">\n"+
"                      <option value=\"include\">" + Messages.getBodyString(locale,"FileConnector.include") + "</option>\n"+
"                      <option value=\"exclude\">" + Messages.getBodyString(locale,"FileConnector.exclude") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <select name=\""+"spectype"+pathDescription+"\">\n"+
"                      <option value=\"file\">" + Messages.getBodyString(locale,"FileConnector.File") + "</option>\n"+
"                      <option value=\"directory\">" + Messages.getBodyString(locale,"FileConnector.Directory") + "</option>\n"+
"                    </select>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    <input type=\"text\" size=\"10\" name=\""+"specmatch"+pathDescription+"\" value=\"\"/>\n"+
"                  </nobr>\n"+
"                </td>\n"+
"              </tr>\n"+
"            </table>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formcolumnmessage\" colspan=\"4\">" + Messages.getBodyString(locale,"FileConnector.NoDocumentsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"                <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"FileConnector.Add") + "\" onClick='Javascript:SpecOp(\"specop\",\"Add\",\"path_"+Integer.toString(i+1)+"\")' alt=\"" + Messages.getAttributeString(locale,"FileConnector.AddNewPath") + "\"/>\n"+
"                <input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"+
"                <input type=\"hidden\" name=\"specop\" value=\"\"/>\n"+
"              </a>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input type=\"text\" size=\"30\" name=\"specpath\" value=\"\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              <input name=\"converttouri\" type=\"checkbox\" value=\"true\"/>\n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String pathDescription = "_"+Integer.toString(k);

          String path = sn.getAttributeValue("path");
          String convertToURIString = sn.getAttributeValue("converttouri");

          boolean convertToURI = false;
          if (convertToURIString != null && convertToURIString.equals("true"))
            convertToURI = true;

          out.print(
"<input type=\"hidden\" name=\"specpath"+pathDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(path)+"\"/>\n"+
"<input type=\"hidden\" name=\"converttouri"+pathDescription+"\" value=\""+(convertToURI?"true":"false")+"\">\n"+
"<input type=\"hidden\" name=\"specchildcount"+pathDescription+"\" value=\""+Integer.toString(sn.getChildCount())+"\"/>\n"
          );

          int j = 0;
	  while (j < sn.getChildCount())
	  {
            SpecificationNode excludeNode = sn.getChild(j);
            String instanceDescription = "_"+Integer.toString(k)+"_"+Integer.toString(j);

            String nodeFlavor = excludeNode.getType();
            String nodeType = excludeNode.getAttributeValue("type");
            String nodeMatch = excludeNode.getAttributeValue("match");
            out.print(
"<input type=\"hidden\" name=\"specfl"+instanceDescription+"\" value=\""+nodeFlavor+"\"/>\n"+
"<input type=\"hidden\" name=\"specty"+instanceDescription+"\" value=\""+nodeType+"\"/>\n"+
"<input type=\"hidden\" name=\"specma"+instanceDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(nodeMatch)+"\"/>\n"
            );
            j++;
          }
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }
    
  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the document specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param ds is the current document specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException
  {
    String x = variableContext.getParameter("pathcount");
    if (x != null)
    {
      ds.clearChildren();
      // Find out how many children were sent
      int pathCount = Integer.parseInt(x);
      // Gather up these
      int i = 0;
      int k = 0;
      while (i < pathCount)
      {
        String pathDescription = "_"+Integer.toString(i);
        String pathOpName = "specop"+pathDescription;
        x = variableContext.getParameter(pathOpName);
        if (x != null && x.equals("Delete"))
        {
          // Skip to the next
          i++;
          continue;
        }
        // Path inserts won't happen until the very end
        String path = variableContext.getParameter("specpath"+pathDescription);
        String convertToURI = variableContext.getParameter("converttouri"+pathDescription);

        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        if (convertToURI != null)
          node.setAttribute("converttouri",convertToURI);

        // Now, get the number of children
        String y = variableContext.getParameter("specchildcount"+pathDescription);
        int childCount = Integer.parseInt(y);
        int j = 0;
        int w = 0;
        while (j < childCount)
        {
          String instanceDescription = "_"+Integer.toString(i)+"_"+Integer.toString(j);
          // Look for an insert or a delete at this point
          String instanceOp = "specop"+instanceDescription;
          String z = variableContext.getParameter(instanceOp);
          String flavor;
          String type;
          String match;
          SpecificationNode sn;
          if (z != null && z.equals("Delete"))
          {
            // Process the deletion as we gather
            j++;
            continue;
          }
          if (z != null && z.equals("Insert Here"))
          {
            // Process the insertion as we gather.
            flavor = variableContext.getParameter("specflavor"+instanceDescription);
            type = variableContext.getParameter("spectype"+instanceDescription);
            match = variableContext.getParameter("specmatch"+instanceDescription);
            sn = new SpecificationNode(flavor);
            sn.setAttribute("type",type);
            sn.setAttribute("match",match);
            node.addChild(w++,sn);
          }
          flavor = variableContext.getParameter("specfl"+instanceDescription);
          type = variableContext.getParameter("specty"+instanceDescription);
          match = variableContext.getParameter("specma"+instanceDescription);
          sn = new SpecificationNode(flavor);
          sn.setAttribute("type",type);
          sn.setAttribute("match",match);
          node.addChild(w++,sn);
          j++;
        }
        if (x != null && x.equals("Add"))
        {
          // Process adds to the end of the rules in-line
          String match = variableContext.getParameter("specmatch"+pathDescription);
          String type = variableContext.getParameter("spectype"+pathDescription);
          String flavor = variableContext.getParameter("specflavor"+pathDescription);
          SpecificationNode sn = new SpecificationNode(flavor);
          sn.setAttribute("type",type);
          sn.setAttribute("match",match);
          node.addChild(w,sn);
        }
        ds.addChild(k++,node);
        i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter("specop");
      if (op != null && op.equals("Add"))
      {
        String path = variableContext.getParameter("specpath");
        String convertToURI = variableContext.getParameter("converttouri");

        SpecificationNode node = new SpecificationNode("startpoint");
        node.setAttribute("path",path);
        if (convertToURI != null)
          node.setAttribute("converttouri",convertToURI);

        // Now add in the defaults; these will be "include all directories" and "include all files".
        SpecificationNode sn = new SpecificationNode("include");
        sn.setAttribute("type","file");
        sn.setAttribute("match","*");
        node.addChild(node.getChildCount(),sn);
        sn = new SpecificationNode("include");
        sn.setAttribute("type","directory");
        sn.setAttribute("match","*");
        node.addChild(node.getChildCount(),sn);

        ds.addChild(k,node);
      }
    }
    
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\">" + Messages.getAttributeString(locale,"FileConnector.Paths2") + "</td>\n"+    
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.RootPath") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.ConvertToURI") + "<br/>" + Messages.getBodyString(locale,"FileConnector.ConvertToURIExample")+ "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.Rules") + "</nobr></td>\n"+
"        </tr>\n"
    );
    
    int k = 0;
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("startpoint"))
      {
        String path = sn.getAttributeValue("path");
        String convertToURIString = sn.getAttributeValue("converttouri");
        boolean convertToURI = false;
        if (convertToURIString != null && convertToURIString.equals("true"))
          convertToURI = true;
        
        out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(path)+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>\n"+
"              "+(convertToURI?Messages.getBodyString(locale,"FileConnector.Yes"):Messages.getBodyString(locale,"FileConnector.No"))+" \n"+
"            </nobr>\n"+
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <table class=\"formtable\">\n"+
"              <tr class=\"formheaderrow\">\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.IncludeExclude") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.FileDirectory") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"FileConnector.Match") + "</nobr></td>\n"+
"              </tr>\n"
        );
        
        int l = 0;
        for (int j = 0; j < sn.getChildCount(); j++)
        {
          SpecificationNode excludeNode = sn.getChild(j);

          String nodeFlavor = excludeNode.getType();
          String nodeType = excludeNode.getAttributeValue("type");
          String nodeMatch = excludeNode.getAttributeValue("match");
          out.print(
"              <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+nodeFlavor+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+nodeType+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>\n"+
"                    "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(nodeMatch)+"\n"+
"                  </nobr>\n"+
"                </td>\n"+
"              </tr>\n"
          );
          l++;
        }

        if (l == 0)
        {
          out.print(
"              <tr><td class=\"formcolumnmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"FileConnector.NoRulesDefined") + "</td></tr>\n"
          );
        }

        out.print(
"            </table>\n"+
"           </td>\n"
        );

        out.print(
"        </tr>\n"
        );

        k++;
      }
      
    }

    if (k == 0)
    {
      out.print(
"        <tr><td class=\"formcolumnmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"FileConnector.NoDocumentsSpecified") + "</td></tr>\n"
      );
    }
    
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"
    );

    out.print(
"</table>\n"
    );
    
  }

  // Protected static methods

  /** Check if a file or directory should be included, given a document specification.
  *@param fileName is the canonical file name.
  *@param documentSpecification is the specification.
  *@return true if it should be included.
  */
  protected static boolean checkInclude(File file, String fileName, DocumentSpecification documentSpecification)
    throws ManifoldCFException
  {
    if (Logging.connectors.isDebugEnabled())
    {
      Logging.connectors.debug("Checking whether to include file '"+fileName+"'");
    }

    try
    {
      String pathPart;
      String filePart;
      if (file.isDirectory())
      {
        pathPart = fileName;
        filePart = null;
      }
      else
      {
        pathPart = file.getParentFile().getCanonicalPath();
        filePart = file.getName();
      }

      // Scan until we match a startpoint
      int i = 0;
      while (i < documentSpecification.getChildCount())
      {
        SpecificationNode sn = documentSpecification.getChild(i++);
        if (sn.getType().equals("startpoint"))
        {
          String path = new File(sn.getAttributeValue("path")).getCanonicalPath();
          if (Logging.connectors.isDebugEnabled())
          {
            Logging.connectors.debug("Checking path '"+path+"' against canonical '"+pathPart+"'");
          }
          // Compare with filename
          int matchEnd = matchSubPath(path,pathPart);
          if (matchEnd == -1)
          {
            if (Logging.connectors.isDebugEnabled())
            {
              Logging.connectors.debug("Match check '"+path+"' against canonical '"+pathPart+"' failed");
            }

            continue;
          }
          // matchEnd is the start of the rest of the path (after the match) in fileName.
          // We need to walk through the rules and see whether it's in or out.
          int j = 0;
          while (j < sn.getChildCount())
          {
            SpecificationNode node = sn.getChild(j++);
            String flavor = node.getType();
            String match = node.getAttributeValue("match");
            String type = node.getAttributeValue("type");
            // If type is "file", then our match string is against the filePart.
            // If filePart is null, then this rule is simply skipped.
            String sourceMatch;
            int sourceIndex;
            if (type.equals("file"))
            {
              if (filePart == null)
                continue;
              sourceMatch = filePart;
              sourceIndex = 0;
            }
            else
            {
              if (filePart != null)
                continue;
              sourceMatch = pathPart;
              sourceIndex = matchEnd;
            }

            if (flavor.equals("include"))
            {
              if (checkMatch(sourceMatch,sourceIndex,match))
                return true;
            }
            else if (flavor.equals("exclude"))
            {
              if (checkMatch(sourceMatch,sourceIndex,match))
                return false;
            }
          }
        }
      }
      if (Logging.connectors.isDebugEnabled())
      {
        Logging.connectors.debug("Not including '"+fileName+"' because no matching rules");
      }

      return false;
    }
    catch (IOException e)
    {
      throw new ManifoldCFException("IO Error",e);
    }
  }

  /** Check if a file should be ingested, given a document specification.  It is presumed that
  * documents that do not pass checkInclude() will be checked with this method.
  *@param file is the file.
  *@param documentSpecification is the specification.
  */
  protected static boolean checkIngest(File file, DocumentSpecification documentSpecification)
    throws ManifoldCFException
  {
    // Since the only exclusions at this point are not based on file contents, this is a no-op.
    // MHL
    return true;
  }

  /** Match a sub-path.  The sub-path must match the complete starting part of the full path, in a path
  * sense.  The returned value should point into the file name beyond the end of the matched path, or
  * be -1 if there is no match.
  *@param subPath is the sub path.
  *@param fullPath is the full path.
  *@return the index of the start of the remaining part of the full path, or -1.
  */
  protected static int matchSubPath(String subPath, String fullPath)
  {
    if (subPath.length() > fullPath.length())
      return -1;
    if (fullPath.startsWith(subPath) == false)
      return -1;
    int rval = subPath.length();
    if (fullPath.length() == rval)
      return rval;
    char x = fullPath.charAt(rval);
    if (x == File.separatorChar)
      rval++;
    return rval;
  }

  /** Check a match between two strings with wildcards.
  *@param sourceMatch is the expanded string (no wildcards)
  *@param sourceIndex is the starting point in the expanded string.
  *@param match is the wildcard-based string.
  *@return true if there is a match.
  */
  protected static boolean checkMatch(String sourceMatch, int sourceIndex, String match)
  {
    // Note: The java regex stuff looks pretty heavyweight for this purpose.
    // I've opted to try and do a simple recursive version myself, which is not compiled.
    // Basically, the match proceeds by recursive descent through the string, so that all *'s cause
    // recursion.
    boolean caseSensitive = true;

    return processCheck(caseSensitive, sourceMatch, sourceIndex, match, 0);
  }

  /** Recursive worker method for checkMatch.  Returns 'true' if there is a path that consumes both
  * strings in their entirety in a matched way.
  *@param caseSensitive is true if file names are case sensitive.
  *@param sourceMatch is the source string (w/o wildcards)
  *@param sourceIndex is the current point in the source string.
  *@param match is the match string (w/wildcards)
  *@param matchIndex is the current point in the match string.
  *@return true if there is a match.
  */
  protected static boolean processCheck(boolean caseSensitive, String sourceMatch, int sourceIndex,
    String match, int matchIndex)
  {
    // Logging.connectors.debug("Matching '"+sourceMatch+"' position "+Integer.toString(sourceIndex)+
    //      " against '"+match+"' position "+Integer.toString(matchIndex));

    // Match up through the next * we encounter
    while (true)
    {
      // If we've reached the end, it's a match.
      if (sourceMatch.length() == sourceIndex && match.length() == matchIndex)
        return true;
      // If one has reached the end but the other hasn't, no match
      if (match.length() == matchIndex)
        return false;
      if (sourceMatch.length() == sourceIndex)
      {
        if (match.charAt(matchIndex) != '*')
          return false;
        matchIndex++;
        continue;
      }
      char x = sourceMatch.charAt(sourceIndex);
      char y = match.charAt(matchIndex);
      if (!caseSensitive)
      {
        if (x >= 'A' && x <= 'Z')
          x -= 'A'-'a';
        if (y >= 'A' && y <= 'Z')
          y -= 'A'-'a';
      }
      if (y == '*')
      {
        // Wildcard!
        // We will recurse at this point.
        // Basically, we want to combine the results for leaving the "*" in the match string
        // at this point and advancing the source index, with skipping the "*" and leaving the source
        // string alone.
        return processCheck(caseSensitive,sourceMatch,sourceIndex+1,match,matchIndex) ||
          processCheck(caseSensitive,sourceMatch,sourceIndex,match,matchIndex+1);
      }
      if (y == '?' || x == y)
      {
        sourceIndex++;
        matchIndex++;
      }
      else
        return false;
    }
  }

  /** Document identifier stream.
  */
  protected static class IdentifierStream implements IDocumentIdentifierStream
  {
    protected String[] ids = null;
    protected int currentIndex = 0;

    public IdentifierStream(DocumentSpecification spec)
      throws ManifoldCFException
    {
      try
      {
        // Walk the specification for the "startpoint" types.  Amalgamate these into a list of strings.
        // Presume that all roots are startpoint nodes
        int i = 0;
        int j = 0;
        while (i < spec.getChildCount())
        {
          SpecificationNode n = spec.getChild(i);
          if (n.getType().equals("startpoint"))
            j++;
          i++;
        }
        ids = new String[j];
        i = 0;
        j = 0;
        while (i < ids.length)
        {
          SpecificationNode n = spec.getChild(i);
          if (n.getType().equals("startpoint"))
          {
            // The id returned MUST be in canonical form!!!
            ids[j] = new File(n.getAttributeValue("path")).getCanonicalPath();
            if (Logging.connectors.isDebugEnabled())
            {
              Logging.connectors.debug("Seed = '"+ids[j]+"'");
            }
            j++;
          }
          i++;
        }
      }
      catch (IOException e)
      {
        throw new ManifoldCFException("Could not get a canonical path",e);
      }
    }

    /** Get the next identifier.
    *@return the next document identifier, or null if there are no more.
    */
    public String getNextIdentifier()
      throws ManifoldCFException, ServiceInterruption
    {
      if (currentIndex == ids.length)
        return null;
      return ids[currentIndex++];
    }

    /** Close the stream.
    */
    public void close()
      throws ManifoldCFException
    {
      ids = null;
    }

  }

}
