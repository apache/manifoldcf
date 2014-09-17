/* $Id: SolrConnector.java 998081 2010-09-17 11:33:15Z kwright $ */

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
package org.apache.manifoldcf.agents.output.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.ConfigNode;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IDFactory;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IKeystoreManager;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.KeystoreManagerFactory;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.VersionContext;


/** This is the output connector for SOLR.  Currently, no frills.
*/
public class SolrConnector extends org.apache.manifoldcf.agents.output.BaseOutputConnector
{
  public static final String _rcsid = "@(#)$Id: SolrConnector.java 998081 2010-09-17 11:33:15Z kwright $";

  // Activities we log

  /** Ingestion activity */
  public final static String INGEST_ACTIVITY = "document ingest";
  /** Document removal activity */
  public final static String REMOVE_ACTIVITY = "document deletion";

  /** Local connection */
  protected HttpPoster poster = null;
  
  /** Expiration */
  protected long expirationTime = -1L;
  
  /** The allow attribute name */
  protected String allowAttributeName = "allow_token_";
  /** The deny attribute name */
  protected String denyAttributeName = "deny_token_";
  /** The maximum document length */
  protected Long maxDocumentLength = null;
  /** Included mime types string */
  protected String includedMimeTypesString = null;
  /** Included mime types */
  protected Map<String,String> includedMimeTypes = null;
  /** Excluded mime types string */
  protected String excludedMimeTypesString = null;
  /** Excluded mime types */
  protected Map<String,String> excludedMimeTypes = null;
  
  // Attributes going into Solr
  protected String idAttributeName = null;
  protected String modifiedDateAttributeName = null;
  protected String createdDateAttributeName = null;
  protected String indexedDateAttributeName = null;
  protected String fileNameAttributeName = null;
  protected String mimeTypeAttributeName = null;
  protected String contentAttributeName = null;
  /** Use extractiing update handler? */
  protected boolean useExtractUpdateHandler = true;
  
  /** Whether or not to commit */
  protected boolean doCommits = false;
  
  /** Idle connection expiration interval */
  protected final static long EXPIRATION_INTERVAL = 300000L;

  static
  {
    System.setProperty("jdk.xml.maxGeneralEntitySizeLimit",new Integer(Integer.MAX_VALUE).toString());
  }
  
