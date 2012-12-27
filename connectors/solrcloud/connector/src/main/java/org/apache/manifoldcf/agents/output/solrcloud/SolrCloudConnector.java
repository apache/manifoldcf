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
package org.apache.manifoldcf.agents.output.solrcloud;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.OutputSpecification;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.core.interfaces.ConfigNode;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IDFactory;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IKeystoreManager;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.KeystoreManagerFactory;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;

/**
 * @author minoru
 *
 */
public class SolrCloudConnector extends BaseOutputConnector {
  private final static String SOLRCLOUD_INDEXATION_ACTIVITY = "Indexation";
  private final static String SOLRCLOUD_DELETION_ACTIVITY = "Deletion";

  private final static String[] SOLRCLOUD_ACTIVITIES = {
    SOLRCLOUD_INDEXATION_ACTIVITY, SOLRCLOUD_DELETION_ACTIVITY
  };

  private SolrServer solrServer = null;
  private HttpSolrServer httpSolrServer = null;
  private CloudSolrServer cloudSolrServer = null;

  private DefaultHttpClient client = null;

  /** Solr type */
  protected String solrType = null;

  /** ZooKeeper Hosts <HOSTNAME>:<POERT>,<HOSTNAME>:<POERT>... */
  protected String zkHost = null;

  /** ZooKeeper client timeout */
  protected Integer zkClientTimeout = null;

  /** ZooKeeper connection timeout */
  protected Integer zkConnectTimeout = null;

  /** Protocol */
  protected String protocol = null;

  /** Server name */
  protected String host = null;

  /** Port */
  protected Integer port = null;

  /** Context */
  protected String context = null;

  /** Default collection name */
  protected String collection = null;

  /** Update handler */
  protected String updatePath = null;

  /** Remove handler */
  protected String removePath = null;

  /** Status handler */
  protected String statusPath = null;

  /** HTTP Client Connection Timeout */
  protected Integer httpClientConnectionTimeout = null;

  /** HTTP Client Socket Timeout */
  protected Integer httpClientSocketTimeout = null;

  /** Realm */
  protected String realm = null;

  /** User ID */
  protected String userID = null;

  /** Password */
  protected String password = null;

  /** Keystore */
  IKeystoreManager keystoreManager = null;

  /** Whether or not to commit */
  protected Boolean doCommits = false;

  /** Commit each document within */
  protected Integer commitWithin = null;

  /** Unique key field */
  protected String uniqueKeyField = null;

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

  private static final String LITERAL = "literal.";
  private static final String ALLOW_TOKEN_PREFIX = "allow_token_";
  private static final String DENY_TOKEN_PREFIX = "deny_token_";