  /** Constructor.
  */
  public SolrConnector()
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

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    if (poster != null)
    {
      if (expirationTime <= System.currentTimeMillis())
      {
        // Expire connection
        poster.shutdown();
        poster = null;
        expirationTime = -1L;
      }
    }
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return poster != null;
  }

  /** Close the connection.  Call this before discarding the connection.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    if (poster != null)
    {
      poster.shutdown();
      poster = null;
      expirationTime = -1L;
    }
    maxDocumentLength = null;
    includedMimeTypesString = null;
    includedMimeTypes = null;
    excludedMimeTypesString = null;
    excludedMimeTypes = null;
    idAttributeName = null;
    modifiedDateAttributeName = null;
    createdDateAttributeName = null;
    indexedDateAttributeName = null;
    fileNameAttributeName = null;
    mimeTypeAttributeName = null;
    contentAttributeName = null;
    useExtractUpdateHandler = true;
    super.disconnect();
  }

  /** Set up a session */
  protected void getSession()
    throws ManifoldCFException
  {
    if (poster == null)
    {
      String updatePath = params.getParameter(SolrConfig.PARAM_UPDATEPATH);
      if (updatePath == null || updatePath.length() == 0)
        updatePath = "";

      String removePath = params.getParameter(SolrConfig.PARAM_REMOVEPATH);
      if (removePath == null || removePath.length() == 0)
        removePath = "";

      String statusPath = params.getParameter(SolrConfig.PARAM_STATUSPATH);
      if (statusPath == null || statusPath.length() == 0)
        statusPath = "";

      idAttributeName = params.getParameter(SolrConfig.PARAM_IDFIELD);
      if (idAttributeName == null || idAttributeName.length() == 0)
        idAttributeName = "id";

      modifiedDateAttributeName = params.getParameter(SolrConfig.PARAM_MODIFIEDDATEFIELD);
      if (modifiedDateAttributeName == null || modifiedDateAttributeName.length() == 0)
        modifiedDateAttributeName = null;

      createdDateAttributeName = params.getParameter(SolrConfig.PARAM_CREATEDDATEFIELD);
      if (createdDateAttributeName == null || createdDateAttributeName.length() == 0)
        createdDateAttributeName = null;
  
      indexedDateAttributeName = params.getParameter(SolrConfig.PARAM_INDEXEDDATEFIELD);
      if (indexedDateAttributeName == null || indexedDateAttributeName.length() == 0)
        indexedDateAttributeName = null;

      fileNameAttributeName = params.getParameter(SolrConfig.PARAM_FILENAMEFIELD);
      if (fileNameAttributeName == null || fileNameAttributeName.length() == 0)
        fileNameAttributeName = null;

      mimeTypeAttributeName = params.getParameter(SolrConfig.PARAM_MIMETYPEFIELD);
      if (mimeTypeAttributeName == null || mimeTypeAttributeName.length() == 0)
        mimeTypeAttributeName = null;

      contentAttributeName = params.getParameter(SolrConfig.PARAM_CONTENTFIELD);
      if (contentAttributeName == null || contentAttributeName.length() == 0)
        contentAttributeName = null;
      
      String useExtractUpdateHandlerValue = params.getParameter(SolrConfig.PARAM_EXTRACTUPDATE);
      if (useExtractUpdateHandlerValue == null || useExtractUpdateHandlerValue.length() == 0)
        useExtractUpdateHandler = true;
      else
        useExtractUpdateHandler = !useExtractUpdateHandlerValue.equals("false");
      if (contentAttributeName == null && !useExtractUpdateHandler)
        throw new ManifoldCFException("Content attribute name required for non-extract-update indexing");

      String commits = params.getParameter(SolrConfig.PARAM_COMMITS);
      if (commits == null || commits.length() == 0)
        commits = "true";
      
      doCommits = commits.equals("true");
      
      String commitWithin = params.getParameter(SolrConfig.PARAM_COMMITWITHIN);
      if (commitWithin == null || commitWithin.length() == 0)
        commitWithin = null;
      
      String docMax = params.getParameter(SolrConfig.PARAM_MAXLENGTH);
      if (docMax == null || docMax.length() == 0)
        maxDocumentLength = null;
      else
        maxDocumentLength = new Long(docMax);
      if (maxDocumentLength == null && !useExtractUpdateHandler)
        throw new ManifoldCFException("Maximum document length required for non-extract-update indexing");
      
      includedMimeTypesString = params.getParameter(SolrConfig.PARAM_INCLUDEDMIMETYPES);
      if (includedMimeTypesString == null || includedMimeTypesString.length() == 0)
      {
        includedMimeTypesString = null;
        includedMimeTypes = null;
      }
      else
      {
        // Parse the included mime types
        includedMimeTypes = parseMimeTypes(includedMimeTypesString);
        if (includedMimeTypes.size() == 0)
        {
          includedMimeTypesString = null;
          includedMimeTypes = null;
        }
      }

      excludedMimeTypesString = params.getParameter(SolrConfig.PARAM_EXCLUDEDMIMETYPES);
      if (excludedMimeTypesString == null || excludedMimeTypesString.length() == 0)
      {
        excludedMimeTypesString = null;
        excludedMimeTypes = null;
      }
      else
      {
        // Parse the included mime types
        excludedMimeTypes = parseMimeTypes(excludedMimeTypesString);
        if (excludedMimeTypes.size() == 0)
        {
          excludedMimeTypesString = null;
          excludedMimeTypes = null;
        }
      }
      

      // Now, initialize Solr-j
      String solrType = params.getParameter(SolrConfig.PARAM_SOLR_TYPE);
      if (solrType == null)
        solrType = SolrConfig.SOLR_TYPE_STANDARD;

      if (solrType.equals(SolrConfig.SOLR_TYPE_STANDARD))
      {
        String userID = params.getParameter(SolrConfig.PARAM_USERID);
        String password = params.getObfuscatedParameter(SolrConfig.PARAM_PASSWORD);
        String realm = params.getParameter(SolrConfig.PARAM_REALM);
        String keystoreData = params.getParameter(SolrConfig.PARAM_KEYSTORE);
        IKeystoreManager keystoreManager;
        if (keystoreData != null)
          keystoreManager = KeystoreManagerFactory.make("",keystoreData);
        else
          keystoreManager = null;

        String protocol = params.getParameter(SolrConfig.PARAM_PROTOCOL);
        if (protocol == null || protocol.length() == 0)
          throw new ManifoldCFException("Missing parameter: "+SolrConfig.PARAM_PROTOCOL);

        String server = params.getParameter(SolrConfig.PARAM_SERVER);
        if (server == null || server.length() == 0)
          throw new ManifoldCFException("Missing parameter: "+SolrConfig.PARAM_SERVER);

        String port = params.getParameter(SolrConfig.PARAM_PORT);
        if (port == null || port.length() == 0)
          port = "80";

        String webapp = params.getParameter(SolrConfig.PARAM_WEBAPPNAME);
        if (webapp != null && webapp.length() == 0)
          webapp = null;

        String core = params.getParameter(SolrConfig.PARAM_CORE);
        if (core != null && core.length() == 0)
          core = null;

        // Pick up timeouts
        String socketTimeoutString = params.getParameter(SolrConfig.PARAM_SOCKET_TIMEOUT);
        if (socketTimeoutString == null)
          socketTimeoutString = "900";
        String connectTimeoutString = params.getParameter(SolrConfig.PARAM_CONNECTION_TIMEOUT);
        if (connectTimeoutString == null)
          connectTimeoutString = "60";
        
        try
        {
          int socketTimeout = Integer.parseInt(socketTimeoutString) * 1000;
          int connectTimeout = Integer.parseInt(connectTimeoutString) * 1000;
          
          poster = new HttpPoster(protocol,server,Integer.parseInt(port),webapp,core,
            connectTimeout,socketTimeout,
            updatePath,removePath,statusPath,realm,userID,password,
            allowAttributeName,denyAttributeName,idAttributeName,
            modifiedDateAttributeName,createdDateAttributeName,indexedDateAttributeName,
            fileNameAttributeName,mimeTypeAttributeName,contentAttributeName,
            keystoreManager,maxDocumentLength,commitWithin,useExtractUpdateHandler);
          
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException(e.getMessage());
        }

      }
      else if (solrType.equals(SolrConfig.SOLR_TYPE_SOLRCLOUD))
      {
        StringBuilder zookeeperString = new StringBuilder();
        // Pull together the zookeeper string describing the zookeeper nodes
        for (int i = 0; i < params.getChildCount(); i++)
        {
          ConfigurationNode cn = params.getChild(i);
          if (cn.getType().equals(SolrConfig.NODE_ZOOKEEPER))
          {
            if (zookeeperString.length() > 0)
              zookeeperString.append(",");
            zookeeperString.append(cn.getAttributeValue(SolrConfig.ATTR_HOST)).append(":").append(cn.getAttributeValue(SolrConfig.ATTR_PORT));
          }
        }
        
        String znodePath = params.getParameter(SolrConfig.PARAM_ZOOKEEPER_ZNODE_PATH);
        if (znodePath == null)
          znodePath = "";
        
        String zookeeperHost = zookeeperString.toString() + znodePath;
        
        // Get collection
        String collection = params.getParameter(SolrConfig.PARAM_COLLECTION);
        if (collection == null)
          collection = "collection1";

        // Pick up timeouts
        String zkClientTimeoutString = params.getParameter(SolrConfig.PARAM_ZOOKEEPER_CLIENT_TIMEOUT);
        if (zkClientTimeoutString == null)
          zkClientTimeoutString = "60";
        String zkConnectTimeoutString = params.getParameter(SolrConfig.PARAM_ZOOKEEPER_CONNECT_TIMEOUT);
        if (zkConnectTimeoutString == null)
          zkConnectTimeoutString = "60";
        
        // Create an httpposter
        try
        {
          int zkClientTimeout = Integer.parseInt(zkClientTimeoutString) * 1000;
          int zkConnectTimeout = Integer.parseInt(zkConnectTimeoutString) * 1000;
          
          poster = new HttpPoster(zookeeperHost,collection,
            zkClientTimeout,zkConnectTimeout,
            updatePath,removePath,statusPath,
            allowAttributeName,denyAttributeName,idAttributeName,
            modifiedDateAttributeName,createdDateAttributeName,indexedDateAttributeName,
            fileNameAttributeName,mimeTypeAttributeName,contentAttributeName,
            maxDocumentLength,commitWithin,useExtractUpdateHandler);
          
        }
        catch (NumberFormatException e)
        {
          throw new ManifoldCFException(e.getMessage());
        }

      }
      else
        throw new ManifoldCFException("Illegal value for parameter '"+SolrConfig.PARAM_SOLR_TYPE+"': '"+solrType+"'");
      
    }
    expirationTime = System.currentTimeMillis() + EXPIRATION_INTERVAL;
  }

  /** Parse a mime type field into individual mime types in a hash */
  protected static Map<String,String> parseMimeTypes(String mimeTypes)
    throws ManifoldCFException
  {
    Map<String,String> rval = new HashMap<String,String>();
    try
    {
      java.io.Reader str = new java.io.StringReader(mimeTypes);
      try
      {
        java.io.BufferedReader is = new java.io.BufferedReader(str);
        try
        {
          while (true)
          {
            String nextString = is.readLine();
            if (nextString == null)
              break;
            if (nextString.length() == 0)
              continue;
            rval.put(nextString,nextString);
          }
          return rval;
        }
        finally
        {
          is.close();
        }
      }
      finally
      {
        str.close();
      }
    }
    catch (java.io.IOException e)
    {
      throw new ManifoldCFException("IO error: "+e.getMessage(),e);
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
    getSession();
    SpecPacker sp = new SpecPacker(spec);
    return new VersionContext(sp.toPackedString(),params,spec);
  }

  private final static Set<String> acceptableMimeTypes = new HashSet<String>();
  static
  {
    acceptableMimeTypes.add("text/plain;charset=utf-8");
    acceptableMimeTypes.add("text/plain;charset=ascii");
    acceptableMimeTypes.add("text/plain;charset=us-ascii");
    acceptableMimeTypes.add("text/plain");
  }

  /** Detect if a mime type is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
  * unusable documents that will be passed to this output connector.
  *@param outputDescription is the document's output version.
  *@param mimeType is the mime type of the document.
  *@return true if the mime type is indexable by this connector.
  */
  @Override
  public boolean checkMimeTypeIndexable(String outputDescription, String mimeType)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    if (useExtractUpdateHandler)
    {
      if (includedMimeTypes != null && includedMimeTypes.get(mimeType) == null)
        return false;
      if (excludedMimeTypes != null && excludedMimeTypes.get(mimeType) != null)
        return false;
      return super.checkMimeTypeIndexable(outputDescription,mimeType);
    }
    return acceptableMimeTypes.contains(mimeType.toLowerCase(Locale.ROOT));
  }

  /** Pre-determine whether a document's length is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that are too long to be indexable.
  *@param outputDescription is the document's output version.
  *@param length is the length of the document.
  *@return true if the file is indexable.
  */
  @Override
  public boolean checkLengthIndexable(String outputDescription, long length)
    throws ManifoldCFException, ServiceInterruption
  {
    getSession();
    if (maxDocumentLength != null && length > maxDocumentLength.longValue())
      return false;
    return super.checkLengthIndexable(outputDescription,length);
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
  @Override
  public int addOrReplaceDocument(String documentURI, String outputDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    SpecPacker sp = new SpecPacker(outputDescription);

    // Establish a session
    getSession();

    // Now, go off and call the ingest API.
    if (poster.indexPost(documentURI,document,sp.getArgs(),sp.getMappings(),sp.keepAllMetadata(),authorityNameString,activities))
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
    poster.deletePost(documentURI,activities);
  }

  /** Notify the connector of a completed job.
  * This is meant to allow the connector to flush any internal data structures it has been keeping around, or to tell the output repository that this
  * is a good time to synchronize things.  It is called whenever a job is either completed or aborted.
  *@param activities is the handle to an object that the implementer of an output connector may use to perform operations, such as logging processing activity.
  */
  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
    throws ManifoldCFException, ServiceInterruption
  {
    // Establish a session
    getSession();
    
    // Do a commit post
    if (doCommits)
    {
      poster.commitPost();
    }
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
    tabsArray.add(Messages.getString(locale,"SolrConnector.SolrType"));
    tabsArray.add(Messages.getString(locale,"SolrConnector.Server"));
    tabsArray.add(Messages.getString(locale,"SolrConnector.Zookeeper"));
    tabsArray.add(Messages.getString(locale,"SolrConnector.Paths"));
    tabsArray.add(Messages.getString(locale,"SolrConnector.Schema"));
    tabsArray.add(Messages.getString(locale,"SolrConnector.Arguments"));
    tabsArray.add(Messages.getString(locale,"SolrConnector.Documents"));
    tabsArray.add(Messages.getString(locale,"SolrConnector.Commits"));

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function SolrDeleteCertificate(aliasName)\n"+
"{\n"+
"  editconnection.solrkeystorealias.value = aliasName;\n"+
"  editconnection.configop.value = \"Delete\";\n"+
"  postForm();\n"+
"}\n"+
"\n"+
"function SolrAddCertificate()\n"+
"{\n"+
"  if (editconnection.solrcertificate.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ChooseACertificateFile")+"\");\n"+
"    editconnection.solrcertificate.focus();\n"+
"  }\n"+
"  else\n"+
"  {\n"+
"    editconnection.configop.value = \"Add\";\n"+
"    postForm();\n"+
"  }\n"+
"}\n"+
"\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.servername.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.PleaseSupplyAValidSolrServerName")+"\");\n"+
"    editconnection.servername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.SolrServerPortMustBeAValidInteger")+"\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webappname.value != \"\" && editconnection.webappname.value.indexOf(\"/\") != -1)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.WebApplicationNameCannotHaveCharacters")+"\");\n"+
"    editconnection.webappname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.core.value != \"\" && editconnection.core.value.indexOf(\"/\") != -1)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.CoreNameCannotHaveCharacters")+"\");\n"+
"    editconnection.core.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webappname.value == \"\" && editconnection.core.value != \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.WebApplicationMustBeSpecifiedIfCoreIsSpecified")+"\");\n"+
"    editconnection.webappname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (!isInteger(editconnection.connectiontimeout.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ConnectionTimeoutMustBeInteger")+"\");\n"+
"    editconnection.connectiontimeout.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (!isInteger(editconnection.sockettimeout.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.SocketTimeoutMustBeInteger")+"\");\n"+
"    editconnection.sockettimeout.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.updatepath.value != \"\" && editconnection.updatepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.UpdatePathMustStartWithACharacter")+"\");\n"+
"    editconnection.updatepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.removepath.value != \"\" && editconnection.removepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.RemovePathMustStartWithACharacter")+"\");\n"+
"    editconnection.removepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.statuspath.value != \"\" && editconnection.statuspath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.StatusPathMustStartWithACharacter")+"\");\n"+
"    editconnection.statuspath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.maxdocumentlength.value != \"\" && !isInteger(editconnection.maxdocumentlength.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.MaximumDocumentLengthMustBAnInteger")+"\");\n"+
"    editconnection.maxdocumentlength.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.commitwithin.value != \"\" && !isInteger(editconnection.commitwithin.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.CommitWithinValueMustBeAnInteger")+"\");\n"+
"    editconnection.commitwithin.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.znodepath.value != \"\" && editconnection.znodepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ZnodePathMustStartWithACharacter")+"\");\n"+
"    editconnection.znodepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.servername.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.PleaseSupplyAValidSolrServerName")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Server")+"\");\n"+
"    editconnection.servername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.serverport.value != \"\" && !isInteger(editconnection.serverport.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.SolrServerPortMustBeAValidInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Server")+"\");\n"+
"    editconnection.serverport.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webappname.value != \"\" && editconnection.webappname.value.indexOf(\"/\") != -1)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.WebApplicationNameCannotHaveCharacters")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Server")+"\");\n"+
"    editconnection.webappname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.core.value != \"\" && editconnection.core.value.indexOf(\"/\") != -1)\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.CoreNameCannotHaveCharacters")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Server")+"\");\n"+
"    editconnection.core.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webappname.value == \"\" && editconnection.core.value != \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.WebApplicationMustBeSpecifiedIfCoreIsSpecified")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Server")+"\");\n"+
"    editconnection.webappname.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (!isInteger(editconnection.connectiontimeout.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ConnectionTimeoutMustBeInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Server")+"\");\n"+
"    editconnection.connectiontimeout.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (!isInteger(editconnection.sockettimeout.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.SocketTimeoutMustBeInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Server")+"\");\n"+
"    editconnection.sockettimeout.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.updatepath.value != \"\" && editconnection.updatepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.UpdatePathMustStartWithACharacter")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Paths")+"\");\n"+
"    editconnection.updatepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.removepath.value != \"\" && editconnection.removepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.RemovePathMustStartWithACharacter")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Paths")+"\");\n"+
"    editconnection.removepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.statuspath.value != \"\" && editconnection.statuspath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.StatusPathMustStartWithACharacter")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Paths")+"\");\n"+
"    editconnection.statuspath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.maxdocumentlength.value != \"\" && !isInteger(editconnection.maxdocumentlength.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.MaximumDocumentLengthMustBeAnInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Documents")+"\");\n"+
"    editconnection.maxdocumentlength.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.maxdocumentlength.value == \"\" && ((editconnection.extractupdatecheckbox.value == \"true\" && editconnection.extractupdate.checked == false) || (editconnection.extractupdatecheckbox.value != \"true\" && editconnection.extractupdate.value != \"true\")))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.MaximumDocumentLengthRequiredUnlessExtractingUpdateHandler")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Documents")+"\");\n"+
"    editconnection.maxdocumentlength.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.contentfield.value == \"\" && ((editconnection.extractupdatecheckbox.value == \"true\" && editconnection.extractupdate.checked == false) || (editconnection.extractupdatecheckbox.value != \"true\" && editconnection.extractupdate.value != \"true\")))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ContentFieldNameRequiredUnlessExtractingUpdateHandler")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Schema")+"\");\n"+
"    editconnection.contentfield.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.commitwithin.value != \"\" && !isInteger(editconnection.commitwithin.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.CommitWithinValueMustBeAnInteger")+"\");\n"+
"    SelectTab(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.Commits")+"\");\n"+
"    editconnection.commitwithin.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.znodepath.value != \"\" && editconnection.znodepath.value.substring(0,1) != \"/\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ZnodePathMustStartWithACharacter")+"\");\n"+
"    editconnection.znodepath.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function deleteZookeeperHost(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.op_zookeeper_\"+i+\".value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editconnection.count_zookeeper.value==i)\n"+
"    postFormSetAnchor(\"zookeeper\");\n"+
"  else\n"+
"    postFormSetAnchor(\"zookeeper_\"+i)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.op_zookeeper_\"+i+\".value=\\\"Continue\\\"\");\n"+
"}\n"+
"\n"+
"function addZookeeperHost()\n"+
"{\n"+
"  if (editconnection.host_zookeeper.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"SolrConnector.ZookeeperHostCannotBeNull")+"\");\n"+
"    editconnection.host_zookeeper.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (editconnection.port_zookeeper.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ZookeeperPortCannotBeNull")+"\");\n"+
"    editconnection.port_zookeeper.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (!isInteger(editconnection.port_zookeeper.value))\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ZookeeperPortMustBeAnInteger")+"\");\n"+
"    editconnection.port_zookeeper.focus();\n"+
"    return;\n"+
"  }\n"+
"  editconnection.op_zookeeper.value=\"Add\";\n"+
"  postFormSetAnchor(\"zookeeper\");\n"+
"}\n"+
"\n"+
"function deleteArgument(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editconnection.argument_\"+i+\"_op.value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editconnection.argument_count.value==i)\n"+
"    postFormSetAnchor(\"argument\");\n"+
"  else\n"+
"    postFormSetAnchor(\"argument_\"+i)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editconnection.argument_\"+i+\"_op.value=\\\"Continue\\\"\");\n"+
"}\n"+
"\n"+
"function addArgument()\n"+
"{\n"+
"  if (editconnection.argument_name.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.ArgumentNameCannotBeAnEmptyString")+"\");\n"+
"    editconnection.argument_name.focus();\n"+
"    return;\n"+
"  }\n"+
"  editconnection.argument_op.value=\"Add\";\n"+
"  postFormSetAnchor(\"argument\");\n"+
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
    String type = parameters.getParameter(SolrConfig.PARAM_SOLR_TYPE);
    if (type == null)
      type = SolrConfig.SOLR_TYPE_STANDARD;
    
    String protocol = parameters.getParameter(SolrConfig.PARAM_PROTOCOL);
    if (protocol == null)
      protocol = SolrConfig.PROTOCOL_TYPE_HTTP;
		
    String server = parameters.getParameter(SolrConfig.PARAM_SERVER);
    if (server == null)
      server = "localhost";

    String port = parameters.getParameter(SolrConfig.PARAM_PORT);
    if (port == null)
      port = "8983";

    String webapp = parameters.getParameter(SolrConfig.PARAM_WEBAPPNAME);
    if (webapp == null)
      webapp = "solr";

    String core = parameters.getParameter(SolrConfig.PARAM_CORE);
    if (core == null)
      core = "";

    String znodePath = parameters.getParameter(SolrConfig.PARAM_ZOOKEEPER_ZNODE_PATH);
    if (znodePath == null)
    	znodePath = "";
    
    String collection = parameters.getParameter(SolrConfig.PARAM_COLLECTION);
    if (collection == null)
      collection = "collection1";
    
    String connectionTimeout = parameters.getParameter(SolrConfig.PARAM_CONNECTION_TIMEOUT);
    if (connectionTimeout == null)
      connectionTimeout = "60";
    
    String socketTimeout = parameters.getParameter(SolrConfig.PARAM_SOCKET_TIMEOUT);
    if (socketTimeout == null)
      socketTimeout = "900";

    String zkClientTimeout = parameters.getParameter(SolrConfig.PARAM_ZOOKEEPER_CLIENT_TIMEOUT);
    if (zkClientTimeout == null)
      zkClientTimeout = "60";

    String zkConnectTimeout = parameters.getParameter(SolrConfig.PARAM_ZOOKEEPER_CONNECT_TIMEOUT);
    if (zkConnectTimeout == null)
      zkConnectTimeout = "60";
    
    String updatePath = parameters.getParameter(SolrConfig.PARAM_UPDATEPATH);
    if (updatePath == null)
      updatePath = "/update/extract";

    String removePath = parameters.getParameter(SolrConfig.PARAM_REMOVEPATH);
    if (removePath == null)
      removePath = "/update";

    String statusPath = parameters.getParameter(SolrConfig.PARAM_STATUSPATH);
    if (statusPath == null)
      statusPath = "/admin/ping";

    String idField = parameters.getParameter(SolrConfig.PARAM_IDFIELD);
    if (idField == null)
      idField = "id";
    
    String modifiedDateField = parameters.getParameter(SolrConfig.PARAM_MODIFIEDDATEFIELD);
    if (modifiedDateField == null)
      modifiedDateField = "";
    
    String createdDateField = parameters.getParameter(SolrConfig.PARAM_CREATEDDATEFIELD);
    if (createdDateField == null)
      createdDateField = "";

    String indexedDateField = parameters.getParameter(SolrConfig.PARAM_INDEXEDDATEFIELD);
    if (indexedDateField == null)
      indexedDateField = "";
    
    String fileNameField = parameters.getParameter(SolrConfig.PARAM_FILENAMEFIELD);
    if (fileNameField == null)
      fileNameField = "";
    
    String mimeTypeField = parameters.getParameter(SolrConfig.PARAM_MIMETYPEFIELD);
    if (mimeTypeField == null)
      mimeTypeField = "";

    String contentField = parameters.getParameter(SolrConfig.PARAM_CONTENTFIELD);
    if (contentField == null)
      contentField = "";
    
    String useExtractUpdate = parameters.getParameter(SolrConfig.PARAM_EXTRACTUPDATE);
    if (useExtractUpdate == null || useExtractUpdate.length() == 0)
      useExtractUpdate = "true";

    String realm = parameters.getParameter(SolrConfig.PARAM_REALM);
    if (realm == null)
      realm = "";

    String userID = parameters.getParameter(SolrConfig.PARAM_USERID);
    if (userID == null)
      userID = "";
		
    String password = parameters.getObfuscatedParameter(SolrConfig.PARAM_PASSWORD);
    if (password == null)
      password = "";
    else
      password = out.mapPasswordToKey(password);
    
    String commits = parameters.getParameter(SolrConfig.PARAM_COMMITS);
    if (commits == null)
      commits = "true";
    
    String commitWithin = parameters.getParameter(SolrConfig.PARAM_COMMITWITHIN);
    if (commitWithin == null)
      commitWithin = "";

    String solrKeystore = parameters.getParameter(SolrConfig.PARAM_KEYSTORE);
    IKeystoreManager localKeystore;
    if (solrKeystore == null)
      localKeystore = KeystoreManagerFactory.make("");
    else
      localKeystore = KeystoreManagerFactory.make("",solrKeystore);

    String maxLength = parameters.getParameter(SolrConfig.PARAM_MAXLENGTH);
    if (maxLength == null)
      maxLength = "";
    
    String includedMimeTypes = parameters.getParameter(SolrConfig.PARAM_INCLUDEDMIMETYPES);
    if (includedMimeTypes == null)
      includedMimeTypes = "";
    
    String excludedMimeTypes = parameters.getParameter(SolrConfig.PARAM_EXCLUDEDMIMETYPES);
    if (excludedMimeTypes == null)
      excludedMimeTypes = "";
    
    // "SOLR type" tab
    if (tabName.equals(Messages.getString(locale,"SolrConnector.SolrType")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.SolrType2") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"solrtype\">\n"+
"        <option value=\""+SolrConfig.SOLR_TYPE_STANDARD+"\""+(type.equals(SolrConfig.SOLR_TYPE_STANDARD)?" selected=\"true\"":"")+">"+Messages.getBodyString(locale,"SolrConnector.SingleServer")+"</option>\n"+
"        <option value=\""+SolrConfig.SOLR_TYPE_SOLRCLOUD+"\""+(type.equals(SolrConfig.SOLR_TYPE_SOLRCLOUD)?" selected=\"true\"":"")+">"+Messages.getBodyString(locale,"SolrConnector.SolrCloud")+"</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Type tab hiddens
      out.print(
"<input type=\"hidden\" name=\"solrtype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(type)+"\"/>\n"
      );
    }

    // "Server" tab
    // Always pass the whole keystore as a hidden.
    if (solrKeystore != null)
    {
      out.print(
"<input type=\"hidden\" name=\"keystoredata\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(solrKeystore)+"\"/>\n"
      );
    }
    out.print(
"<input name=\"configop\" type=\"hidden\" value=\"Continue\"/>\n"
    );
    
    if (tabName.equals(Messages.getString(locale,"SolrConnector.Server")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Protocol") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <select name=\"serverprotocol\">\n"+
"        <option value=\""+SolrConfig.PROTOCOL_TYPE_HTTP+"\""+(protocol.equals(SolrConfig.PROTOCOL_TYPE_HTTP)?" selected=\"true\"":"")+">http</option>\n"+
"        <option value=\""+SolrConfig.PROTOCOL_TYPE_HTTPS+"\""+(protocol.equals(SolrConfig.PROTOCOL_TYPE_HTTPS)?" selected=\"true\"":"")+">https</option>\n"+
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ServerName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"servername\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(server)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Port") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"serverport\" type=\"text\" size=\"5\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(port)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.WebApplicationName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"webappname\" type=\"text\" size=\"16\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(webapp)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.CoreName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"core\" type=\"text\" size=\"16\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(core)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ConnectionTimeout") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"connectiontimeout\" type=\"text\" size=\"5\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionTimeout)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.SocketTimeout") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"sockettimeout\" type=\"text\" size=\"5\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(socketTimeout)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Realm") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"realm\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(realm)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.UserID") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"userid\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userID)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Password") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"password\" size=\"32\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.SSLTrustCertificateList") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"hidden\" name=\"solrkeystorealias\" value=\"\"/>\n"+
"      <table class=\"displaytable\">\n"
      );
      // List the individual certificates in the store, with a delete button for each
      String[] contents = localKeystore.getContents();
      if (contents.length == 0)
      {
        out.print(
"        <tr><td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.NoCertificatesPresent") + "</nobr></td></tr>\n"
        );
      }
      else
      {
        int i = 0;
        while (i < contents.length)
        {
          String alias = contents[i];
          String description = localKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0,125) + "...";
          out.print(
"        <tr>\n"+
"          <td class=\"value\"><input type=\"button\" onclick='Javascript:SolrDeleteCertificate(\""+org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(alias)+"\")' alt=\""+Messages.getAttributeString(locale,"SolrConnector.DeleteCert")+" "+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(alias)+"\" value=\"Delete\"/></td>\n"+
"          <td>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description)+"</td>\n"+
"        </tr>\n"
          );
          i++;
        }
      }
      out.print(
"      </table>\n"+
"      <input type=\"button\" onclick='Javascript:SolrAddCertificate()' alt=\"" + Messages.getAttributeString(locale,"SolrConnector.AddCert") + "\" value=\"" + Messages.getAttributeString(locale,"SolrConnector.Add") + "\"/>&nbsp;\n"+
"      " + Messages.getBodyString(locale,"SolrConnector.Certificate") + "&nbsp;<input name=\"solrcertificate\" size=\"50\" type=\"file\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Server tab hiddens
      out.print(
"<input type=\"hidden\" name=\"serverprotocol\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(protocol)+"\"/>\n"+
"<input type=\"hidden\" name=\"servername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(server)+"\"/>\n"+
"<input type=\"hidden\" name=\"serverport\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(port)+"\"/>\n"+
"<input type=\"hidden\" name=\"webappname\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(webapp)+"\"/>\n"+
"<input type=\"hidden\" name=\"core\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(core)+"\"/>\n"+
"<input type=\"hidden\" name=\"connectiontimeout\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(connectionTimeout)+"\"/>\n"+
"<input type=\"hidden\" name=\"sockettimeout\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(socketTimeout)+"\"/>\n"+
"<input type=\"hidden\" name=\"realm\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(realm)+"\"/>\n"+
"<input type=\"hidden\" name=\"userid\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userID)+"\"/>\n"+
"<input type=\"hidden\" name=\"password\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password)+"\"/>\n"
      );
    }

    // "Zookeeper" tab
    if (tabName.equals(Messages.getString(locale,"SolrConnector.Zookeeper")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ZookeeperHosts") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Host") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Port") + "</nobr></td>\n"+
"        </tr>\n"
      );

      // Loop through the existing zookeeper nodes
      int k = 0;
      for (int i = 0; i < parameters.getChildCount(); i++)
      {
        ConfigurationNode cn = parameters.getChild(i);
        if (cn.getType().equals(SolrConfig.NODE_ZOOKEEPER))
        {
          String host = cn.getAttributeValue(SolrConfig.ATTR_HOST);
          String zkport = cn.getAttributeValue(SolrConfig.ATTR_PORT);
          String postfix = "zookeeper_"+k;
          out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+postfix+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SolrConnector.Delete") + "\" alt=\""+Messages.getAttributeString(locale,"SolrConnector.DeleteZookeeperHost")+Integer.toString(k+1)+"\" onclick='javascript:deleteZookeeperHost("+Integer.toString(k)+");'/>\n"+
"              <input type=\"hidden\" name=\""+"op_"+postfix+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+"host_"+postfix+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(host)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+"port_"+postfix+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkport)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(host)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(zkport)+"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          k++;
        }
      }
      // If this looks like the first time through for this connection, add a default zookeeper setup.
      // Only works because after the first post, parameters always will have children.
      if (parameters.getChildCount() == 0)
      {
        String postfix = "zookeeper_"+k;
        out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+postfix+"\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SolrConnector.Delete") + "\" alt=\""+Messages.getAttributeString(locale,"SolrConnector.DeleteZookeeperHost")+Integer.toString(k+1)+"\" onclick='javascript:deleteZookeeperHost("+Integer.toString(k)+");'/>\n"+
"              <input type=\"hidden\" name=\""+"op_"+postfix+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+"host_"+postfix+"\" value=\"localhost\"/>\n"+
"              <input type=\"hidden\" name=\""+"port_"+postfix+"\" value=\"2181\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>localhost</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>2181</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
        );
        k++;
      }
      if (k == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">"+Messages.getBodyString(locale,"SolrConnector.NoZookeeperHostsSpecified")+"</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"zookeeper\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SolrConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"SolrConnector.AddZookeeperHost") + "\" onclick=\"javascript:addZookeeperHost();\"/>\n"+
"            </a>\n"+
"            <input type=\"hidden\" name=\"count_zookeeper\" value=\""+k+"\"/>\n"+
"            <input type=\"hidden\" name=\"op_zookeeper\" value=\"Continue\"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"host_zookeeper\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"5\" name=\"port_zookeeper\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ZnodePath") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"znodepath\" type=\"text\" size=\"16\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(znodePath)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.CollectionName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"collection\" type=\"text\" size=\"16\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(collection)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ZookeeperClientTimeout") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"zkclienttimeout\" type=\"text\" size=\"5\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkClientTimeout)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ZookeeperConnectTimeout") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"zkconnecttimeout\" type=\"text\" size=\"5\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkConnectTimeout)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Zookeeper tab
      int k = 0;
      for (int i = 0; i < parameters.getChildCount(); i++)
      {
        ConfigurationNode cn = parameters.getChild(i);
        if (cn.getType().equals(SolrConfig.NODE_ZOOKEEPER))
        {
          String host = cn.getAttributeValue(SolrConfig.ATTR_HOST);
          String zkport = cn.getAttributeValue(SolrConfig.ATTR_PORT);
          String postfix = "zookeeper_"+k;
          out.print(
"<input type=\"hidden\" name=\"host_"+postfix+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(host)+"\"/>\n"+
"<input type=\"hidden\" name=\"port_"+postfix+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkport)+"\"/>\n"
          );
          k++;
        }
      }
      if (parameters.getChildCount() == 0)
      {
        String postfix = "zookeeper_"+k;
        out.print(
"<input type=\"hidden\" name=\"host_"+postfix+"\" value=\"localhost\"/>\n"+
"<input type=\"hidden\" name=\"port_"+postfix+"\" value=\"2181\"/>\n"
        );
        k++;
      }
      out.print(
"<input type=\"hidden\" name=\"count_zookeeper\" value=\""+k+"\"/>\n"+
"<input type=\"hidden\" name=\"znodepath\" value=\""+znodePath+"\"/>\n"+
"<input type=\"hidden\" name=\"collection\" value=\""+collection+"\"/>\n"+
"<input type=\"hidden\" name=\"zkclienttimeout\" value=\""+zkClientTimeout+"\"/>\n"+
"<input type=\"hidden\" name=\"zkconnecttimeout\" value=\""+zkConnectTimeout+"\"/>\n"
      );
    }
    
    // "Paths" tab
    if (tabName.equals(Messages.getString(locale,"SolrConnector.Paths")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.UpdateHandler") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"updatepath\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(updatePath)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.RemoveHandler") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"removepath\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(removePath)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.StatusHandler") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"statuspath\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(statusPath)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Paths tab hiddens
      out.print(
"<input type=\"hidden\" name=\"updatepath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(updatePath)+"\"/>\n"+
"<input type=\"hidden\" name=\"removepath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(removePath)+"\"/>\n"+
"<input type=\"hidden\" name=\"statuspath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(statusPath)+"\"/>\n"
      );
    }
    
    // "Schema" tab
    if (tabName.equals(Messages.getString(locale,"SolrConnector.Schema")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.IDFieldName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"idfield\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(idField)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ModifiedDateFieldName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"modifieddatefield\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(modifiedDateField)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.CreatedDateFieldName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"createddatefield\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(createdDateField)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.IndexedDateFieldName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"indexeddatefield\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(indexedDateField)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.FileNameFieldName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"filenamefield\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(fileNameField)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.MimeTypeFieldName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"mimetypefield\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mimeTypeField)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.UseExtractUpdateHandler") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"extractupdatecheckbox\" type=\"hidden\" value=\"true\"/>\n"+
"      <input name=\"extractupdatepresent\" type=\"hidden\" value=\"true\"/>\n"
      );
      if (!useExtractUpdate.equals("false"))
      {
        out.print(
"      <input name=\"extractupdate\" type=\"checkbox\" value=\"true\" checked=\"true\"/>\n"
        );
      }
      else
      {
        out.print(
"      <input name=\"extractupdate\" type=\"checkbox\" value=\"true\"/>\n"
        );
      }
      out.print(
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ContentFieldName") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"contentfield\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(contentField)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"idfield\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(idField)+"\"/>\n"+
"<input type=\"hidden\" name=\"modifieddatefield\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(modifiedDateField)+"\"/>\n"+
"<input type=\"hidden\" name=\"createddatefield\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(createdDateField)+"\"/>\n"+
"<input type=\"hidden\" name=\"indexeddatefield\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(indexedDateField)+"\"/>\n"+
"<input type=\"hidden\" name=\"filenamefield\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(fileNameField)+"\"/>\n"+
"<input type=\"hidden\" name=\"mimetypefield\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(mimeTypeField)+"\"/>\n"+
"<input type=\"hidden\" name=\"contentfield\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(contentField)+"\"/>\n"+
"<input name=\"extractupdatecheckbox\" type=\"hidden\" value=\"false\"/>\n"+
"<input type=\"hidden\" name=\"extractupdate\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(useExtractUpdate)+"\"/>\n"+
"<input name=\"extractupdatepresent\" type=\"hidden\" value=\"true\"/>\n"
      );
    }
    
    // "Documents" tab
    if (tabName.equals(Messages.getString(locale,"SolrConnector.Documents")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.MaximumDocumentLength") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"maxdocumentlength\" type=\"text\" size=\"16\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(maxLength)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.IncludedMimeTypes") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <textarea rows=\"10\" cols=\"20\" name=\"includedmimetypes\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(includedMimeTypes)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ExcludedMimeTypes") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <textarea rows=\"10\" cols=\"20\" name=\"excludedmimetypes\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(excludedMimeTypes)+"</textarea>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"maxdocumentlength\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(maxLength)+"\"/>\n"+