  /**
   * Constructor.
   */
  public SolrCloudConnector() {
  }

  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);

    try {
      /*
       * HTTP client
       */
      String strHttpConnectionTimeout = this.params.getParameter(SolrCloudConfig.PARAM_HTTP_CLIENT_CONNECTION_TIMEOUT);
      if (strHttpConnectionTimeout == null || strHttpConnectionTimeout.length() == 0) {
        this.httpClientConnectionTimeout = 0;
      } else {
        this.httpClientConnectionTimeout = new Integer(strHttpConnectionTimeout);
      }

      String strHttpSocketTimeout = this.params.getParameter(SolrCloudConfig.PARAM_HTTP_CLIENT_SOCKET_TIMEOUT);
      if (strHttpSocketTimeout == null || strHttpSocketTimeout.length() == 0) {
        this.httpClientSocketTimeout = 0;
      } else {
        this.httpClientSocketTimeout = new Integer(strHttpSocketTimeout);
      }

      this.userID = this.params.getParameter(SolrCloudConfig.PARAM_USERID);
      if (this.userID == null || this.userID.length() == 0) {
        this.userID = "";
      }

      this.password = this.params.getParameter(SolrCloudConfig.PARAM_PASSWORD);
      if (this.password == null || this.password.length() == 0) {
        this.password = "";
      }

      this.realm = this.params.getParameter(SolrCloudConfig.PARAM_REALM);
      if (this.realm == null || this.realm.length() == 0) {
        this.realm = "";
      }

      this.client = new DefaultHttpClient();
      HttpParams httpParams = this.client.getParams();
      HttpConnectionParams.setConnectionTimeout(httpParams, this.httpClientConnectionTimeout);
      HttpConnectionParams.setSoTimeout(httpParams, this.httpClientSocketTimeout);

      if (!this.userID.equals("") && !this.password.equals("")) {
        Credentials credentials = new UsernamePasswordCredentials(this.userID, this.password);
        if (this.realm.equals("")) {
          this.client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, this.realm), credentials);
        }
      }

      /*
       * Solr type
       */
      this.solrType = this.params.getParameter(SolrCloudConfig.PARAM_SOLR_TYPE);
      if (this.solrType == null || this.solrType.length() == 0) {
        this.solrType = "solrcloud";
      }

      /*
       * SolrCloud confiuration
       */
      this.zkHost = this.params.getParameter(SolrCloudConfig.PARAM_ZOOKEEPER_HOST);
      if (this.zkHost == null || this.zkHost.length() == 0) {
        this.zkHost = "localhost:2181";
      }

      String strZkClientTimeout = this.params.getParameter(SolrCloudConfig.PARAM_ZOOKEEPER_CLIENT_TIMEOUT);
      if (strZkClientTimeout == null || strZkClientTimeout.length() == 0) {
        strZkClientTimeout = "15000";
      }
      this.zkClientTimeout = new Integer(strZkClientTimeout);

      String strZkConnectTimeout = this.params.getParameter(SolrCloudConfig.PARAM_ZOOKEEPER_CONNECT_TIMEOUT);
      if (strZkConnectTimeout == null || strZkConnectTimeout.length() == 0) {
        strZkConnectTimeout = "15000";
      }
      this.zkConnectTimeout = new Integer(strZkConnectTimeout);

      /*
       * Solr configuration
       */
      this.protocol = this.params.getParameter(SolrCloudConfig.PARAM_PROTOCOL);
      if (this.protocol == null || this.protocol.length() == 0) {
        this.protocol = "http";
      }

      this.host = this.params.getParameter(SolrCloudConfig.PARAM_HOST);
      if (this.host == null || this.host.length() == 0) {
        this.host = "localhost";
      }

      String strPort = this.params.getParameter(SolrCloudConfig.PARAM_PORT);
      if (strPort == null || strPort.length() == 0) {
        strPort = "8983";
      }
      this.port = new Integer(strPort);

      this.context = this.params.getParameter(SolrCloudConfig.PARAM_CONTEXT);
      if (this.context == null || this.context.length() == 0) {
        this.context = "";
      }

      /*
       * Collection
       */
      this.collection = this.params.getParameter(SolrCloudConfig.PARAM_COLLECTION);
      if (this.collection == null || this.collection.length() == 0) {
        this.collection = "collection1";
      }

      if (this.solrType.equals("solrcloud")) {
        this.cloudSolrServer = new CloudSolrServer(this.zkHost);
        this.cloudSolrServer.setZkClientTimeout(this.zkClientTimeout);
        this.cloudSolrServer.setZkConnectTimeout(this.zkConnectTimeout);
        this.cloudSolrServer.setDefaultCollection(this.collection);
        this.solrServer = this.cloudSolrServer;
      } else if (this.solrType.equals("solr")) {
        String httpSolrServerUrl = this.protocol + "://" + this.host + ":" + this.port.toString() + "/" + this.context + "/" + this.collection;
        this.httpSolrServer = new HttpSolrServer(httpSolrServerUrl, this.client);
        this.solrServer = this.httpSolrServer;
      }
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
  }

  @Override
  public String check() throws ManifoldCFException {
    return super.check();
  }

  @Override
  public void poll() throws ManifoldCFException {
    super.poll();
  }

  @Override
  public void disconnect() throws ManifoldCFException {
    this.solrServer.shutdown();
    this.solrServer = null;
    this.cloudSolrServer = null;
    this.httpSolrServer = null;
    this.client = null;

    this.maxDocumentLength = null;
    this.includedMimeTypesString = null;
    this.includedMimeTypes = null;
    this.excludedMimeTypesString = null;
    this.excludedMimeTypes = null;

    super.disconnect();
  }

  @Override
  public String[] getActivitiesList() {
    return SOLRCLOUD_ACTIVITIES;
  }

  @Override
  public int addOrReplaceDocument(String documentURI,
      String outputDescription, RepositoryDocument document,
      String authorityNameString, IOutputAddActivity activities)
          throws ManifoldCFException, ServiceInterruption {
    setParams();

    try {
      ContentStreamUpdateRequest contentStreamUpdateRequest = new ContentStreamUpdateRequest("/update/extract");

      /*
       * Add ContetntStream.
       */
      contentStreamUpdateRequest.addContentStream(new RepositoryDocumentStream(document));

      /*
       * Set UniqueKey field to ContentStreamUpdateRequest.
       */
      contentStreamUpdateRequest.setParam(LITERAL + this.uniqueKeyField, documentURI);

      /*
       * Set ACL to ContentStreamUpdateRequest.
       */
      String[] shareAcls = convertACL(document.getShareACL(),authorityNameString,activities);
      for(String acl : shareAcls) {
        contentStreamUpdateRequest.setParam(LITERAL + ALLOW_TOKEN_PREFIX + "share", acl);
      }

      String[] shareDenyAcls = convertACL(document.getShareDenyACL(),authorityNameString,activities);
      for(String acl : shareDenyAcls) {
        contentStreamUpdateRequest.setParam(LITERAL + DENY_TOKEN_PREFIX + "share", acl);
      }

      String[] acls = convertACL(document.getACL(),authorityNameString,activities);
      for(String acl : acls) {
        contentStreamUpdateRequest.setParam(LITERAL + ALLOW_TOKEN_PREFIX + "document", acl);
      }

      String[] denyAcls = convertACL(document.getDenyACL(),authorityNameString,activities);
      for(String acl : denyAcls) {
        contentStreamUpdateRequest.setParam(LITERAL + DENY_TOKEN_PREFIX + "document", acl);
      }

      int index = 0;
      ArrayList nameValues = new ArrayList();
      index = unpackList(nameValues, outputDescription, index, '+');
      String[] fixedBuffer = new String[2];
      
      int i = 0;
      while (i < nameValues.size()) {
        String x = (String)nameValues.get(i++);
        unpackFixedList(fixedBuffer, x, 0, '=');
        contentStreamUpdateRequest.setParam(fixedBuffer[0], fixedBuffer[1]);
      }
      
      if (this.commitWithin != null) {
        contentStreamUpdateRequest.setCommitWithin(this.commitWithin);
      }
      
      /*
       * Request to Solr
       */
      this.solrServer.request(contentStreamUpdateRequest);
    } catch (SolrServerException e) {
      e.printStackTrace();
      return DOCUMENTSTATUS_REJECTED;
    } catch (IOException e) {
      e.printStackTrace();
      return DOCUMENTSTATUS_REJECTED;
    }

    return DOCUMENTSTATUS_ACCEPTED;
  }

  @Override
  public void removeDocument(String documentURI, String outputDescription,
      IOutputRemoveActivity activities) throws ManifoldCFException,
      ServiceInterruption {
    setParams();

    try {
      this.solrServer.deleteById(documentURI);
    } catch (SolrServerException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void noteJobComplete(IOutputNotifyActivity activities)
      throws ManifoldCFException, ServiceInterruption {
    setParams();

    try {
      if (this.doCommits) {
        this.solrServer.commit();
      }
    } catch (SolrServerException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
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
  public String getOutputDescription(OutputSpecification spec)
      throws ManifoldCFException, ServiceInterruption {
    StringBuilder sb = new StringBuilder();

    // All the arguments need to go into this string, since they affect ingestion.
    Map args = new HashMap();
    int i = 0;
    while (i < this.params.getChildCount()) {
      ConfigNode node = this.params.getChild(i++);
      if (node.getType().equals(SolrCloudConfig.NODE_ARGUMENT)) {
        String attrName = node.getAttributeValue(SolrCloudConfig.ATTRIBUTE_NAME);
        ArrayList list = (ArrayList)args.get(attrName);
        if (list == null) {
          list = new ArrayList();
          args.put(attrName, list);
        }
        list.add(node.getAttributeValue(SolrCloudConfig.ATTRIBUTE_VALUE));
      }
    }

    String[] sortArray = new String[args.size()];
    Iterator iter = args.keySet().iterator();
    i = 0;
    while (iter.hasNext()) {
      sortArray[i++] = (String)iter.next();
    }

    // Always use sorted order, because we need this to be comparable.
    java.util.Arrays.sort(sortArray);

    String[] fixedList = new String[2];
    ArrayList nameValues = new ArrayList();
    i = 0;
    while (i < sortArray.length) {
      String name = sortArray[i++];
      ArrayList values = (ArrayList)args.get(name);
      int j = 0;
      while (j < values.size()) {
        String value = (String)values.get(j++);
        fixedList[0] = name;
        fixedList[1] = value;
        StringBuilder pairBuffer = new StringBuilder();
        packFixedList(pairBuffer, fixedList, '=');
        nameValues.add(pairBuffer.toString());
      }
    }

    packList(sb,nameValues,'+');

    Map fieldMap = new HashMap();
    i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals(SolrCloudConfig.NODE_FIELDMAP)) {
        String source = sn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_TARGET);
        if (target == null) {
          target = "";
        }
        fieldMap.put(source,target);
      }
    }

    sortArray = new String[fieldMap.size()];
    i = 0;
    iter = fieldMap.keySet().iterator();
    while (iter.hasNext()) {
      sortArray[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(sortArray);

    ArrayList sourceTargets = new ArrayList();

    i = 0;
    while (i < sortArray.length) {
      String source = sortArray[i++];
      String target = (String)fieldMap.get(source);
      fixedList[0] = source;
      fixedList[1] = target;
      StringBuilder pairBuffer = new StringBuilder();
      packFixedList(pairBuffer, fixedList, '=');
      sourceTargets.add(pairBuffer.toString());
    }

    packList(sb, sourceTargets, '+');

    // Here, append things which we have no intention of unpacking.  This includes stuff that comes from
    // the configuration information, for instance.

    if (this.maxDocumentLength != null || this.includedMimeTypesString != null || this.excludedMimeTypesString != null) {
      // Length limitation.  We pack this because when it is changed we want to be sure we get any previously excluded documents.
      if (this.maxDocumentLength != null) {
        sb.append('+');
        pack(sb, this.maxDocumentLength.toString(), '+');
      } else {
        sb.append('-');
      }
      // Included mime types
      if (this.includedMimeTypesString != null) {
        sb.append('+');
        pack(sb,this.includedMimeTypesString, '+');
      } else {
        sb.append('-');
      }
      // Excluded mime types
      if (this.excludedMimeTypesString != null) {
        sb.append('+');
        pack(sb,this.excludedMimeTypesString, '+');
      } else {
        sb.append('-');
      }
    }

    return sb.toString();
  }

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
          throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale, "SolrCloudConnector.Server"));
    tabsArray.add(Messages.getString(locale, "SolrCloudConnector.Schema"));
    tabsArray.add(Messages.getString(locale, "SolrCloudConnector.Arguments"));
    tabsArray.add(Messages.getString(locale, "SolrCloudConnector.Documents"));
    tabsArray.add(Messages.getString(locale, "SolrCloudConnector.Commits"));

    out.print(
        "<script type=\"text/javascript\">\n" +
            "<!--\n" +
            "function SolrCloudDeleteCertificate(aliasName)\n" +
            "{\n" +
            "  editconnection.solrkeystorealias.value = aliasName;\n" +
            "  editconnection.configop.value = \"Delete\";\n" +
            "  postForm();\n" +
            "}\n" +
            "\n" +
            "function SolrCloudAddCertificate()\n" +
            "{\n" +
            "  if (editconnection.solrcertificate.value == \"\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.ChooseACertificateFile") + "\");\n" +
            "    editconnection.solrcertificate.focus();\n" +
            "  }\n" +
            "  else\n" +
            "  {\n" +
            "    editconnection.configop.value = \"Add\";\n" +
            "    postForm();\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "function checkConfig()\n" +
            "{\n" +
            "  if (editconnection.host.value == \"\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.PleaseSupplyAValidSolrServerName") + "\");\n" +
            "    editconnection.host.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.port.value != \"\" && !isInteger(editconnection.port.value))\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.SolrServerPortMustBeAValidInteger") + "\");\n" +
            "    editconnection.port.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.context.value != \"\" && editconnection.context.value.indexOf(\"/\") != -1)\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.ContextCannotHaveCharacters") + "\");\n" +
            "    editconnection.context.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.collection.value != \"\" && editconnection.collection.value.indexOf(\"/\") != -1)\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.CollectionCannotHaveCharacter") + "\");\n" +
            "    editconnection.collection.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.context.value == \"\" && editconnection.collection.value != \"\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.WebApplicationMustBeSpecifiedIfCoreIsSpecified") + "\");\n" +
            "    editconnection.context.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.updatepath.value != \"\" && editconnection.updatepath.value.substring(0,1) != \"/\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.UpdatePathMustStartWithACharacter") + "\");\n" +
            "    editconnection.updatepath.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.removepath.value != \"\" && editconnection.removepath.value.substring(0,1) != \"/\")\n" +
            "  {\n" +
            "    alert(\""+Messages.getBodyJavascriptString(locale,"SolrCloudConnector.RemovePathMustStartWACharacter")+"\");\n"+
            "    editconnection.removepath.focus();\n"+
            "    return false;\n"+
            "  }\n"+
            "  if (editconnection.statuspath.value != \"\" && editconnection.statuspath.value.substring(0,1) != \"/\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.StatusPathMustStartWACharacter") + "\");\n" +
            "    editconnection.statuspath.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.maxdocumentlength.value != \"\" && !isInteger(editconnection.maxdocumentlength.value))\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.MaximumDocumentLengthMustBAnInteger") + "\");\n" +
            "    editconnection.maxdocumentlength.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.commitwithin.value != \"\" && !isInteger(editconnection.commitwithin.value))\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.CommitWithinValueMustBeAnInteger") + "\");\n" +
            "    editconnection.commitwithin.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  return true;\n" +
            "}\n" +
            "\n" +
            "function checkConfigForSave()\n" +
            "{\n" +
            "  if (editconnection.host.value == \"\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.PleaseSupplyAValidSolrServerName") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Server") + "\");\n" +
            "    editconnection.host.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.port.value != \"\" && !isInteger(editconnection.port.value))\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.SolrServerPortMustBeAValidInteger") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Server") + "\");\n" +
            "    editconnection.port.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.context.value != \"\" && editconnection.context.value.indexOf(\"/\") != -1)\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.ContextCannotHaveCharacters") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Server") + "\");\n" +
            "    editconnection.context.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.collection.value != \"\" && editconnection.collection.value.indexOf(\"/\") != -1)\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.CollectionCannotHaveCharacters") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Server") + "\");\n" +
            "    editconnection.collection.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.context.value == \"\" && editconnection.collection.value != \"\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.WebApplicationMustBeSpecifiedIfCoreIsSpecified") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Server") + "\");\n" +
            "    editconnection.context.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.updatepath.value != \"\" && editconnection.updatepath.value.substring(0,1) != \"/\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.UpdatePathMustStartWithACharacter") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Server") + "\");\n" +
            "    editconnection.updatepath.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.removepath.value != \"\" && editconnection.removepath.value.substring(0,1) != \"/\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.RemovePathMustStartWithACharacter") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Server") + "\");\n" +
            "    editconnection.removepath.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.statuspath.value != \"\" && editconnection.statuspath.value.substring(0,1) != \"/\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.StatusPathMustStartWithACharacter") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Server") + "\");\n" +
            "    editconnection.statuspath.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.maxdocumentlength.value != \"\" && !isInteger(editconnection.maxdocumentlength.value))\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.MaximumDocumentLengthMustBeAnInteger") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Documents") + "\");\n" +
            "    editconnection.maxdocumentlength.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  if (editconnection.commitwithin.value != \"\" && !isInteger(editconnection.commitwithin.value))\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.CommitWithinValueMustBeAnInteger") + "\");\n" +
            "    SelectTab(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.Commits") + "\");\n" +
            "    editconnection.commitwithin.focus();\n" +
            "    return false;\n" +
            "  }\n" +
            "  return true;\n" +
            "}\n" +
            "\n" +
            "function deleteArgument(i)\n" +
            "{\n" +
            "  // Set the operation\n" +
            "  eval(\"editconnection.argument_\"+i+\"_op.value=\\\"Delete\\\"\");\n" +
            "  // Submit\n" +
            "  if (editconnection.argument_count.value==i)\n" +
            "    postFormSetAnchor(\"argument\");\n" +
            "  else\n" +
            "    postFormSetAnchor(\"argument_\"+i)\n" +
            "  // Undo, so we won't get two deletes next time\n" +
            "  eval(\"editconnection.argument_\"+i+\"_op.value=\\\"Continue\\\"\");\n" +
            "}\n" +
            "\n" +
            "function addArgument()\n" +
            "{\n" +
            "  if (editconnection.argument_name.value == \"\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.ArgumentNameCannotBeAnEmptyString") + "\");\n" +
            "    editconnection.argument_name.focus();\n" +
            "    return;\n" +
            "  }\n" +
            "  editconnection.argument_op.value=\"Add\";\n" +
            "  postFormSetAnchor(\"argument\");\n" +
            "}\n" +
            "\n" +
            "//-->\n" +
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
          throws ManifoldCFException, IOException {
    /*
     * Server tab
     */
    String solrType = parameters.getParameter(SolrCloudConfig.PARAM_SOLR_TYPE);
    if (solrType == null) {
      solrType = "solrcloud";
    }

    String zkHost = parameters.getParameter(SolrCloudConfig.PARAM_ZOOKEEPER_HOST);
    if (zkHost == null) {
      zkHost = "localhost:2181";
    }

    String zkClientTimeout = parameters.getParameter(SolrCloudConfig.PARAM_ZOOKEEPER_CLIENT_TIMEOUT);
    if (zkClientTimeout == null) {
      zkClientTimeout = "15000";
    }

    String zkConnectTimeout = parameters.getParameter(SolrCloudConfig.PARAM_ZOOKEEPER_CONNECT_TIMEOUT);
    if (zkConnectTimeout == null) {
      zkConnectTimeout = "15000";
    }

    String protocol = parameters.getParameter(SolrCloudConfig.PARAM_PROTOCOL);
    if (protocol == null) {
      protocol = "http";
    }

    String host = parameters.getParameter(SolrCloudConfig.PARAM_HOST);
    if (host == null) {
      host = "localhost";
    }

    String port = parameters.getParameter(SolrCloudConfig.PARAM_PORT);
    if (port == null) {
      port = "8983";
    }

    String context = parameters.getParameter(SolrCloudConfig.PARAM_CONTEXT);
    if (context == null) {
      context = "solr";
    }

    String collection = parameters.getParameter(SolrCloudConfig.PARAM_COLLECTION);
    if (collection == null) {
      collection = "collection1";
    }

    String updatePath = parameters.getParameter(SolrCloudConfig.PARAM_UPDATEPATH);
    if (updatePath == null) {
      updatePath = "/update/extract";
    }

    String removePath = parameters.getParameter(SolrCloudConfig.PARAM_REMOVEPATH);
    if (removePath == null) {
      removePath = "/update";
    }

    String statusPath = parameters.getParameter(SolrCloudConfig.PARAM_STATUSPATH);
    if (statusPath == null) {
      statusPath = "/admin/ping";
    }

    String httpClientConnectionTimeout = parameters.getParameter(SolrCloudConfig.PARAM_HTTP_CLIENT_CONNECTION_TIMEOUT);
    if (httpClientConnectionTimeout == null) {
      httpClientConnectionTimeout = "0";
    }

    String httpClientSocketTimeout = parameters.getParameter(SolrCloudConfig.PARAM_HTTP_CLIENT_SOCKET_TIMEOUT);
    if (httpClientSocketTimeout == null) {
      httpClientSocketTimeout = "0";
    }

    String realm = parameters.getParameter(SolrCloudConfig.PARAM_REALM);
    if (realm == null) {
      realm = "";
    }

    String userID = parameters.getParameter(SolrCloudConfig.PARAM_USERID);
    if (userID == null) {
      userID = "";
    }

    String password = parameters.getObfuscatedParameter(SolrCloudConfig.PARAM_PASSWORD);
    if (password == null) {
      password = "";
    }

    String solrKeystore = parameters.getParameter(SolrCloudConfig.PARAM_KEYSTORE);
    IKeystoreManager localKeystore;
    if (solrKeystore == null) {
      localKeystore = KeystoreManagerFactory.make("");
    } else {
      localKeystore = KeystoreManagerFactory.make("", solrKeystore);
    }



    /*
     * Schema tab
     */
    String uniqueKeyField = parameters.getParameter(SolrCloudConfig.PARAM_UNIQUE_KEY_FIELD);
    if (uniqueKeyField == null) {
      uniqueKeyField = "id";
    }



    /*
     * Commits tab
     */
    String commits = parameters.getParameter(SolrCloudConfig.PARAM_COMMITS);
    if (commits == null) {
      commits = "true";
    }

    String commitWithin = parameters.getParameter(SolrCloudConfig.PARAM_COMMITWITHIN);
    if (commitWithin == null) {
      commitWithin = "";
    }



    /*
     * Documents tab
     */
    String maxLength = parameters.getParameter(SolrCloudConfig.PARAM_MAXLENGTH);
    if (maxLength == null) {
      maxLength = "";
    }

    String includedMimeTypes = parameters.getParameter(SolrCloudConfig.PARAM_INCLUDEDMIMETYPES);
    if (includedMimeTypes == null) {
      includedMimeTypes = "";
    }

    String excludedMimeTypes = parameters.getParameter(SolrCloudConfig.PARAM_EXCLUDEDMIMETYPES);
    if (excludedMimeTypes == null) {
      excludedMimeTypes = "";
    }



    // "Server" tab
    // Always pass the whole keystore as a hidden.
    if (solrKeystore != null) {
      out.print(
          "<input type=\"hidden\" name=\"keystoredata\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(solrKeystore) + "\"/>\n"
          );
    }
    out.print(
        "<input name=\"configop\" type=\"hidden\" value=\"Continue\"/>\n"
        );

    if (tabName.equals(Messages.getString(locale, "SolrCloudConnector.Server"))) {
      out.print(
          "<table class=\"displaytable\">\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.UseSolrCloud") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"solrtype\" type=\"radio\" value=\"solrcloud\" " + (solrType.equals("solrcloud") ? " checked=\"checked\"" : "") + " />\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.ZooKeeperHost") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"zkhost\" type=\"text\" size=\"32\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkHost) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.ZooKeeperClientTimeout") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"zkclienttimeout\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkClientTimeout) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrCloudConnector.ZooKeeperConnectTimeout") + "</nobr></td>\n"+
              "    <td class=\"value\">\n"+
              "      <input name=\"zkconnecttimeout\" type=\"text\" size=\"16\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkConnectTimeout)+"\"/>\n"+
              "    </td>\n"+
              "  </tr>\n"+
              "  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n"+
              "  <tr>\n"+
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.UseSolr") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"solrtype\" type=\"radio\" value=\"solr\" " + (solrType.equals("solr") ? " checked=\"checked\"" : "") + " />\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.Protocol") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <select name=\"protocol\">\n" +
              "        <option value=\"http\"" + (protocol.equals("http") ? " selected=\"true\"" : "") + ">http</option>\n" +
              "        <option value=\"https\"" + (protocol.equals("https") ? " selected=\"true\"" : "") + ">https</option>\n" +
              "      </select>\n"+
              "    </td>\n"+
              "  </tr>\n"+
              "  <tr>\n"+
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrCloudConnector.Host") + "</nobr></td>\n"+
              "    <td class=\"value\">\n"+
              "      <input name=\"host\" type=\"text\" size=\"32\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(host)+"\"/>\n"+
              "    </td>\n"+
              "  </tr>\n"+
              "  <tr>\n"+
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"SolrCloudConnector.Port") + "</nobr></td>\n"+
              "    <td class=\"value\">\n"+
              "      <input name=\"port\" type=\"text\" size=\"5\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(port)+"\"/>\n"+
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.Context") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"context\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(context) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.Collection") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"collection\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(collection) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.UpdateHandler") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"updatepath\" type=\"text\" size=\"32\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(updatePath) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.RemoveHandler") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"removepath\" type=\"text\" size=\"32\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(removePath) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.StatusHandler") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"statuspath\" type=\"text\" size=\"32\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(statusPath) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.HttoClientConnectionTimeout") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"httpclientconnectiontimeout\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(httpClientConnectionTimeout) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.HttoClientSocketTimeout") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"httpclientsockettimeout\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(httpClientSocketTimeout) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr><td colspan=\"2\" class=\"separator\"><hr/></td></tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.Realm") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"realm\" type=\"text\" size=\"32\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(realm) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.UserID") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"userid\" type=\"text\" size=\"32\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userID) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.Password") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input type=\"password\" size=\"32\" name=\"password\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n" +
              "  <tr>\n"+
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.SSLTrustCertificateList") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input type=\"hidden\" name=\"solrkeystorealias\" value=\"\"/>\n" +
              "      <table class=\"displaytable\">\n"
          );
      // List the individual certificates in the store, with a delete button for each
      String[] contents = localKeystore.getContents();
      if (contents.length == 0) {
        out.print(
            "        <tr><td class=\"message\" colspan=\"2\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.NoCertificatesPresent") + "</nobr></td></tr>\n"
            );
      } else {
        int i = 0;
        while (i < contents.length) {
          String alias = contents[i];
          String description = localKeystore.getDescription(alias);
          if (description.length() > 128) {
            description = description.substring(0,125) + "...";
          }
          out.print(
              "        <tr>\n" +
                  "          <td class=\"value\"><input type=\"button\" onclick='Javascript:SolrCloudDeleteCertificate(\"" + org.apache.manifoldcf.ui.util.Encoder.attributeJavascriptEscape(alias) + "\")' alt=\"" + Messages.getAttributeString(locale, "SolrCloudConnector.DeleteCert") + " " + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(alias) + "\" value=\"Delete\"/></td>\n" +
                  "          <td>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(description) + "</td>\n" +
                  "        </tr>\n"
              );
          i++;
        }
      }
      out.print(
          "      </table>\n"+
              "      <input type=\"button\" onclick='Javascript:SolrCloudAddCertificate()' alt=\"" + Messages.getAttributeString(locale, "SolrCloudConnector.AddCert") + "\" value=\"" + Messages.getAttributeString(locale, "SolrCloudConnector.Add") + "\"/>&nbsp;\n" +
              "      " + Messages.getBodyString(locale, "SolrCloudConnector.Certificate") + "&nbsp;<input name=\"solrcertificate\" size=\"50\" type=\"file\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "</table>\n"
          );
    } else {
      // Server tab hiddens
      out.print(
          "<input type=\"hidden\" name=\"solrtype\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(solrType) + "\"/>\n" +
              "<input type=\"hidden\" name=\"zkhost\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkHost) + "\"/>\n" +
              "<input type=\"hidden\" name=\"zkclienttimeout\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkClientTimeout) + "\"/>\n" +
              "<input type=\"hidden\" name=\"zkconnecttimeout\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(zkConnectTimeout) + "\"/>\n" +
              "<input type=\"hidden\" name=\"protocol\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(protocol) + "\"/>\n" +
              "<input type=\"hidden\" name=\"host\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(host) + "\"/>\n" +
              "<input type=\"hidden\" name=\"port\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(port) + "\"/>\n" +
              "<input type=\"hidden\" name=\"context\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(context) + "\"/>\n" +
              "<input type=\"hidden\" name=\"collection\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(collection) + "\"/>\n" +
              "<input type=\"hidden\" name=\"updatepath\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(updatePath) + "\"/>\n" +
              "<input type=\"hidden\" name=\"removepath\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(removePath) + "\"/>\n" +
              "<input type=\"hidden\" name=\"statuspath\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(statusPath) + "\"/>\n" +
              "<input type=\"hidden\" name=\"httpclientconnectiontimeout\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(httpClientConnectionTimeout) + "\"/>\n" +
              "<input type=\"hidden\" name=\"httpclientsockettimeout\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(httpClientSocketTimeout) + "\"/>\n" +
              "<input type=\"hidden\" name=\"realm\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(realm) + "\"/>\n" +
              "<input type=\"hidden\" name=\"userid\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(userID) + "\"/>\n" +
              "<input type=\"hidden\" name=\"password\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(password) + "\"/>\n"
          );
    }

    // "Schema" tab
    if (tabName.equals(Messages.getString(locale, "SolrCloudConnector.Schema"))) {
      out.print(
          "<table class=\"displaytable\">\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.UniqueKeyField") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"uniquekeyfield\" type=\"text\" size=\"32\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(uniqueKeyField) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "</table>\n"
          );
    } else {
      out.print(
          "<input type=\"hidden\" name=\"uniquekeyfield\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(uniqueKeyField) + "\"/>\n"
          );
    }

    // "Documents" tab
    if (tabName.equals(Messages.getString(locale, "SolrCloudConnector.Documents"))) {
      out.print(
          "<table class=\"displaytable\">\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.MaximumDocumentLength") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"maxdocumentlength\" type=\"text\" size=\"16\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(maxLength) + "\"/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.IncludedMimeTypes") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <textarea rows=\"10\" cols=\"20\" name=\"includedmimetypes\">" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(includedMimeTypes) + "</textarea>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.ExcludedMimeTypes") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <textarea rows=\"10\" cols=\"20\" name=\"excludedmimetypes\">" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(excludedMimeTypes) + "</textarea>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "</table>\n"
          );
    } else {
      out.print(
          "<input type=\"hidden\" name=\"maxdocumentlength\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(maxLength) + "\"/>\n" +
              "<input type=\"hidden\" name=\"includedmimetypes\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(includedMimeTypes) + "\"/>\n" +
              "<input type=\"hidden\" name=\"excludedmimetypes\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(excludedMimeTypes) + "\"/>\n"
          );
    }

    // "Commits" tab
    if (tabName.equals(Messages.getString(locale, "SolrCloudConnector.Commits"))) {
      out.print(
          "<table class=\"displaytable\">\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.CommitAtEndOfEveryJob") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"commits_present\" type=\"hidden\" value=\"true\"/>\n" +
              "      <input name=\"commits\" type=\"checkbox\" value=\"true\"" + (commits.equals("true") ? " checked=\"yes\"" : "") + "/>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.CommitEachDocumentWithin") + "</nobr></td>\n" +
              "    <td class=\"value\">\n" +
              "      <input name=\"commitwithin\" type=\"text\" size=\"16\" value=\"" + commitWithin + "\"/>\n" +
              "    </td>\n"+
              "  </tr>\n"+
              "</table>\n"
          );
    } else {
      out.print(
          "<input type=\"hidden\" name=\"commits_present\" value=\"true\"/>\n" +
              "<input name=\"commits\" type=\"hidden\" value=\"" + commits + "\"/>\n" +
              "<input name=\"commitwithin\" type=\"hidden\" value=\"" + commitWithin + "\"/>\n"
          );
    }

    // Prepare for the argument tab
    Map argumentMap = new HashMap();
    int i = 0;
    while (i < parameters.getChildCount()) {
      ConfigNode sn = parameters.getChild(i++);
      if (sn.getType().equals(SolrCloudConfig.NODE_ARGUMENT)) {
        String name = sn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_NAME);
        String value = sn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_VALUE);
        ArrayList values = (ArrayList)argumentMap.get(name);
        if (values == null) {
          values = new ArrayList();
          argumentMap.put(name, values);
        }
        values.add(value);
      }
    }

    // "Arguments" tab
    if (tabName.equals(Messages.getString(locale, "SolrCloudConnector.Arguments"))) {
      // For the display, sort the arguments into alphabetic order
      String[] sortArray = new String[argumentMap.size()];
      i = 0;
      Iterator iter = argumentMap.keySet().iterator();
      while (iter.hasNext()) {
        sortArray[i++] = (String)iter.next();
      }
      java.util.Arrays.sort(sortArray);
      out.print(
          "<table class=\"displaytable\">\n" +
              "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.Arguments2") + "</nobr></td>\n" +
              "    <td class=\"boxcell\">\n" +
              "      <table class=\"formtable\">\n" +
              "        <tr class=\"formheaderrow\">\n" +
              "          <td class=\"formcolumnheader\"></td>\n" +
              "          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.Name") + "</nobr></td>\n" +
              "          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.Value") + "</nobr></td>\n" +
              "        </tr>\n"
          );
      i = 0;
      int k = 0;
      while (k < sortArray.length) {
        String name = sortArray[k++];
        ArrayList values = (ArrayList)argumentMap.get(name);
        int j = 0;
        while (j < values.size()) {
          String value = (String)values.get(j++);
          // Its prefix will be...
          String prefix = "argument_" + Integer.toString(i);
          out.print(
              "        <tr class=\"" + (((i % 2) == 0) ? "evenformrow" : "oddformrow") + "\">\n" +
                  "          <td class=\"formcolumncell\">\n" +
                  "            <a name=\"" + prefix + "\"><input type=\"button\" value=\"Delete\" alt=\"" + Messages.getAttributeString(locale, "SolrCloudConnector.DeleteArgument") + " " + Integer.toString(i + 1) + "\" onclick=\"javascript:deleteArgument(" + Integer.toString(i) + ");" + "\"/>\n" +
                  "              <input type=\"hidden\" name=\"" + prefix + "_op" + "\" value=\"Continue\"/>\n" +
                  "              <input type=\"hidden\" name=\"" + prefix + "_name" + "\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name) + "\"/>\n" +
                  "            </a>\n" +
                  "          </td>\n" +
                  "          <td class=\"formcolumncell\">\n" +
                  "            <nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(name) + "</nobr>\n" +
                  "          </td>\n" +
                  "          <td class=\"formcolumncell\">\n" +
                  "            <nobr><input type=\"text\" size=\"30\" name=\"" + prefix + "_value" + "\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(value) + "\"</nobr>\n" +
                  "          </td>\n" +
                  "        </tr>\n"
              );
          i++;
        }
      }
      if (i == 0) {
        out.print(
            "        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale, "SolrCloudConnector.NoArgumentsSpecified") + "</td></tr>\n"
            );
      }
      out.print(
          "        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n" +
              "        <tr class=\"formrow\">\n" +
              "          <td class=\"formcolumncell\">\n" +
              "            <a name=\"argument\"><input type=\"button\" value=\"Add\" alt=\"Add argument\" onclick=\"javascript:addArgument();\"/>\n" +
              "              <input type=\"hidden\" name=\"argument_count\" value=\"" + Integer.toString(i) + "\"/>\n" +
              "              <input type=\"hidden\" name=\"argument_op\" value=\"Continue\"/>\n" +
              "            </a>\n" +
              "          </td>\n" +
              "          <td class=\"formcolumncell\">\n" +
              "            <nobr><input type=\"text\" size=\"30\" name=\"argument_name\" value=\"\"/></nobr>\n" +
              "          </td>\n" +
              "          <td class=\"formcolumncell\">\n" +
              "            <nobr><input type=\"text\" size=\"30\" name=\"argument_value\" value=\"\"/></nobr>\n" +
              "          </td>\n" +
              "        </tr>\n" +
              "      </table>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "</table>\n"
          );
    } else {
      // Emit hiddens for argument tab
      i = 0;
      Iterator iter = argumentMap.keySet().iterator();
      while (iter.hasNext()) {
        String name = (String)iter.next();
        ArrayList values = (ArrayList)argumentMap.get(name);
        int j = 0;
        while (j < values.size()) {
          String value = (String)values.get(j++);
          // It's prefix will be...
          String prefix = "argument_" + Integer.toString(i++);
          out.print(
              "<input type=\"hidden\" name=\"" + prefix + "_name\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(name) + "\"/>\n" +
                  "<input type=\"hidden\" name=\"" + prefix + "_value\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(value) + "\"/>\n"
              );
        }
      }
      out.print(
          "<input type=\"hidden\" name=\"argument_count\" value=\"" + Integer.toString(i) + "\"/>\n"
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
          throws ManifoldCFException {
    String solrType = variableContext.getParameter("solrtype");
    if (solrType != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_SOLR_TYPE, solrType);
    }

    String zkHost = variableContext.getParameter("zkhost");
    if (zkHost != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_ZOOKEEPER_HOST, zkHost);
    }

    String zkClientTimeout = variableContext.getParameter("zkclienttimeout");
    if (zkClientTimeout != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_ZOOKEEPER_CLIENT_TIMEOUT, zkClientTimeout);
    }

    String zkConnectTimeout = variableContext.getParameter("zkconnecttimeout");
    if (zkConnectTimeout != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_ZOOKEEPER_CONNECT_TIMEOUT, zkConnectTimeout);
    }

    String protocol = variableContext.getParameter("protocol");
    if (protocol != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_PROTOCOL, protocol);
    }

    String host = variableContext.getParameter("host");
    if (host != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_HOST, host);
    }

    String port = variableContext.getParameter("port");
    if (port != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_PORT, port);
    }

    String context = variableContext.getParameter("context");
    if (context != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_CONTEXT, context);
    }

    String collection = variableContext.getParameter("collection");
    if (collection != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_COLLECTION, collection);
    }

    String updatePath = variableContext.getParameter("updatepath");
    if (updatePath != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_UPDATEPATH, updatePath);
    }

    String removePath = variableContext.getParameter("removepath");
    if (removePath != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_REMOVEPATH, removePath);
    }

    String statusPath = variableContext.getParameter("statuspath");
    if (statusPath != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_STATUSPATH, statusPath);
    }

    String httpClientConnectionTimeout = variableContext.getParameter("httpclientconnectiontimeout");
    if (httpClientConnectionTimeout != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_HTTP_CLIENT_CONNECTION_TIMEOUT, httpClientConnectionTimeout);
    }

    String httpClientSocketTimeout = variableContext.getParameter("httpclientsockettimeout");
    if (httpClientSocketTimeout != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_HTTP_CLIENT_SOCKET_TIMEOUT, httpClientSocketTimeout);
    }

    String realm = variableContext.getParameter("realm");
    if (realm != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_REALM, realm);
    }

    String userID = variableContext.getParameter("userid");
    if (userID != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_USERID, userID);
    }

    String password = variableContext.getParameter("password");
    if (password != null) {
      parameters.setObfuscatedParameter(SolrCloudConfig.PARAM_PASSWORD, password);
    }

    String keystoreValue = variableContext.getParameter("keystoredata");
    IKeystoreManager mgr;
    if (keystoreValue != null) {
      mgr = KeystoreManagerFactory.make("", keystoreValue);
    } else {
      mgr = KeystoreManagerFactory.make("");
    }
    parameters.setParameter(SolrCloudConfig.PARAM_KEYSTORE, mgr.getString());

    String uniqueKeyField = variableContext.getParameter("uniquekeyfield");
    if (uniqueKeyField != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_UNIQUE_KEY_FIELD, uniqueKeyField);
    }

    String maxLength = variableContext.getParameter("maxdocumentlength");
    if (maxLength != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_MAXLENGTH, maxLength);
    }

    String includedMimeTypes = variableContext.getParameter("includedmimetypes");
    if (includedMimeTypes != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_INCLUDEDMIMETYPES, includedMimeTypes);
    }

    String excludedMimeTypes = variableContext.getParameter("excludedmimetypes");
    if (excludedMimeTypes != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_EXCLUDEDMIMETYPES, excludedMimeTypes);
    }

    String commitsPresent = variableContext.getParameter("commits_present");
    if (commitsPresent != null) {
      String commits = variableContext.getParameter("commits");
      if (commits == null) {
        commits = "false";
      }
      parameters.setParameter(SolrCloudConfig.PARAM_COMMITS, commits);
    }

    String commitWithin = variableContext.getParameter("commitwithin");
    if (commitWithin != null) {
      parameters.setParameter(SolrCloudConfig.PARAM_COMMITWITHIN, commitWithin);
    }

    String x = variableContext.getParameter("argument_count");
    if (x != null && x.length() > 0) {
      // About to gather the argument nodes, so get rid of the old ones.
      int i = 0;
      while (i < parameters.getChildCount()) {
        ConfigNode node = parameters.getChild(i);
        if (node.getType().equals(SolrCloudConfig.NODE_ARGUMENT)) {
          parameters.removeChild(i);
        } else {
          i++;
        }
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count) {
        String prefix = "argument_" + Integer.toString(i);
        String op = variableContext.getParameter(prefix + "_op");
        if (op == null || !op.equals("Delete")) {
          // Gather the name and value.
          String name = variableContext.getParameter(prefix + "_name");
          String value = variableContext.getParameter(prefix + "_value");
          ConfigNode node = new ConfigNode(SolrCloudConfig.NODE_ARGUMENT);
          node.setAttribute(SolrCloudConfig.ATTRIBUTE_NAME, name);
          node.setAttribute(SolrCloudConfig.ATTRIBUTE_VALUE, value);
          parameters.addChild(parameters.getChildCount(), node);
        }
        i++;
      }
      String addop = variableContext.getParameter("argument_op");
      if (addop != null && addop.equals("Add")) {
        String name = variableContext.getParameter("argument_name");
        String value = variableContext.getParameter("argument_value");
        ConfigNode node = new ConfigNode(SolrCloudConfig.NODE_ARGUMENT);
        node.setAttribute(SolrCloudConfig.ATTRIBUTE_NAME, name);
        node.setAttribute(SolrCloudConfig.ATTRIBUTE_VALUE, value);
        parameters.addChild(parameters.getChildCount(), node);
      }
    }

    String configOp = variableContext.getParameter("configop");
    if (configOp != null) {
      if (configOp.equals("Delete")) {
        String alias = variableContext.getParameter("solrkeystorealias");
        keystoreValue = parameters.getParameter(SolrCloudConfig.PARAM_KEYSTORE);
        if (keystoreValue != null) {
          mgr = KeystoreManagerFactory.make("", keystoreValue);
        } else {
          mgr = KeystoreManagerFactory.make("");
        }
        mgr.remove(alias);
        parameters.setParameter(SolrCloudConfig.PARAM_KEYSTORE, mgr.getString());
      } else if (configOp.equals("Add")) {
        String alias = IDFactory.make(threadContext);
        byte[] certificateValue = variableContext.getBinaryBytes("solrcertificate");
        keystoreValue = parameters.getParameter(SolrCloudConfig.PARAM_KEYSTORE);
        if (keystoreValue != null) {
          mgr = KeystoreManagerFactory.make("", keystoreValue);
        } else {
          mgr = KeystoreManagerFactory.make("");
        }
        java.io.InputStream is = new java.io.ByteArrayInputStream(certificateValue);
        String certError = null;
        try {
          mgr.importCertificate(alias, is);
        } catch (Throwable e) {
          certError = e.getMessage();
        } finally {
          try {
            is.close();
          } catch (IOException e) {
            // Eat this exception
          }
        }

        if (certError != null) {
          return "Illegal certificate: " + certError;
        }
        parameters.setParameter(SolrCloudConfig.PARAM_KEYSTORE, mgr.getString());
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
          throws ManifoldCFException, IOException {
    out.print(
        "<table class=\"displaytable\">\n" +
            "  <tr>\n" +
            "    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"SolrCloudConnector.Parameters") + "</nobr></td>\n" +
            "    <td class=\"value\" colspan=\"3\">\n"
        );
    Iterator iter = parameters.listParameters();
    while (iter.hasNext()) {
      String param = (String)iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length() && param.substring(param.length() - "password".length()).equalsIgnoreCase("password")) {
        out.print(
            "      <nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param) + "=********</nobr><br/>\n"
            );
      } else if (param.length() >= "keystore".length() && param.substring(param.length() - "keystore".length()).equalsIgnoreCase("keystore")) {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("", value);
        out.print(
            "      <nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param) + "=&lt;" + Integer.toString(kmanager.getContents().length) + " certificate(s)&gt;</nobr><br/>\n"
            );
      } else {
        out.print(
            "      <nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param) + "=" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value) + "</nobr><br/>\n"
            );
      }
    }

    out.print(
        "    </td>\n" +
            "  </tr>\n" +
            "\n" +
            "  <tr>\n" +
            "    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"SolrCloudConnector.Arguments3") + "</nobr></td>\n" +
            "    <td class=\"boxcell\" colspan=\"3\">\n" +
            "      <table class=\"formtable\">\n" +
            "        <tr class=\"formheaderrow\">\n" +
            "          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrCloudConnector.Name") + "</nobr></td>\n" +
            "          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"SolrCloudConnector.Value") + "</nobr></td>\n" +
            "        </tr>\n"
        );

    int i = 0;
    int instanceNumber = 0;
    while (i < parameters.getChildCount()) {
      ConfigNode cn = parameters.getChild(i++);
      if (cn.getType().equals(SolrCloudConfig.NODE_ARGUMENT)) {
        // An argument node!  Look for all its parameters.
        String name = cn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_NAME);
        String value = cn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_VALUE);

        out.print(
            "        <tr class=\"" + (((instanceNumber % 2) == 0) ? "evenformrow" : "oddformrow") + "\">\n" +
                "          <td class=\"formcolumncell\"><nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(name) + "</nobr></td>\n" +
                "          <td class=\"formcolumncell\"><nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value) + "</nobr></td>\n" +
                "        </tr>\n"
            );

        instanceNumber++;
      }
    }
    if (instanceNumber == 0) {
      out.print(
          "        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"5\">" + Messages.getBodyString(locale,"SolrCloudConnector.NoArguments") + "</td></tr>\n"
          );
    }

    out.print(
        "      </table>\n" +
            "    </td>\n" +
            "  </tr>\n" +
            "</table>\n"
        );
  }

  /** Output the specification header section.
   * This method is called in the head section of a job page which has selected an output connection of the current type.  Its purpose is to add the required tabs
   * to the list, and to output any javascript methods that might be needed by the job editing HTML.
   *@param out is the output to which any HTML should be sent.
   *@param os is the current output specification for this job.
   *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
   */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, OutputSpecification os, List<String> tabsArray)
      throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale,"SolrCloudConnector.SolrFieldMapping"));
    out.print(
        "<script type=\"text/javascript\">\n" +
            "<!--\n" +
            "function checkOutputSpecification()\n" +
            "{\n" +
            "  return true;\n" +
            "}\n" +
            "\n" +
            "function addFieldMapping()\n" +
            "{\n" +
            "  if (editjob.solr_fieldmapping_source.value == \"\")\n" +
            "  {\n" +
            "    alert(\"" + Messages.getBodyJavascriptString(locale, "SolrCloudConnector.FieldMapMustHaveNonNullSource") + "\");\n" +
            "    editjob.solr_fieldmapping_source.focus();\n" +
            "    return;\n" +
            "  }\n" +
            "  editjob.solr_fieldmapping_op.value=\"Add\";\n" +
            "  postFormSetAnchor(\"solr_fieldmapping\");\n" +
            "}\n" +
            "\n" +
            "function deleteFieldMapping(i)\n" +
            "{\n" +
            "  // Set the operation\n" +
            "  eval(\"editjob.solr_fieldmapping_\"+i+\"_op.value=\\\"Delete\\\"\");\n" +
            "  // Submit\n" +
            "  if (editjob.solr_fieldmapping_count.value==i)\n" +
            "    postFormSetAnchor(\"solr_fieldmapping\");\n" +
            "  else\n" +
            "    postFormSetAnchor(\"solr_fieldmapping_\"+i)\n" +
            "  // Undo, so we won't get two deletes next time\n" +
            "  eval(\"editjob.solr_fieldmapping_\"+i+\"_op.value=\\\"Continue\\\"\");\n" +
            "}\n" +
            "\n" +
            "//-->\n" +
            "</script>\n"
        );
  }

  /** Output the specification body section.
   * This method is called in the body section of a job page which has selected an output connection of the current type.  Its purpose is to present the required form elements for editing.
   * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
   * form is "editjob".
   *@param out is the output to which any HTML should be sent.
   *@param os is the current output specification for this job.
   *@param tabName is the current tab name.
   */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, OutputSpecification os, String tabName)
      throws ManifoldCFException, IOException {
    // Prep for field mapping tab
    HashMap fieldMap = new HashMap();
    int i = 0;
    while (i < os.getChildCount()) {
      SpecificationNode sn = os.getChild(i++);
      if (sn.getType().equals(SolrCloudConfig.NODE_FIELDMAP)) {
        String source = sn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_TARGET);
        if (target != null && target.length() == 0) {
          target = null;
        }
        fieldMap.put(source, target);
      }
    }

    // Field Mapping tab
    if (tabName.equals(Messages.getString(locale, "SolrCloudConnector.SolrFieldMapping"))) {
      out.print(
          "<table class=\"displaytable\">\n" +
              "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n" +
              "  <tr>\n" +
              "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.FieldMappings") + "</nobr></td>\n" +
              "    <td class=\"boxcell\">\n" +
              "      <table class=\"formtable\">\n" +
              "        <tr class=\"formheaderrow\">\n" +
              "          <td class=\"formcolumnheader\"></td>\n" +
              "          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.MetadataFieldName") + "</nobr></td>\n" +
              "          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.SolrFieldName") + "</nobr></td>\n" +
              "        </tr>\n"
          );

      String[] sourceFieldNames = new String[fieldMap.size()];
      Iterator iter = fieldMap.keySet().iterator();
      i = 0;
      while (iter.hasNext()) {
        sourceFieldNames[i++] = (String)iter.next();
      }
      java.util.Arrays.sort(sourceFieldNames);

      int fieldCounter = 0;
      i = 0;
      while (i < sourceFieldNames.length) {
        String source = sourceFieldNames[i++];
        String target = (String)fieldMap.get(source);
        String targetDisplay = target;
        if (target == null) {
          target = "";
          targetDisplay = "(remove)";
        }
        // It's prefix will be...
        String prefix = "solr_fieldmapping_" + Integer.toString(fieldCounter);
        out.print(
            "        <tr class=\"" + (((fieldCounter % 2) == 0) ? "evenformrow" : "oddformrow") + "\">\n" +
                "          <td class=\"formcolumncell\">\n" +
                "            <a name=\"" + prefix + "\">\n" +
                "              <input type=\"button\" value=\"Delete\" alt=\"" + Messages.getAttributeString(locale, "SolrCloudConnector.DeleteFieldMapping") + Integer.toString(fieldCounter + 1) + "\" onclick='javascript:deleteFieldMapping(" + Integer.toString(fieldCounter) + ");'/>\n" +
                "              <input type=\"hidden\" name=\"" + prefix + "_op\" value=\"Continue\"/>\n" +
                "              <input type=\"hidden\" name=\"" + prefix + "_source\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(source) + "\"/>\n" +
                "              <input type=\"hidden\" name=\"" + prefix + "_target\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(target) + "\"/>\n" +
                "            </a>\n" +
                "          </td>\n" +
                "          <td class=\"formcolumncell\">\n" +
                "            <nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(source) + "</nobr>\n" +
                "          </td>\n" +
                "          <td class=\"formcolumncell\">\n" +
                "            <nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(targetDisplay) + "</nobr>\n" +
                "          </td>\n" +
                "        </tr>\n"
            );
        fieldCounter++;
      }

      if (fieldCounter == 0) {
        out.print(
            "        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale, "SolrCloudConnector.NoFieldMappingSpecified") + "</td></tr>\n"
            );
      }
      out.print(
          "        <tr class=\"formrow\"><td class=\"formseparator\" colspan=\"3\"><hr/></td></tr>\n" +
              "        <tr class=\"formrow\">\n" +
              "          <td class=\"formcolumncell\">\n" +
              "            <a name=\"solr_fieldmapping\">\n" +
              "              <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"SolrCloudConnector.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"SolrCloudConnector.AddFieldMapping") + "\" onclick=\"javascript:addFieldMapping();\"/>\n" +
              "            </a>\n"+
              "            <input type=\"hidden\" name=\"solr_fieldmapping_count\" value=\"" + fieldCounter + "\"/>\n" +
              "            <input type=\"hidden\" name=\"solr_fieldmapping_op\" value=\"Continue\"/>\n" +
              "          </td>\n" +
              "          <td class=\"formcolumncell\">\n" +
              "            <nobr><input type=\"text\" size=\"15\" name=\"solr_fieldmapping_source\" value=\"\"/></nobr>\n" +
              "          </td>\n" +
              "          <td class=\"formcolumncell\">\n" +
              "            <nobr><input type=\"text\" size=\"15\" name=\"solr_fieldmapping_target\" value=\"\"/></nobr>\n" +
              "          </td>\n" +
              "        </tr>\n" +
              "      </table>\n" +
              "    </td>\n" +
              "  </tr>\n" +
              "</table>\n"
          );
    } else {
      // Hiddens for field mapping
      out.print(
          "<input type=\"hidden\" name=\"solr_fieldmapping_count\" value=\"" + Integer.toString(fieldMap.size()) + "\"/>\n"
          );
      Iterator iter = fieldMap.keySet().iterator();
      int fieldCounter = 0;
      while (iter.hasNext()) {
        String source = (String)iter.next();
        String target = (String)fieldMap.get(source);
        if (target == null) {
          target = "";
        }
        // It's prefix will be...
        String prefix = "solr_fieldmapping_" + Integer.toString(fieldCounter);
        out.print(
            "<input type=\"hidden\" name=\"" + prefix + "_source\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(source) + "\"/>\n" +
                "<input type=\"hidden\" name=\"" + prefix + "_target\" value=\"" + org.apache.manifoldcf.ui.util.Encoder.attributeEscape(target) + "\"/>\n"
            );
        fieldCounter++;
      }
    }

  }

  /** Process a specification post.
   * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
   * posted.  Its purpose is to gather form information and modify the output specification accordingly.
   * The name of the posted form is "editjob".
   *@param variableContext contains the post data, including binary file-upload information.
   *@param os is the current output specification for this job.
   *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
   */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, OutputSpecification os)
      throws ManifoldCFException {
    String x = variableContext.getParameter("solr_fieldmapping_count");
    if (x != null && x.length() > 0) {
      // About to gather the fieldmapping nodes, so get rid of the old ones.
      int i = 0;
      while (i < os.getChildCount()) {
        SpecificationNode node = os.getChild(i);
        if (node.getType().equals(SolrCloudConfig.NODE_FIELDMAP)) {
          os.removeChild(i);
        } else {
          i++;
        }
      }
      int count = Integer.parseInt(x);
      i = 0;
      while (i < count) {
        String prefix = "solr_fieldmapping_" + Integer.toString(i);
        String op = variableContext.getParameter(prefix + "_op");
        if (op == null || !op.equals("Delete")) {
          // Gather the fieldmap etc.
          String source = variableContext.getParameter(prefix + "_source");
          String target = variableContext.getParameter(prefix + "_target");
          if (target == null) {
            target = "";
          }
          SpecificationNode node = new SpecificationNode(SolrCloudConfig.NODE_FIELDMAP);
          node.setAttribute(SolrCloudConfig.ATTRIBUTE_SOURCE, source);
          node.setAttribute(SolrCloudConfig.ATTRIBUTE_TARGET, target);
          os.addChild(os.getChildCount(), node);
        }
        i++;
      }
      String addop = variableContext.getParameter("solr_fieldmapping_op");
      if (addop != null && addop.equals("Add")) {
        String source = variableContext.getParameter("solr_fieldmapping_source");
        String target = variableContext.getParameter("solr_fieldmapping_target");
        if (target == null) {
          target = "";
        }
        SpecificationNode node = new SpecificationNode(SolrCloudConfig.NODE_FIELDMAP);
        node.setAttribute(SolrCloudConfig.ATTRIBUTE_SOURCE, source);
        node.setAttribute(SolrCloudConfig.ATTRIBUTE_TARGET, target);
        os.addChild(os.getChildCount(), node);
      }
    }
    return null;
  }

  /** View specification.
   * This method is called in the body section of a job's view page.  Its purpose is to present the output specification information to the user.
   * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
   *@param out is the output to which any HTML should be sent.
   *@param os is the current output specification for this job.
   */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, OutputSpecification os)
      throws ManifoldCFException, IOException {
    // Prep for field mappings
    HashMap fieldMap = new HashMap();
    int i = 0;
    while (i < os.getChildCount()) {
      SpecificationNode sn = os.getChild(i++);
      if (sn.getType().equals(SolrCloudConfig.NODE_FIELDMAP)) {
        String source = sn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_SOURCE);
        String target = sn.getAttributeValue(SolrCloudConfig.ATTRIBUTE_TARGET);
        if (target != null && target.length() == 0) {
          target = null;
        }
        fieldMap.put(source, target);
      }
    }

    String[] sourceFieldNames = new String[fieldMap.size()];
    Iterator iter = fieldMap.keySet().iterator();
    i = 0;
    while (iter.hasNext()) {
      sourceFieldNames[i++] = (String)iter.next();
    }
    java.util.Arrays.sort(sourceFieldNames);

    // Display field mappings
    out.print(
        "\n" +
            "<table class=\"displaytable\">\n" +
            "  <tr>\n" +
            "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.FieldMappings") + "</nobr></td>\n" +
            "    <td class=\"boxcell\">\n" +
            "      <table class=\"formtable\">\n" +
            "        <tr class=\"formheaderrow\">\n" +
            "          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.MetadataFieldName") + "</nobr></td>\n" +
            "          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale, "SolrCloudConnector.SolrFieldName") + "</nobr></td>\n" +
            "        </tr>\n"
        );

    int fieldCounter = 0;
    while (fieldCounter < sourceFieldNames.length) {
      String source = sourceFieldNames[fieldCounter++];
      String target = (String)fieldMap.get(source);
      String targetDisplay = target;
      if (target == null) {
        target = "";
        targetDisplay = "(remove)";
      }
      out.print(
          "        <tr class=\"" + (((fieldCounter % 2) == 0) ? "evenformrow" : "oddformrow") + "\">\n" +
              "          <td class=\"formcolumncell\">\n" +
              "            <nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(source) + "</nobr>\n" +
              "          </td>\n" +
              "          <td class=\"formcolumncell\">\n" +
              "            <nobr>" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(targetDisplay) + "</nobr>\n" +
              "          </td>\n" +
              "        </tr>\n"
          );
      fieldCounter++;
    }

    if (fieldCounter == 0) {
      out.print(
          "        <tr class=\"formrow\"><td class=\"formmessage\" colspan=\"3\">" + Messages.getBodyString(locale, "SolrCloudConnector.NoFieldMappingSpecified") + "</td></tr>\n"
          );
    }
    out.print(
        "      </table>\n" +
            "    </td>\n" +
            "  </tr>\n" +
            "</table>\n"
        );

  }

  /**
   * @throws ManifoldCFException
   */
  protected void setParams() throws ManifoldCFException {
    /*
     * Update handler
     */
    this.updatePath = this.params.getParameter(SolrCloudConfig.PARAM_UPDATEPATH);
    if (this.updatePath == null || this.updatePath.length() == 0) {
      this.updatePath = "";
    }

    /*
     * Remove handler
     */
    this.removePath = this.params.getParameter(SolrCloudConfig.PARAM_REMOVEPATH);
    if (this.removePath == null || this.removePath.length() == 0) {
      this.removePath = "";
    }

    /*
     * Status handler
     */
    this.statusPath = this.params.getParameter(SolrCloudConfig.PARAM_STATUSPATH);
    if (this.statusPath == null || this.statusPath.length() == 0) {
      this.statusPath = "";
    }

    /*
     * Realm
     */
    this.realm = this.params.getParameter(SolrCloudConfig.PARAM_REALM);

    /*
     * User ID
     */
    this.userID = this.params.getParameter(SolrCloudConfig.PARAM_USERID);

    /*
     * Password
     */
    this.password = this.params.getObfuscatedParameter(SolrCloudConfig.PARAM_PASSWORD);

    /*
     * Keystore
     */
    String keystoreData = this.params.getParameter(SolrCloudConfig.PARAM_KEYSTORE);
    if (keystoreData != null) {
      this.keystoreManager = KeystoreManagerFactory.make("", keystoreData);
    } else {
      this.keystoreManager = null;
    }

    /*
     * ID field name
     */
    this.uniqueKeyField = this.params.getParameter(SolrCloudConfig.PARAM_UNIQUE_KEY_FIELD);
    if (this.uniqueKeyField == null || this.uniqueKeyField.length() == 0) {
      this.uniqueKeyField = "id";
    }

    /*
     * Maximum document length
     */
    String docMax = this.params.getParameter(SolrCloudConfig.PARAM_MAXLENGTH);
    if (docMax == null || docMax.length() == 0) {
      this.maxDocumentLength = null;
    } else {
      this.maxDocumentLength = new Long(docMax);
    }

    /*
     * Included mime types
     */
    this.includedMimeTypesString = this.params.getParameter(SolrCloudConfig.PARAM_INCLUDEDMIMETYPES);
    if (this.includedMimeTypesString == null || this.includedMimeTypesString.length() == 0) {
      this.includedMimeTypesString = null;
      this.includedMimeTypes = null;
    } else {
      // Parse the included mime types
      this.includedMimeTypes = parseMimeTypes(this.includedMimeTypesString);
      if (this.includedMimeTypes.size() == 0)
      {
        this.includedMimeTypesString = null;
        this.includedMimeTypes = null;
      }
    }

    /*
     * Excluded mime types
     */
    this.excludedMimeTypesString = this.params.getParameter(SolrCloudConfig.PARAM_EXCLUDEDMIMETYPES);
    if (this.excludedMimeTypesString == null || this.excludedMimeTypesString.length() == 0) {
      this.excludedMimeTypesString = null;
      this.excludedMimeTypes = null;
    } else {
      // Parse the included mime types
      this.excludedMimeTypes = parseMimeTypes(this.excludedMimeTypesString);
      if (this.excludedMimeTypes.size() == 0) {
        this.excludedMimeTypesString = null;
        this.excludedMimeTypes = null;
      }
    }

    /*
     * Commit at end of every job
     */
    String strCommits = this.params.getParameter(SolrCloudConfig.PARAM_COMMITS);
    if (strCommits == null || strCommits.length() == 0) {
      strCommits = "true";
    }
    this.doCommits = strCommits.equals("true");

    /*
     * Commit each document within
     */
    String strCommitWithin = this.params.getParameter(SolrCloudConfig.PARAM_COMMITWITHIN);
    if (strCommitWithin == null || strCommitWithin.length() == 0) {
      this.commitWithin = null;
    } else {
      this.commitWithin = new Integer(strCommitWithin);
    }

  }

  /** Convert an unqualified ACL to qualified form.
   * @param acl is the initial, unqualified ACL.
   * @param authorityNameString is the name of the governing authority for this document's acls, or null if none.
   * @param activities is the activities object, so we can report what's happening.
   * @return the modified ACL.
   */
  protected static String[] convertACL(String[] acl, String authorityNameString, IOutputAddActivity activities)
      throws ManifoldCFException {
    if (acl != null) {
      String[] rval = new String[acl.length];
      int i = 0;
      while (i < rval.length) {
        rval[i] = activities.qualifyAccessToken(authorityNameString, acl[i]);
        i++;
      }
      return rval;
    }
    return new String[0];
  }

  /** Detect if a mime type is indexable or not.  This method is used by participating repository connectors to pre-filter the number of
   * unusable documents that will be passed to this output connector.
   *@param outputDescription is the document's output version.
   *@param mimeType is the mime type of the document.
   *@return true if the mime type is indexable by this connector.
   */
  public boolean checkMimeTypeIndexable(String outputDescription, String mimeType)
      throws ManifoldCFException, ServiceInterruption {
    if (this.includedMimeTypes != null && this.includedMimeTypes.get(mimeType) == null) {
      return false;
    }
    if (this.excludedMimeTypes != null && this.excludedMimeTypes.get(mimeType) != null) {
      return false;
    }
    return super.checkMimeTypeIndexable(outputDescription, mimeType);
  }

  /** Pre-determine whether a document's length is indexable by this connector.  This method is used by participating repository connectors
   * to help filter out documents that are too long to be indexable.
   *@param outputDescription is the document's output version.
   *@param length is the length of the document.
   *@return true if the file is indexable.
   */
  public boolean checkLengthIndexable(String outputDescription, long length)
      throws ManifoldCFException, ServiceInterruption {
    if (maxDocumentLength != null && length > maxDocumentLength.longValue()) {
      return false;
    }
    return super.checkLengthIndexable(outputDescription, length);
  }

  /** Parse a mime type field into individual mime types in a hash */
  protected static Map<String,String> parseMimeTypes(String mimeTypes)
      throws ManifoldCFException {
    Map<String,String> rval = new HashMap<String,String>();
    try {
      java.io.Reader str = new java.io.StringReader(mimeTypes);
      try {
        java.io.BufferedReader is = new java.io.BufferedReader(str);
        try {
          while (true) {
            String nextString = is.readLine();
            if (nextString == null) {
              break;
            }
            if (nextString.length() == 0) {
              continue;
            }
            rval.put(nextString, nextString);
          }
          return rval;
        } finally {
          is.close();
        }
      } finally {
        str.close();
      }
    } catch (java.io.IOException e) {
      throw new ManifoldCFException("IO error: " + e.getMessage(), e);
    }
  }


}