"<input type=\"hidden\" name=\"includedmimetypes\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(includedMimeTypes)+"\"/>\n"+
"<input type=\"hidden\" name=\"excludedmimetypes\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(excludedMimeTypes)+"\"/>\n"
      );
    }
    
    // "Commits" tab
    if (tabName.equals(Messages.getString(locale,"SolrConnector.Commits")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.CommitAtEndOfEveryJob") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"commits_present\" type=\"hidden\" value=\"true\"/>\n"+
"      <input name=\"commits\" type=\"checkbox\" value=\"true\""+(commits.equals("true")?" checked=\"yes\"":"")+"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.CommitEachDocumentWithin") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"commitwithin\" type=\"text\" size=\"16\" value=\""+commitWithin+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"commits_present\" value=\"true\"/>\n"+
"<input name=\"commits\" type=\"hidden\" value=\""+commits+"\"/>\n"+
"<input name=\"commitwithin\" type=\"hidden\" value=\""+commitWithin+"\"/>\n"
      );
    }

    // Prepare for the argument tab
    Map argumentMap = new HashMap();
    int i = 0;
    while (i < parameters.getChildCount())
    {
      ConfigNode sn = parameters.getChild(i++);
      if (sn.getType().equals(SolrConfig.NODE_ARGUMENT))
      {
        String name = sn.getAttributeValue(SolrConfig.ATTRIBUTE_NAME);
        String value = sn.getAttributeValue(SolrConfig.ATTRIBUTE_VALUE);
        ArrayList values = (ArrayList)argumentMap.get(name);
        if (values == null)
        {
          values = new ArrayList();
          argumentMap.put(name,values);
        }
        values.add(value);
      }
    }
    
    // "Arguments" tab
    if (tabName.equals(Messages.getString(locale,"SolrConnector.Arguments")))
    {
      // For the display, sort the arguments into alphabetic order
      String[] sortArray = new String[argumentMap.size()];
      i = 0;
      Iterator iter = argumentMap.keySet().iterator();
      while (iter.hasNext())
      {
        sortArray[i++] = (String)iter.next();
      }
      java.util.Arrays.sort(sortArray);
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Arguments2") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Name") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Value") + "</nobr></td>\n"+
"        </tr>\n"
      );
      i = 0;
      int k = 0;
      while (k < sortArray.length)
      {
        String name = sortArray[k++];
        ArrayList values = (ArrayList)argumentMap.get(name);
        int j = 0;
        while (j < values.size())
        {
          String value = (String)values.get(j++);
          // Its prefix will be...
          String prefix = "argument_" + Integer.toString(i);
          out.print(
"        <tr class=\""+(((i % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\"><input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"SolrConnector.DeleteArgument")+" "+Integer.toString(i+1)+"\" onclick=\"javascript:deleteArgument("+Integer.toString(i)+");"+"\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_op"+"\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_name"+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(name)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\""+prefix+"_value"+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(value)+"\"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          i++;
        }
      }
      if (i == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"SolrConnector.NoArgumentsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\"argument\"><input type=\"button\" value=\"Add\" alt=\"Add argument\" onclick=\"javascript:addArgument();\"/>\n"+
"              <input type=\"hidden\" name=\"argument_count\" value=\""+Integer.toString(i)+"\"/>\n"+
"              <input type=\"hidden\" name=\"argument_op\" value=\"Continue\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"argument_name\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"30\" name=\"argument_value\" value=\"\"/></nobr>\n"+
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
      // Emit hiddens for argument tab
      i = 0;
      Iterator iter = argumentMap.keySet().iterator();
      while (iter.hasNext())
      {
        String name = (String)iter.next();
        ArrayList values = (ArrayList)argumentMap.get(name);
        int j = 0;
        while (j < values.size())
        {
          String value = (String)values.get(j++);
          // It's prefix will be...
          String prefix = "argument_" + Integer.toString(i++);
          out.print(
"<input type=\"hidden\" name=\""+prefix+"_name\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name)+"\"/>\n"+
"<input type=\"hidden\" name=\""+prefix+"_value\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(value)+"\"/>\n"
          );
        }
      }
      out.print(
"<input type=\"hidden\" name=\"argument_count\" value=\""+Integer.toString(i)+"\"/>\n"
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
    String type = variableContext.getParameter("solrtype");
    if (type != null)
      parameters.setParameter(SolrConfig.PARAM_SOLR_TYPE,type);

    String protocol = variableContext.getParameter("serverprotocol");
    if (protocol != null)
      parameters.setParameter(SolrConfig.PARAM_PROTOCOL,protocol);
		
    String server = variableContext.getParameter("servername");
    if (server != null)
      parameters.setParameter(SolrConfig.PARAM_SERVER,server);

    String port = variableContext.getParameter("serverport");
    if (port != null)
      parameters.setParameter(SolrConfig.PARAM_PORT,port);

    String webapp = variableContext.getParameter("webappname");
    if (webapp != null)
      parameters.setParameter(SolrConfig.PARAM_WEBAPPNAME,webapp);

    String core = variableContext.getParameter("core");
    if (core != null)
      parameters.setParameter(SolrConfig.PARAM_CORE,core);

    String collection = variableContext.getParameter("collection");
    if (collection != null)
      parameters.setParameter(SolrConfig.PARAM_COLLECTION,collection);

    String connectionTimeout = variableContext.getParameter("connectiontimeout");
    if (connectionTimeout != null)
      parameters.setParameter(SolrConfig.PARAM_CONNECTION_TIMEOUT,connectionTimeout);
    
    String socketTimeout = variableContext.getParameter("sockettimeout");
    if (socketTimeout != null)
      parameters.setParameter(SolrConfig.PARAM_SOCKET_TIMEOUT,socketTimeout);

    String znodePath = variableContext.getParameter("znodepath");
    if (znodePath != null)
      parameters.setParameter(SolrConfig.PARAM_ZOOKEEPER_ZNODE_PATH,znodePath);
    
    String zkClientTimeout = variableContext.getParameter("zkclienttimeout");
    if (zkClientTimeout != null)
      parameters.setParameter(SolrConfig.PARAM_ZOOKEEPER_CLIENT_TIMEOUT,zkClientTimeout);
    
    String zkConnectTimeout = variableContext.getParameter("zkconnecttimeout");
    if (zkConnectTimeout != null)
      parameters.setParameter(SolrConfig.PARAM_ZOOKEEPER_CONNECT_TIMEOUT,zkConnectTimeout);
    
    String updatePath = variableContext.getParameter("updatepath");
    if (updatePath != null)
      parameters.setParameter(SolrConfig.PARAM_UPDATEPATH,updatePath);

    String removePath = variableContext.getParameter("removepath");
    if (removePath != null)
      parameters.setParameter(SolrConfig.PARAM_REMOVEPATH,removePath);

    String statusPath = variableContext.getParameter("statuspath");
    if (statusPath != null)
      parameters.setParameter(SolrConfig.PARAM_STATUSPATH,statusPath);

    String idField = variableContext.getParameter("idfield");
    if (idField != null)
      parameters.setParameter(SolrConfig.PARAM_IDFIELD,idField);

    String modifiedDateField = variableContext.getParameter("modifieddatefield");
    if (modifiedDateField != null)
      parameters.setParameter(SolrConfig.PARAM_MODIFIEDDATEFIELD,modifiedDateField);

    String createdDateField = variableContext.getParameter("createddatefield");
    if (createdDateField != null)
      parameters.setParameter(SolrConfig.PARAM_CREATEDDATEFIELD,createdDateField);

    String indexedDateField = variableContext.getParameter("indexeddatefield");
    if (indexedDateField != null)
      parameters.setParameter(SolrConfig.PARAM_INDEXEDDATEFIELD,indexedDateField);

    String fileNameField = variableContext.getParameter("filenamefield");
    if (fileNameField != null)
      parameters.setParameter(SolrConfig.PARAM_FILENAMEFIELD,fileNameField);

    String mimeTypeField = variableContext.getParameter("mimetypefield");
    if (mimeTypeField != null)
      parameters.setParameter(SolrConfig.PARAM_MIMETYPEFIELD,mimeTypeField);

    String contentField = variableContext.getParameter("contentfield");
    if (contentField != null)
      parameters.setParameter(SolrConfig.PARAM_CONTENTFIELD,contentField);

    String extractUpdatePresent = variableContext.getParameter("extractupdatepresent");
    if (extractUpdatePresent != null)
    {
      String extractUpdate = variableContext.getParameter("extractupdate");
      if (extractUpdate == null || extractUpdate.length() == 0)
        extractUpdate = "false";
      parameters.setParameter(SolrConfig.PARAM_EXTRACTUPDATE,extractUpdate);
    }

    String realm = variableContext.getParameter("realm");
    if (realm != null)
      parameters.setParameter(SolrConfig.PARAM_REALM,realm);

    String userID = variableContext.getParameter("userid");
    if (userID != null)
      parameters.setParameter(SolrConfig.PARAM_USERID,userID);
		
    String password = variableContext.getParameter("password");
    if (password != null)
      parameters.setObfuscatedParameter(SolrConfig.PARAM_PASSWORD,variableContext.mapKeyToPassword(password));
    
    String maxLength = variableContext.getParameter("maxdocumentlength");
    if (maxLength != null)
      parameters.setParameter(SolrConfig.PARAM_MAXLENGTH,maxLength);
    
    String includedMimeTypes = variableContext.getParameter("includedmimetypes");
    if (includedMimeTypes != null)
      parameters.setParameter(SolrConfig.PARAM_INCLUDEDMIMETYPES,includedMimeTypes);
    
    String excludedMimeTypes = variableContext.getParameter("excludedmimetypes");
    if (excludedMimeTypes != null)
      parameters.setParameter(SolrConfig.PARAM_EXCLUDEDMIMETYPES,excludedMimeTypes);
    
    String commitsPresent = variableContext.getParameter("commits_present");
    if (commitsPresent != null)
    {
      String commits = variableContext.getParameter("commits");
      if (commits == null)
        commits = "false";
      parameters.setParameter(SolrConfig.PARAM_COMMITS,commits);
    }
    
    String commitWithin = variableContext.getParameter("commitwithin");
    if (commitWithin != null)
      parameters.setParameter(SolrConfig.PARAM_COMMITWITHIN,commitWithin);
    
    String keystoreValue = variableContext.getParameter("keystoredata");
    if (keystoreValue != null)
    {
      IKeystoreManager mgr = KeystoreManagerFactory.make("",keystoreValue);
      parameters.setParameter(SolrConfig.PARAM_KEYSTORE,mgr.getString());
    }

    String x = variableContext.getParameter("count_zookeeper");
    if (x != null && x.length() > 0)
    {
      // About to gather the bandwidth nodes, so get rid of the old ones.
      int i = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode node = parameters.getChild(i);
        if (node.getType().equals(SolrConfig.NODE_ZOOKEEPER))
          parameters.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String postfix = "zookeeper_"+Integer.toString(i);
        String op = variableContext.getParameter("op_"+postfix);
        if (op == null || !op.equals("Delete"))
        {
          // Gather the host etc.
          String host = variableContext.getParameter("host_"+postfix);
          String zkport = variableContext.getParameter("port_"+postfix);
          ConfigNode node = new ConfigNode(SolrConfig.NODE_ZOOKEEPER);
          node.setAttribute(SolrConfig.ATTR_HOST,host);
          node.setAttribute(SolrConfig.ATTR_PORT,zkport);
          parameters.addChild(parameters.getChildCount(),node);
        }
        i++;
      }
      String addop = variableContext.getParameter("op_zookeeper");
      if (addop != null && addop.equals("Add"))
      {
        String host = variableContext.getParameter("host_zookeeper");
        String zkport = variableContext.getParameter("port_zookeeper");
        ConfigNode node = new ConfigNode(SolrConfig.NODE_ZOOKEEPER);
        node.setAttribute(SolrConfig.ATTR_HOST,host);
        node.setAttribute(SolrConfig.ATTR_PORT,zkport);
        parameters.addChild(parameters.getChildCount(),node);
      }
    }

    x = variableContext.getParameter("argument_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the argument nodes, so get rid of the old ones.
      int i = 0;
      while (i < parameters.getChildCount())
      {
        ConfigNode node = parameters.getChild(i);
        if (node.getType().equals(SolrConfig.NODE_ARGUMENT))
          parameters.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = "argument_"+Integer.toString(i);
        String op = variableContext.getParameter(prefix+"_op");
        if (op == null || !op.equals("Delete"))
        {
          // Gather the name and value.
          String name = variableContext.getParameter(prefix+"_name");
          String value = variableContext.getParameter(prefix+"_value");
          ConfigNode node = new ConfigNode(SolrConfig.NODE_ARGUMENT);
          node.setAttribute(SolrConfig.ATTRIBUTE_NAME,name);
          node.setAttribute(SolrConfig.ATTRIBUTE_VALUE,value);
          parameters.addChild(parameters.getChildCount(),node);
        }
        i++;
      }
      String addop = variableContext.getParameter("argument_op");
      if (addop != null && addop.equals("Add"))
      {
        String name = variableContext.getParameter("argument_name");
        String value = variableContext.getParameter("argument_value");
        ConfigNode node = new ConfigNode(SolrConfig.NODE_ARGUMENT);
        node.setAttribute(SolrConfig.ATTRIBUTE_NAME,name);
        node.setAttribute(SolrConfig.ATTRIBUTE_VALUE,value);
        parameters.addChild(parameters.getChildCount(),node);
      }
    }
    
    String configOp = variableContext.getParameter("configop");
    if (configOp != null)
    {
      IKeystoreManager mgr;
      if (configOp.equals("Delete"))
      {
        String alias = variableContext.getParameter("solrkeystorealias");
        keystoreValue = parameters.getParameter(SolrConfig.PARAM_KEYSTORE);
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        mgr.remove(alias);
        parameters.setParameter(SolrConfig.PARAM_KEYSTORE,mgr.getString());
      }
      else if (configOp.equals("Add"))
      {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("solrcertificate");
        keystoreValue = parameters.getParameter(SolrConfig.PARAM_KEYSTORE);
        if (keystoreValue != null)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");
        java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
        String certError = null;
        try
        {
          mgr.importCertificate(alias,is);
        }
        catch (Throwable e)
        {
          certError = e.getMessage();
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IOException e)
          {
            // Eat this exception
          }
        }

        if (certError != null)
        {
          return "Illegal certificate: "+certError;
        }
        parameters.setParameter(SolrConfig.PARAM_KEYSTORE,mgr.getString());
      }
    }

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
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Parameters") + "</nobr></td>\n"+
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
"  </tr>\n"
    );
    
    out.print(
"\n"
    );
    
    out.print(
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.ZookeeperHosts") + "</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Host") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Port") + "</nobr></td>\n"+
"        </tr>\n"
    );

    int instanceNumber = 0;
    for (int i = 0; i < parameters.getChildCount(); i++)
    {
      ConfigNode cn = parameters.getChild(i);
      if (cn.getType().equals(SolrConfig.NODE_ZOOKEEPER))
      {
        // An argument node!  Look for all its parameters.
        String host = cn.getAttributeValue(SolrConfig.ATTR_HOST);
        String zkport = cn.getAttributeValue(SolrConfig.ATTR_PORT);

        out.print(
"        <tr class=\""+(((instanceNumber % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(host)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(zkport)+"</nobr></td>\n"+
"        </tr>\n"
        );
        
        instanceNumber++;
      }
    }
    if (instanceNumber == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"5\">" + Messages.getBodyString(locale,"SolrConnector.NoZookeeperHostsSpecified") + "</td></tr>\n"
      );
    }

    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"
    );

    out.print(
"\n"
    );

    out.print(
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Arguments3") + "</nobr></td>\n"+
"    <td class=\"boxcell\" colspan=\"3\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Name") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.Value") + "</nobr></td>\n"+
"        </tr>\n"
    );
    
    instanceNumber = 0;
    for (int i = 0; i < parameters.getChildCount(); i++)
    {
      ConfigNode cn = parameters.getChild(i);
      if (cn.getType().equals(SolrConfig.NODE_ARGUMENT))
      {
        // An argument node!  Look for all its parameters.
        String name = cn.getAttributeValue(SolrConfig.ATTRIBUTE_NAME);
        String value = cn.getAttributeValue(SolrConfig.ATTRIBUTE_VALUE);

        out.print(
"        <tr class=\""+(((instanceNumber % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(name)+"</nobr></td>\n"+
"          <td class=\"formcolumncell\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+"</nobr></td>\n"+
"        </tr>\n"
        );
        
        instanceNumber++;
      }
    }
    if (instanceNumber == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"5\">" + Messages.getBodyString(locale,"SolrConnector.NoArguments") + "</td></tr>\n"
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
    String seqPrefix = "s"+connectionSequenceNumber+"_";
    tabsArray.add(Messages.getString(locale,"SolrConnector.SolrFieldMapping"));
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function "+seqPrefix+"checkSpecification()\n"+
"{\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"addFieldMapping()\n"+
"{\n"+
"  if (editjob."+seqPrefix+"solr_fieldmapping_source.value == \"\")\n"+
"  {\n"+
"    alert(\""+Messages.getBodyJavascriptString(locale,"SolrConnector.FieldMapMustHaveNonNullSource")+"\");\n"+
"    editjob."+seqPrefix+"solr_fieldmapping_source.focus();\n"+
"    return;\n"+
"  }\n"+
"  editjob."+seqPrefix+"solr_fieldmapping_op.value=\"Add\";\n"+
"  postFormSetAnchor(\""+seqPrefix+"+solr_fieldmapping\");\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"deleteFieldMapping(i)\n"+
"{\n"+
"  // Set the operation\n"+
"  eval(\"editjob."+seqPrefix+"solr_fieldmapping_\"+i+\"_op.value=\\\"Delete\\\"\");\n"+
"  // Submit\n"+
"  if (editjob."+seqPrefix+"solr_fieldmapping_count.value==i)\n"+
"    postFormSetAnchor(\""+seqPrefix+"solr_fieldmapping\");\n"+
"  else\n"+
"    postFormSetAnchor(\""+seqPrefix+"solr_fieldmapping_\"+i)\n"+
"  // Undo, so we won't get two deletes next time\n"+
"  eval(\"editjob."+seqPrefix+"solr_fieldmapping_\"+i+\"_op.value=\\\"Continue\\\"\");\n"+
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
    
    // Field Mapping tab
    if (tabName.equals(Messages.getString(locale,"SolrConnector.SolrFieldMapping")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.FieldMappings") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.MetadataFieldName") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.SolrFieldName") + "</nobr></td>\n"+
"        </tr>\n"
      );

      int fieldCounter = 0;
      i = 0;
      boolean keepMetadata = true;
      while (i < os.getChildCount()) {
        SpecificationNode sn = os.getChild(i++);
        if (sn.getType().equals(SolrConfig.NODE_FIELDMAP)) {
          String source = sn.getAttributeValue(SolrConfig.ATTRIBUTE_SOURCE);
          String target = sn.getAttributeValue(SolrConfig.ATTRIBUTE_TARGET);
          if (target != null && target.length() == 0) {
            target = null;
          }
          String targetDisplay = target;
          if (target == null)
          {
            target = "";
            targetDisplay = "(remove)";
          }
          // It's prefix will be...
          String prefix = seqPrefix+"solr_fieldmapping_" + Integer.toString(fieldCounter);
          out.print(
"        <tr class=\""+(((fieldCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+prefix+"\">\n"+
"              <input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"SolrConnector.DeleteFieldMapping")+Integer.toString(fieldCounter+1)+"\" onclick='javascript:"+seqPrefix+"deleteFieldMapping("+Integer.toString(fieldCounter)+");'/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_op\" value=\"Continue\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_source\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(source)+"\"/>\n"+
"              <input type=\"hidden\" name=\""+prefix+"_target\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(target)+"\"/>\n"+
"            </a>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(source)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(targetDisplay)+"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
          );
          fieldCounter++;
        }
        else if(sn.getType().equals(SolrConfig.NODE_KEEPMETADATA)) {
          keepMetadata = Boolean.parseBoolean(sn.getAttributeValue(SolrConfig.ATTRIBUTE_VALUE));
        }
      }
      
      if (fieldCounter == 0)
      {
        out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"SolrConnector.NoFieldMappingSpecified") + "</td></tr>\n"
        );
      }
      
      String keepMetadataValue;
      if (keepMetadata)
        keepMetadataValue = " checked=\"true\"";
      else
        keepMetadataValue = "";

      out.print(
"        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n"+
"        <tr class=\"formrow\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <a name=\""+seqPrefix+"solr_fieldmapping\">\n"+
"              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SolrConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"SolrConnector.AddFieldMapping") + "\" onclick=\"javascript:"+seqPrefix+"addFieldMapping();\"/>\n"+
"            </a>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"solr_fieldmapping_count\" value=\""+fieldCounter+"\"/>\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"solr_fieldmapping_op\" value=\"Continue\"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"15\" name=\""+seqPrefix+"solr_fieldmapping_source\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr><input type=\"text\" size=\"15\" name=\""+seqPrefix+"solr_fieldmapping_target\" value=\"\"/></nobr>\n"+
"          </td>\n"+
"        </tr>\n"+
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>"+Messages.getBodyString(locale,"SolrConnector.KeepAllMetadata")+"</nobr></td>\n"+
"    <td class=\"value\">\n"+
"       <input type=\"checkbox\""+keepMetadataValue+" name=\""+seqPrefix+"solr_keepallmetadata\" value=\"true\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for field mapping
      i = 0;
      int fieldCounter = 0;
      String keepMetadataValue = "true";
      while (i < os.getChildCount()) {
        SpecificationNode sn = os.getChild(i++);
        if (sn.getType().equals(SolrConfig.NODE_FIELDMAP)) {
          String source = sn.getAttributeValue(SolrConfig.ATTRIBUTE_SOURCE);
          String target = sn.getAttributeValue(SolrConfig.ATTRIBUTE_TARGET);
          if (target == null)
            target = "";
        // It's prefix will be...
          String prefix = seqPrefix+"solr_fieldmapping_" + Integer.toString(fieldCounter);
          out.print(
"<input type=\"hidden\" name=\""+prefix+"_source\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(source)+"\"/>\n"+
"<input type=\"hidden\" name=\""+prefix+"_target\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(target)+"\"/>\n"
          );
          fieldCounter++;
        }
        else if(sn.getType().equals(SolrConfig.NODE_KEEPMETADATA))
        {
          keepMetadataValue = sn.getAttributeValue(SolrConfig.ATTRIBUTE_VALUE);
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"solr_keepallmetadata\" value=\""+keepMetadataValue+"\"/>\n"
      );
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"solr_fieldmapping_count\" value=\""+Integer.toString(fieldCounter)+"\"/>\n"
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
    String x = variableContext.getParameter(seqPrefix+"solr_fieldmapping_count");
    if (x != null && x.length() > 0)
    {
      // About to gather the fieldmapping nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount())
      {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(SolrConfig.NODE_FIELDMAP) || node.getType().equals(SolrConfig.NODE_KEEPMETADATA))
          os.removeChild(i);
        else
          i++;
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count)
      {
        String prefix = seqPrefix+"solr_fieldmapping_"+Integer.toString(i);
        String op = variableContext.getParameter(prefix+"_op");
        if (op == null || !op.equals("Delete"))
        {
          // Gather the fieldmap etc.
          String source = variableContext.getParameter(prefix+"_source");
          String target = variableContext.getParameter(prefix+"_target");
          if (target == null)
            target = "";
          SpecificationNode node = new SpecificationNode(SolrConfig.NODE_FIELDMAP);
          node.setAttribute(SolrConfig.ATTRIBUTE_SOURCE,source);
          node.setAttribute(SolrConfig.ATTRIBUTE_TARGET,target);
          os.addChild(os.getChildCount(),node);
        }
        i++;
      }
      
      String addop = variableContext.getParameter(seqPrefix+"solr_fieldmapping_op");
      if (addop != null && addop.equals("Add"))
      {
        String source = variableContext.getParameter(seqPrefix+"solr_fieldmapping_source");
        String target = variableContext.getParameter(seqPrefix+"solr_fieldmapping_target");
        if (target == null)
          target = "";
        SpecificationNode node = new SpecificationNode(SolrConfig.NODE_FIELDMAP);
        node.setAttribute(SolrConfig.ATTRIBUTE_SOURCE,source);
        node.setAttribute(SolrConfig.ATTRIBUTE_TARGET,target);
        os.addChild(os.getChildCount(),node);
      }
      
      // Gather the keep all metadata parameter to be the last one
      SpecificationNode node = new SpecificationNode(SolrConfig.NODE_KEEPMETADATA);
      String keepAll = variableContext.getParameter(seqPrefix+"solr_keepallmetadata");
      if (keepAll != null) {
        node.setAttribute(SolrConfig.ATTRIBUTE_VALUE, keepAll);
      }
      else {
        node.setAttribute(SolrConfig.ATTRIBUTE_VALUE, "false");
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
    // Prep for field mappings
    HashMap fieldMap = new HashMap();
    int i = 0;

    // Display field mappings
    out.print(
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.FieldMappings") + "</nobr></td>\n"+
"    <td class=\"boxcell\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.MetadataFieldName") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.SolrFieldName") + "</nobr></td>\n"+
"        </tr>\n"
    );

    int fieldCounter = 0;
    i = 0;
    String keepAllMetadataValue = "true";
    while (i < os.getChildCount()) {
      SpecificationNode sn = os.getChild(i++);
      if (sn.getType().equals(SolrConfig.NODE_FIELDMAP)) {
        String source = sn.getAttributeValue(SolrConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(SolrConfig.ATTRIBUTE_TARGET);
        String targetDisplay = target;
        if (target == null)
        {
          target = "";
          targetDisplay = "(remove)";
        }
        out.print(
"        <tr class=\""+(((fieldCounter % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(source)+"</nobr>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(targetDisplay)+"</nobr>\n"+
"          </td>\n"+
"        </tr>\n"
        );
        fieldCounter++;
      }
      else if (sn.getType().equals(SolrConfig.NODE_KEEPMETADATA))
      {
        keepAllMetadataValue = sn.getAttributeValue(SolrConfig.ATTRIBUTE_VALUE);
      }
    }
    
    if (fieldCounter == 0)
    {
      out.print(
"        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale,"SolrConnector.NoFieldMappingSpecified") + "</td></tr>\n"
      );
    }
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrConnector.KeepAllMetadata") + "</nobr></td>\n"+
"    <td class=\"value\"><nobr>" + keepAllMetadataValue + "</nobr></td>\n"+
"  </tr>\n"+
"</table>\n"
    );

  }

  /** This class handles Solr connector version string packing/unpacking/interpretation.
  */
  protected class SpecPacker {
    
    /** Arguments, from configuration */
    private final Map<String,List<String>> args = new HashMap<String,List<String>>();
    /** Source/targets from specification */
    private final Map<String, List<String>> sourceTargets = new HashMap<String, List<String>>();
    /** Keep all metadata flag, from specification */
    private final boolean keepAllMetadata;
    
    public SpecPacker(Specification spec) {

      // Process arguments
      for (int i = 0; i < params.getChildCount(); i++)
      {
        ConfigNode node = params.getChild(i);
        if (node.getType().equals(SolrConfig.NODE_ARGUMENT))
        {
          String attrName = node.getAttributeValue(SolrConfig.ATTRIBUTE_NAME);
          List<String> list = args.get(attrName);
          if (list == null)
          {
            list = new ArrayList<String>();
            args.put(attrName,list);
          }
          list.add(node.getAttributeValue(SolrConfig.ATTRIBUTE_VALUE));
        }
      }
    
      // Do the source/target pairs
      boolean keepAllMetadata = true;
      for (int i = 0; i < spec.getChildCount(); i++)
      {
        SpecificationNode sn = spec.getChild(i);
        
        if(sn.getType().equals(SolrConfig.NODE_KEEPMETADATA)) {
          String value = sn.getAttributeValue(SolrConfig.ATTRIBUTE_VALUE);
          keepAllMetadata = Boolean.parseBoolean(value);
        } else if (sn.getType().equals(SolrConfig.NODE_FIELDMAP)) {
          String source = sn.getAttributeValue(SolrConfig.ATTRIBUTE_SOURCE);
          String target = sn.getAttributeValue(SolrConfig.ATTRIBUTE_TARGET);
          
          if (target == null) {
            target = "";
          }
          List<String> list = sourceTargets.get(source);
          if (list == null) {
            list = new ArrayList<String>();
            sourceTargets.put(source, list);
          }
          list.add(target);
        }
      }
      this.keepAllMetadata = keepAllMetadata;
    
    }
    
    /** Packed string parser.
    * This method unpacks a packed version string, and makes the formerly packed data available for use.
    * Note that it is actually *not* a requirement for this method to do the unpacking; that can happen "on demand"
    * for performance, if deemed helpful.
    */
    public SpecPacker(String packedString) {
      // Build the argument map we'll send.
      int index = 0;
      List<String> nameValues = new ArrayList<String>();
      index = unpackList(nameValues,packedString,index,'+');
      List<String> sts = new ArrayList<String>();
      index = unpackList(sts,packedString,index,'+');
      // extract keep all metadata Flag
      boolean keepAllMetadata = true;
      if (index < packedString.length())
      {
        keepAllMetadata = (packedString.charAt(index++) == '+');
      }
      this.keepAllMetadata = keepAllMetadata;
      
      
      String[] fixedBuffer = new String[2];
      
      // Do the name/value pairs
      for (String x : nameValues)
      {
        unpackFixedList(fixedBuffer,x,0,'=');
        String attrName = fixedBuffer[0];
        List<String> list = args.get(attrName);
        if (list == null)
        {
          list = new ArrayList<String>();
          args.put(attrName,list);
        }
        list.add(fixedBuffer[1]);
      }
      
      // Do the source/target pairs
      for (String x : sts)
      {
        unpackFixedList(fixedBuffer,x,0,'=');
        String source = fixedBuffer[0];
        String target = fixedBuffer[1];
        List<String> list = sourceTargets.get(source);
        if (list == null) {
          list = new ArrayList<String>();
          sourceTargets.put(source, list);
        }
        list.add(target);
      }

    }
    
    public String toPackedString() {
      StringBuilder sb = new StringBuilder();
      String[] sortArray = new String[args.size()];
      Iterator<String> iter = args.keySet().iterator();
      int i = 0;
      while (iter.hasNext())
      {
        sortArray[i++] = iter.next();
      }
      
      // Always use sorted order, because we need this to be comparable.
      java.util.Arrays.sort(sortArray);
      
      String[] fixedList = new String[2];
      List<String> nameValues = new ArrayList<String>();
      for (int k = 0; k < sortArray.length; k++)
      {
        String name = sortArray[k];
        List<String> values = args.get(name);
        java.util.Collections.sort(values);
        for (String value : values)
        {
          fixedList[0] = name;
          fixedList[1] = value;
          StringBuilder pairBuffer = new StringBuilder();
          packFixedList(pairBuffer,fixedList,'=');
          nameValues.add(pairBuffer.toString());
        }
      }
      
      packList(sb,nameValues,'+');
      
      // Do the source/target pairs
      sortArray = new String[sourceTargets.size()];
      iter = sourceTargets.keySet().iterator();
      i = 0;
      while (iter.hasNext()) {
        sortArray[i++] = iter.next();
      }
      java.util.Arrays.sort(sortArray);
      
      List<String> sourceTargetsList = new ArrayList<String>();
      for (int k = 0; k < sortArray.length; k++)
      {
        String source = sortArray[k];
        List<String> values = sourceTargets.get(source);
        java.util.Collections.sort(values);
        for (String target : values)
        {
          fixedList[0] = source;
          fixedList[1] = target;
          StringBuilder pairBuffer = new StringBuilder();
          packFixedList(pairBuffer,fixedList,'=');
          sourceTargetsList.add(pairBuffer.toString());
        }
      }
      
      packList(sb,sourceTargetsList,'+');

      // Keep all metadata flag
      if (keepAllMetadata)
        sb.append('+');
      else
        sb.append('-');

      // Here, append things which we have no intention of unpacking.  This includes stuff that comes from
      // the configuration information, for instance.

      if (idAttributeName != null)
      {
          sb.append('+');
          pack(sb,idAttributeName,'+');
      }
      else
        sb.append('-');

      if (modifiedDateAttributeName != null)
      {
          sb.append('+');
          pack(sb,modifiedDateAttributeName,'+');
      }
      else
        sb.append('-');
      
      if (createdDateAttributeName != null)
      {
          sb.append('+');
          pack(sb,createdDateAttributeName,'+');
      }
      else
        sb.append('-');

      if (indexedDateAttributeName != null)
      {
          sb.append('+');
          pack(sb,indexedDateAttributeName,'+');
      }
      else
        sb.append('-');

      if (fileNameAttributeName != null)
      {
          sb.append('+');
          pack(sb,fileNameAttributeName,'+');
      }
      else
        sb.append('-');

      if (mimeTypeAttributeName != null)
      {
          sb.append('+');
          pack(sb,mimeTypeAttributeName,'+');
      }
      else
        sb.append('-');

      if (contentAttributeName != null)
      {
          sb.append('+');
          pack(sb,contentAttributeName,'+');
      }
      else
        sb.append('-');

      if (useExtractUpdateHandler)
        sb.append('+');
      else
        sb.append('-');

      // Length limitation.  We pack this because when it is changed we want to be sure we get any previously excluded documents.
      if (maxDocumentLength != null)
      {
        sb.append('+');
        pack(sb,maxDocumentLength.toString(),'+');
      }
      else
        sb.append('-');
      // Included mime types
      if (includedMimeTypesString != null)
      {
        sb.append('+');
        pack(sb,includedMimeTypesString,'+');
      }
      else
        sb.append('-');
      // Excluded mime types
      if (excludedMimeTypesString != null)
      {
        sb.append('+');
        pack(sb,excludedMimeTypesString,'+');
      }
      else
        sb.append('-');

      return sb.toString();
    }
    
    public Map<String,List<String>> getArgs() {
      return args;
    }
    
    public Map<String,List<String>> getMappings() {
      return sourceTargets;
    }
    
    public boolean keepAllMetadata() {
      return keepAllMetadata;
    }
  }

}
