/* $Id: SvnConnector.java 994959 2010-09-08 10:04:42Z krycek $ */
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
package org.apache.manifoldcf.crawler.connectors.generic;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.manifoldcf.core.util.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.connectorcommon.common.XThreadInputStream;
import org.apache.manifoldcf.connectorcommon.common.XThreadStringBuffer;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
import org.apache.manifoldcf.crawler.connectors.generic.api.Item;
import org.apache.manifoldcf.crawler.connectors.generic.api.Items;
import org.apache.manifoldcf.crawler.connectors.generic.api.Meta;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.ui.util.Encoder;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class GenericConnector extends BaseRepositoryConnector {

  public static final String _rcsid = "@(#)$Id: GenericConnector.java 994959 2010-09-08 10:04:42Z redguy $";

  /**
   * Deny access token for default authority
   */
  private final static String defaultAuthorityDenyToken = "DEAD_AUTHORITY";

  private final static String ACTION_PARAM_NAME = "action";

  private final static String ACTION_CHECK = "check";

  private final static String ACTION_SEED = "seed";

  private final static String ACTION_ITEMS = "items";

  private final static String ACTION_ITEM = "item";

  private String genericLogin = null;

  private String genericPassword = null;

  private String genericEntryPoint = null;

  private int connectionTimeoutMillis = 60 * 1000;

  private int socketTimeoutMillis = 30 * 60 * 1000;

  protected static final String RELATIONSHIP_RELATED = "related";

  private ConcurrentHashMap<String, Item> documentCache = new ConcurrentHashMap<String, Item>(10);

  /**
   * Constructor.
   */
  public GenericConnector() {
  }

  @Override
  public int getMaxDocumentRequest() {
    return 10;
  }

  @Override
  public String[] getRelationshipTypes() {
    return new String[]{RELATIONSHIP_RELATED};
  }

  @Override
  public int getConnectorModel() {
    return GenericConnector.MODEL_ADD_CHANGE;
  }

  /**
   * For any given document, list the bins that it is a member of.
   */
  @Override
  public String[] getBinNames(String documentIdentifier) {
    // Return the host name
    return new String[]{genericEntryPoint};
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!
  /**
   * Connect. The configuration parameters are included.
   *
   * @param configParams are the configuration parameters for this connection.
   * Note well: There are no exceptions allowed from this call, since it is
   * expected to mainly establish connection parameters.
   */
  @Override
  public void connect(ConfigParams configParams) {
    super.connect(configParams);
    genericEntryPoint = getParam(configParams, "genericEntryPoint", null);
    genericLogin = getParam(configParams, "genericLogin", null);
    genericPassword = "";
    try {
      genericPassword = ManifoldCF.deobfuscate(getParam(configParams, "genericPassword", ""));
    } catch (ManifoldCFException ignore) {
    }
    connectionTimeoutMillis = Integer.parseInt(getParam(configParams, "genericConnectionTimeout", "60000"));
    if (connectionTimeoutMillis == 0) {
      connectionTimeoutMillis = 60000;
    }
    socketTimeoutMillis = Integer.parseInt(getParam(configParams, "genericSocketTimeout", "1800000"));
    if (socketTimeoutMillis == 0) {
      socketTimeoutMillis = 1800000;
    }
  }

  protected DefaultHttpClient getClient() throws ManifoldCFException {
    DefaultHttpClient cl = new DefaultHttpClient();
    if (genericLogin != null && !genericLogin.isEmpty()) {
      try {
        URL url = new URL(genericEntryPoint);
        Credentials credentials = new UsernamePasswordCredentials(genericLogin, genericPassword);
        cl.getCredentialsProvider().setCredentials(new AuthScope(url.getHost(), url.getPort() > 0 ? url.getPort() : 80, AuthScope.ANY_REALM), credentials);
        cl.addRequestInterceptor(new PreemptiveAuth(credentials), 0);
      } catch (MalformedURLException ex) {
        throw new ManifoldCFException("getClient exception: " + ex.getMessage(), ex);
      }
    }
    HttpConnectionParams.setConnectionTimeout(cl.getParams(), connectionTimeoutMillis);
    HttpConnectionParams.setSoTimeout(cl.getParams(), socketTimeoutMillis);
    return cl;
  }

  @Override
  public String check() throws ManifoldCFException {
    HttpClient client = getClient();
    try {
      CheckThread checkThread = new CheckThread(client, genericEntryPoint + "?" + ACTION_PARAM_NAME + "=" + ACTION_CHECK);
      checkThread.start();
      checkThread.join();
      if (checkThread.getException() != null) {
        Throwable thr = checkThread.getException();
        return "Check exception: " + thr.getMessage();
      }
      return checkThread.getResult();
    } catch (InterruptedException ex) {
      throw new ManifoldCFException(ex.getMessage(), ex, ManifoldCFException.INTERRUPTED);
    }
  }

  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption {

    long startTime;
    if (lastSeedVersion == null)
      startTime = 0L;
    else
    {
      // Unpack seed time from seed version string
      startTime = new Long(lastSeedVersion).longValue();
    }

    HttpClient client = getClient();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);

    StringBuilder url = new StringBuilder(genericEntryPoint);
    url.append("?").append(ACTION_PARAM_NAME).append("=").append(ACTION_SEED);
    if (startTime > 0) {
      url.append("&startTime=").append(sdf.format(new Date(startTime)));
    }
    url.append("&endTime=").append(sdf.format(new Date(seedTime)));
    for (int i = 0; i < spec.getChildCount(); i++) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals("param")) {
          String paramName = sn.getAttributeValue("name");
          String paramValue = sn.getValue();
          url.append("&").append(URLEncoder.encode(paramName)).append("=").append(URLEncoder.encode(paramValue));
      }
    }
    ExecuteSeedingThread t = new ExecuteSeedingThread(client, url.toString());
    try {
      t.start();
      boolean wasInterrupted = false;
      try {
        XThreadStringBuffer seedBuffer = t.getBuffer();

        // Pick up the paths, and add them to the activities, before we join with the child thread.
        while (true) {
          // The only kind of exceptions this can throw are going to shut the process down.
          String docPath = seedBuffer.fetch();
          if (docPath == null) {
            break;
          }
          // Add the pageID to the queue
          activities.addSeedDocument(docPath);
        }
      } catch (InterruptedException e) {
        wasInterrupted = true;
        throw e;
      } catch (ManifoldCFException e) {
        if (e.getErrorCode() == ManifoldCFException.INTERRUPTED) {
          wasInterrupted = true;
        }
        throw e;
      } finally {
        if (!wasInterrupted) {
          t.finishUp();
        }
      }
    } catch (InterruptedException e) {
      t.interrupt();
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e,
        ManifoldCFException.INTERRUPTED);
    }
    return new Long(seedTime).toString();
  }

  @Override
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
    IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption {

    // Forced acls
    String[] acls = getAcls(spec);
    // Sort it,
    java.util.Arrays.sort(acls);
    String rights = java.util.Arrays.toString(acls);

    String genericAuthMode = "provided";
    for (int i = 0; i < spec.getChildCount(); i++) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals("genericAuthMode")) {
        genericAuthMode = sn.getValue();
        break;
      }
    }

    HttpClient client = getClient();
    StringBuilder url = new StringBuilder(genericEntryPoint);

    url.append("?").append(ACTION_PARAM_NAME).append("=").append(ACTION_ITEMS);
    for (int i = 0; i < documentIdentifiers.length; i++) {
      url.append("&id[]=").append(URLEncoder.encode(documentIdentifiers[i]));
    }
    for (int i = 0; i < spec.getChildCount(); i++) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals("param")) {
        String paramName = sn.getAttributeValue("name");
        String paramValue = sn.getValue();
        url.append("&").append(URLEncoder.encode(paramName)).append("=").append(URLEncoder.encode(paramValue));
      }
    }
    
    String[] versions = null;
    try {
      DocumentVersionThread versioningThread = new DocumentVersionThread(client, url.toString(), documentIdentifiers, genericAuthMode, rights, documentCache);
      versioningThread.start();
      try {
        versions = versioningThread.finishUp();
      } catch (IOException ex) {
        handleIOException((IOException)ex);
      } catch (InterruptedException ex) {
        throw new ManifoldCFException(ex.getMessage(), ex, ManifoldCFException.INTERRUPTED);
      }
      
      // Figure out which ones we need to process, and which we should delete
      for (int i = 0; i < documentIdentifiers.length; i++) {
        String documentIdentifier = documentIdentifiers[i];
        String versionString = versions[i];
        if (versionString == null) {
          activities.deleteDocument(documentIdentifier);
          continue;
        }
        Item item = documentCache.get(documentIdentifier);
        if (item == null) {
          throw new ManifoldCFException("processDocuments error - no cache entry for: " + documentIdentifier);
        }

        if (item.related != null) {
          for (String rel : item.related) {
            activities.addDocumentReference(rel, documentIdentifier, RELATIONSHIP_RELATED);
          }
        }
        if (versionString.length() == 0 || activities.checkDocumentNeedsReindexing(documentIdentifier,versionString)) {
          
          // Process the document
          RepositoryDocument doc = new RepositoryDocument();
          if (item.mimeType != null) {
            doc.setMimeType(item.mimeType);
          }
          if (item.created != null) {
            doc.setCreatedDate(item.created);
          }
          if (item.updated != null) {
            doc.setModifiedDate(item.updated);
          }
          if (item.fileName != null) {
            doc.setFileName(item.fileName);
          }
          if (item.metadata != null) {
            HashMap<String, List<String>> meta = new HashMap<String, List<String>>();
            for (Meta m : item.metadata) {
              if (meta.containsKey(m.name)) {
                meta.get(m.name).add(m.value);
              } else {
                List<String> list = new ArrayList<String>(1);
                list.add(m.value);
                meta.put(m.name, list);
              }
            }
            for (String name : meta.keySet()) {
              List<String> values = meta.get(name);
              if (values.size() > 1) {
                String[] svals = new String[values.size()];
                for (int j = 0; j < values.size(); j++) {
                  svals[j] = values.get(j);
                }
                doc.addField(name, svals);
              } else {
                doc.addField(name, values.get(0));
              }
            }
          }
          if ("provided".equals(genericAuthMode)) {
            if (item.auth != null) {
              String[] acl = new String[item.auth.size()];
              for (int j = 0; j < item.auth.size(); j++) {
                acl[j] = item.auth.get(j);
              }
              doc.setSecurity(RepositoryDocument.SECURITY_TYPE_DOCUMENT,acl,new String[]{defaultAuthorityDenyToken});
            }
          } else {
            if (acls.length > 0) {
              doc.setSecurity(RepositoryDocument.SECURITY_TYPE_DOCUMENT,acls,new String[]{defaultAuthorityDenyToken});
            }
          }
          if (item.content != null) {
            try {
              byte[] content = item.content.getBytes(StandardCharsets.UTF_8);
              ByteArrayInputStream is = new ByteArrayInputStream(content);
              try {
                doc.setBinary(is, content.length);
                activities.ingestDocumentWithException(documentIdentifier, versionString, item.url, doc);
                is.close();
              } finally {
                is.close();
              }
            } catch (IOException ex) {
              handleIOException(ex);
            }
          } else {
            url = new StringBuilder(genericEntryPoint);

            url.append("?").append(ACTION_PARAM_NAME).append("=").append(ACTION_ITEM);
            url.append("&id=").append(URLEncoder.encode(documentIdentifier));
            for (int j = 0; j < spec.getChildCount(); j++) {
              SpecificationNode sn = spec.getChild(j);
              if (sn.getType().equals("param")) {
                String paramName = sn.getAttributeValue("name");
                String paramValue = sn.getValue();
                url.append("&").append(URLEncoder.encode(paramName)).append("=").append(URLEncoder.encode(paramValue));
              }
            }


            ExecuteProcessThread t = new ExecuteProcessThread(client, url.toString());
            try {
              t.start();
              boolean wasInterrupted = false;
              try {
                InputStream is = t.getSafeInputStream();
                long fileLength = t.getStreamLength();
                try {
                  // Can only index while background thread is running!
                  doc.setBinary(is, fileLength);
                  activities.ingestDocumentWithException(documentIdentifier, versionString, item.url, doc);
                } finally {
                  is.close();
                }
              } catch (ManifoldCFException e) {
                if (e.getErrorCode() == ManifoldCFException.INTERRUPTED) {
                  wasInterrupted = true;
                }
                throw e;
              } catch (java.net.SocketTimeoutException e) {
                throw e;
              } catch (InterruptedIOException e) {
                wasInterrupted = true;
                throw e;
              } finally {
                if (!wasInterrupted) {
                  t.finishUp();
                }
              }
            } catch (InterruptedException e) {
              t.interrupt();
              throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (InterruptedIOException e) {
              t.interrupt();
              throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
            } catch (IOException e) {
              handleIOException(e);
            }
          }
        }
      }
      
    } finally {
      for (String documentIdentifier : documentIdentifiers) {
        if (documentCache.containsKey(documentIdentifier)) {
          documentCache.remove(documentIdentifier);
        }
      }
    }
  }

  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale, "generic.EntryPoint"));

    out.print(
      "<script type=\"text/javascript\">\n"
      + "<!--\n"
      + "function checkConfig() {\n"
      + "  return true;\n"
      + "}\n"
      + "\n"
      + "function checkConfigForSave() {\n"
      + "  return true;\n"
      + "}\n"
      + "//-->\n"
      + "</script>\n");
  }

  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException {

    String server = getParam(parameters, "genericEntryPoint", "");
    String login = getParam(parameters, "genericLogin", "");
    String password = "";
    try {
      password = out.mapPasswordToKey(ManifoldCF.deobfuscate(getParam(parameters, "genericPassword", "")));
    } catch (ManifoldCFException ignore) {
    }
    String conTimeout = getParam(parameters, "genericConnectionTimeout", "60000");
    String soTimeout = getParam(parameters, "genericSocketTimeout", "1800000");

    if (tabName.equals(Messages.getString(locale, "generic.EntryPoint"))) {
      out.print(
        "<table class=\"displaytable\">\n"
        + " <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.EntryPointColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericEntryPoint\" value=\"" + Encoder.attributeEscape(server) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.LoginColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericLogin\" value=\"" + Encoder.attributeEscape(login) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.PasswordColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"password\" size=\"32\" name=\"genericPassword\" value=\"" + Encoder.attributeEscape(password) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.ConnectionTimeoutColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericConTimeout\" value=\"" + Encoder.attributeEscape(conTimeout) + "\"/></td>\n"
        + " </tr>\n"
        + " <tr>\n"
        + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.SocketTimeoutColon") + "</nobr></td>\n"
        + "  <td class=\"value\"><input type=\"text\" size=\"32\" name=\"genericSoTimeout\" value=\"" + Encoder.attributeEscape(soTimeout) + "\"/></td>\n"
        + " </tr>\n"
        + "</table>\n");
    } else {
      out.print("<input type=\"hidden\" name=\"genericEntryPoint\" value=\"" + Encoder.attributeEscape(server) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericLogin\" value=\"" + Encoder.attributeEscape(login) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericPassword\" value=\"" + Encoder.attributeEscape(password) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericConTimeout\" value=\"" + Encoder.attributeEscape(conTimeout) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericSoTimeout\" value=\"" + Encoder.attributeEscape(soTimeout) + "\"/>\n");
    }
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException {

    copyParam(variableContext, parameters, "genericLogin");
    copyParam(variableContext, parameters, "genericEntryPoint");
    copyParam(variableContext, parameters, "genericConTimeout");
    copyParam(variableContext, parameters, "genericSoTimeout");

    String password = variableContext.getParameter("genericPassword");
    if (password == null) {
      password = "";
    }
    parameters.setParameter("genericPassword", ManifoldCF.obfuscate(variableContext.mapKeyToPassword(password)));
    return null;
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException {
    String login = getParam(parameters, "genericLogin", "");
    String server = getParam(parameters, "genericEntryPoint", "");
    String conTimeout = getParam(parameters, "genericConnectionTimeout", "60000");
    String soTimeout = getParam(parameters, "genericSocketTimeout", "1800000");
    
    out.print(
      "<table class=\"displaytable\">\n"
      + " <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.EntryPointColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(server) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.LoginColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(login) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.PasswordColon") + "</nobr></td>\n"
      + "  <td class=\"value\">**********</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.ConnectionTimeoutColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(conTimeout) + "</td>\n"
      + " </tr>\n"
      + " <tr>\n"
      + "  <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.SocketTimeoutColon") + "</nobr></td>\n"
      + "  <td class=\"value\">" + Encoder.bodyEscape(soTimeout) + "</td>\n"
      + " </tr>\n"
      + "</table>\n");
  }

  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale, "generic.Parameters"));
    tabsArray.add(Messages.getString(locale, "generic.Security"));

    String seqPrefix = "s"+connectionSequenceNumber+"_";

    out.print(
      "<script type=\"text/javascript\">\n"
      + "<!--\n"
      + "function "+seqPrefix+"SpecOp(n, opValue, anchorvalue) {\n"
      + "  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"
      + "  postFormSetAnchor(anchorvalue);\n"
      + "}\n"
      + "\n"
      + "function "+seqPrefix+"SpecAddToken(anchorvalue) {\n"
      + "  if (editjob."+seqPrefix+"spectoken.value == \"\")\n"
      + "  {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "generic.TypeInAnAccessToken") + "\");\n"
      + "    editjob."+seqPrefix+"spectoken.focus();\n"
      + "    return;\n"
      + "  }\n"
      + "  "+seqPrefix+"SpecOp(\""+seqPrefix+"accessop\",\"Add\",anchorvalue);\n"
      + "}\n"
      + "function "+seqPrefix+"SpecAddParam(anchorvalue) {\n"
      + "  if (editjob."+seqPrefix+"specparamname.value == \"\")\n"
      + "  {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "generic.TypeInParamName") + "\");\n"
      + "    editjob."+seqPrefix+"specparamname.focus();\n"
      + "    return;\n"
      + "  }\n"
      + "  "+seqPrefix+"SpecOp(\""+seqPrefix+"paramop\",\"Add\",anchorvalue);\n"
      + "}\n"
      + "//-->\n"
      + "</script>\n");
  }

  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException {

    String seqPrefix = "s"+connectionSequenceNumber+"_";

    int k, i;

    if (tabName.equals(Messages.getString(locale, "generic.Parameters")) && connectionSequenceNumber == actualSequenceNumber) {

      out.print("<table class=\"displaytable\">"
        + "<tr><td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.ParametersColon") + "</nobr></td>"
        + "<td class=\"value\">");

      out.print("<table class=\"formtable\">\n"
        + "<tr class=\"formheaderrow\">"
        + "<td class=\"formcolumnheader\"></td>"
        + "<td class=\"formcolumnheader\">" + Messages.getBodyString(locale, "generic.ParameterName") + "</td>"
        + "<td class=\"formcolumnheader\">" + Messages.getBodyString(locale, "generic.ParameterValue") + "</td>"
        + "</tr>");

      i = 0;
      k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("param")) {
          String paramDescription = "_" + Integer.toString(k);
          String paramOpName = seqPrefix + "paramop" + paramDescription;
          String paramName = sn.getAttributeValue("name");
          String paramValue = sn.getValue();
          out.print(
            "  <tr class=\"evenformrow\">\n"
            + "    <td class=\"formcolumncell\">\n"
            + "      <input type=\"hidden\" name=\"" + paramOpName + "\" value=\"\"/>\n"
            + "      <a name=\"" + seqPrefix + "param_" + Integer.toString(k) + "\">\n"
            + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "generic.Delete") + "\" onClick='Javascript:SpecOp(\"" + paramOpName + "\",\"Delete\",\"param" + paramDescription + "\")' alt=\"" + Messages.getAttributeString(locale, "generic.DeleteParameter") + Integer.toString(k) + "\"/>\n"
            + "      </a>&nbsp;\n"
            + "    </td>\n"
            + "    <td class=\"formcolumncell\">\n"
            + "      <input type=\"text\" name=\""+seqPrefix+"specparamname" + paramDescription + "\" value=\"" + Encoder.attributeEscape(paramName) + "\"/>\n"
            + "    </td>\n"
            + "    <td class=\"formcolumncell\">\n"
            + "      <input type=\"text\" name=\""+seqPrefix+"specparamvalue" + paramDescription + "\" value=\"" + Encoder.attributeEscape(paramValue) + "\"/>\n"
            + "    </td>\n"
            + "  </tr>\n");
          k++;
        }
      }
      if (k == 0) {
        out.print(
          "  <tr>\n"
          + "    <td class=\"message\" colspan=\"3\">" + Messages.getBodyString(locale, "generic.NoParametersSpecified") + "</td>\n"
          + "  </tr>\n");
      }
      out.print(
        "  <tr><td class=\"lightseparator\" colspan=\"3\"><hr/></td></tr>\n"
        + "  <tr class=\"evenformrow\">\n"
        + "    <td class=\"formcolumncell\">\n"
        + "      <input type=\"hidden\" name=\""+seqPrefix+"paramcount\" value=\"" + Integer.toString(k) + "\"/>\n"
        + "      <input type=\"hidden\" name=\""+seqPrefix+"paramop\" value=\"\"/>\n"
        + "      <a name=\""+seqPrefix+"param_" + Integer.toString(k) + "\">\n"
        + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "generic.Add") + "\" onClick='Javascript:"+seqPrefix+"SpecAddParam(\""+seqPrefix+"param_" + Integer.toString(k + 1) + "\")' alt=\"" + Messages.getAttributeString(locale, "generic.AddParameter") + "\"/>\n"
        + "      </a>&nbsp;\n"
        + "    </td>\n"
        + "    <td class=\"formcolumncell\">\n"
        + "      <input type=\"text\" size=\"30\" name=\""+seqPrefix+"specparamname\" value=\"\"/>\n"
        + "    </td>\n"
        + "    <td class=\"formcolumncell\">\n"
        + "      <input type=\"text\" size=\"30\" name=\""+seqPrefix+"specparamvalue\" value=\"\"/>\n"
        + "    </td>\n"
        + "  </tr>\n"
        + "</table>\n");
      out.print("</td></tr></table>");
    } else {
      i = 0;
      k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("param")) {
          String accessDescription = "_" + Integer.toString(k);
          String paramName = sn.getAttributeValue("name");
          String paramValue = sn.getValue();
          out.print(
            "<input type=\"hidden\" name=\"" + seqPrefix + "specparamname" + accessDescription + "\" value=\"" + Encoder.attributeEscape(paramName) + "\"/>\n"
            + "<input type=\"hidden\" name=\"" + seqPrefix + "specparamvalue" + accessDescription + "\" value=\"" + Encoder.attributeEscape(paramValue) + "\"/>\n");
          k++;
        }
      }
      out.print("<input type=\"hidden\" name=\""+seqPrefix+"paramcount\" value=\"" + Integer.toString(k) + "\"/>\n");
    }

    // Security tab
    String genericAuthMode = "provided";
    for (i = 0; i < ds.getChildCount(); i++) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("genericAuthMode")) {
        genericAuthMode = sn.getValue();
      }
    }
    if (tabName.equals(Messages.getString(locale, "generic.Security")) && connectionSequenceNumber == actualSequenceNumber) {
      out.print(
        "<table class=\"displaytable\">\n"
        + "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n");

      out.print("  <tr>\n"
        + "    <td class=\"description\">" + Messages.getBodyString(locale, "generic.AuthMode") + "</td>\n"
        + "    <td class=\"value\" >\n"
        + "      <input type=\"radio\" name=\""+seqPrefix+"genericAuthMode\" value=\"provided\" " + ("provided".equals(genericAuthMode) ? "checked=\"checked\"" : "") + "/>" + Messages.getBodyString(locale, "generic.AuthModeProvided") + "<br/>\n"
        + "      <input type=\"radio\" name=\""+seqPrefix+"genericAuthMode\" value=\"forced\" " + ("forced".equals(genericAuthMode) ? "checked=\"checked\"" : "") + "/>" + Messages.getBodyString(locale, "generic.AuthModeForced") + "<br/>\n"
        + "    </td>\n"
        + "  </tr>\n");
      // Go through forced ACL
      out.print("<tr><td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.TokensColon") + "</nobr></td>"
        + "<td class=\"value\">");
      out.print("<table class=\"formtable\">\n"
        + "<tr class=\"formheaderrow\">"
        + "<td class=\"formcolumnheader\"></td>"
        + "<td class=\"formcolumnheader\">" + Messages.getBodyString(locale, "generic.Token") + "</td>"
        + "</tr>");
      i = 0;
      k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access")) {
          String accessDescription = "_" + Integer.toString(k);
          String accessOpName = seqPrefix + "accessop" + accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
            "  <tr class=\"evenformrow\">\n"
            + "    <td class=\"formcolumncell\">\n"
            + "      <input type=\"hidden\" name=\"" + accessOpName + "\" value=\"\"/>\n"
            + "      <input type=\"hidden\" name=\"" + seqPrefix + "spectoken" + accessDescription + "\" value=\"" + Encoder.attributeEscape(token) + "\"/>\n"
            + "      <a name=\"" + seqPrefix + "token_" + Integer.toString(k) + "\">\n"
            + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "generic.Delete") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\"" + accessOpName + "\",\"Delete\",\""+seqPrefix+"token_" + Integer.toString(k) + "\")' alt=\"" + Messages.getAttributeString(locale, "generic.DeleteToken") + Integer.toString(k) + "\"/>\n"
            + "      </a>&nbsp;\n"
            + "    </td>\n"
            + "    <td class=\"formcolumncell\">\n"
            + "      " + Encoder.bodyEscape(token) + "\n"
            + "    </td>\n"
            + "  </tr>\n");
          k++;
        }
      }
      if (k == 0) {
        out.print(
          "  <tr>\n"
          + "    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale, "generic.NoAccessTokensSpecified") + "</td>\n"
          + "  </tr>\n");
      }
      out.print(
        "  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"
        + "  <tr class=\"evenformrow\">\n"
        + "    <td class=\"formcolumncell\">\n"
        + "      <input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\"" + Integer.toString(k) + "\"/>\n"
        + "      <input type=\"hidden\" name=\""+seqPrefix+"accessop\" value=\"\"/>\n"
        + "      <a name=\"" + seqPrefix + "token_" + Integer.toString(k) + "\">\n"
        + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "generic.Add") + "\" onClick='Javascript:"+seqPrefix+"SpecAddToken(\""+seqPrefix+"token_" + Integer.toString(k + 1) + "\")' alt=\"" + Messages.getAttributeString(locale, "generic.AddAccessToken") + "\"/>\n"
        + "      </a>&nbsp;\n"
        + "    </td>\n"
        + "    <td class=\"formcolumncell\">\n"
        + "      <input type=\"text\" size=\"30\" name=\""+seqPrefix+"spectoken\" value=\"\"/>\n"
        + "    </td>\n"
        + "  </tr>\n"
        + "</table>\n");
      out.print("</td></tr></table>");
    } else {
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access")) {
          String accessDescription = "_" + Integer.toString(k);
          String token = sn.getAttributeValue("token");
          out.print(
            "<input type=\"hidden\" name=\"" + seqPrefix + "spectoken" + accessDescription + "\" value=\"" + Encoder.attributeEscape(token) + "\"/>\n");
          k++;
        }
      }
      out.print("<input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\"" + Integer.toString(k) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\""+seqPrefix+"genericAuthMode\" value=\"" + Encoder.attributeEscape(genericAuthMode) + "\"/>\n");
    }
  }

  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException {
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    String xc = variableContext.getParameter(seqPrefix+"paramcount");
    if (xc != null) {
      // Delete all tokens first
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("param")) {
          ds.removeChild(i);
        } else {
          i++;
        }
      }

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount) {
        String paramDescription = "_" + Integer.toString(i);
        String paramOpName = seqPrefix + "paramop" + paramDescription;
        xc = variableContext.getParameter(paramOpName);
        if (xc != null && xc.equals("Delete")) {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String paramName = variableContext.getParameter(seqPrefix + "specparamname" + paramDescription);
        String paramValue = variableContext.getParameter(seqPrefix + "specparamvalue" + paramDescription);
        SpecificationNode node = new SpecificationNode("param");
        node.setAttribute("name", paramName);
        node.setValue(paramValue);
        ds.addChild(ds.getChildCount(), node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix+"paramop");
      if (op != null && op.equals("Add")) {
        String paramName = variableContext.getParameter(seqPrefix+"specparamname");
        String paramValue = variableContext.getParameter(seqPrefix+"specparamvalue");
        SpecificationNode node = new SpecificationNode("param");
        node.setAttribute("name", paramName);
        node.setValue(paramValue);
        ds.addChild(ds.getChildCount(), node);
      }
    }

    String redmineAuthMode = variableContext.getParameter(seqPrefix+"genericAuthMode");
    if (redmineAuthMode != null) {
      // Delete existing seeds record first
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("genericAuthMode")) {
          ds.removeChild(i);
        } else {
          i++;
        }
      }
      SpecificationNode cn = new SpecificationNode("genericAuthMode");
      cn.setValue(redmineAuthMode);
      ds.addChild(ds.getChildCount(), cn);
    }

    xc = variableContext.getParameter(seqPrefix+"tokencount");
    if (xc != null) {
      // Delete all tokens first
      int i = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("access")) {
          ds.removeChild(i);
        } else {
          i++;
        }
      }

      int accessCount = Integer.parseInt(xc);
      i = 0;
      while (i < accessCount) {
        String accessDescription = "_" + Integer.toString(i);
        String accessOpName = seqPrefix + "accessop" + accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete")) {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter(seqPrefix + "spectoken" + accessDescription);
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token", accessSpec);
        ds.addChild(ds.getChildCount(), node);
        i++;
      }

      String op = variableContext.getParameter(seqPrefix+"accessop");
      if (op != null && op.equals("Add")) {
        String accessspec = variableContext.getParameter(seqPrefix+"spectoken");
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token", accessspec);
        ds.addChild(ds.getChildCount(), node);
      }
    }

    return null;
  }

  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException {
    boolean seenAny;
    int i;

    i = 0;
    seenAny = false;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("param")) {
        if (seenAny == false) {
          out.print(
            "  <tr>\n"
            + "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.Parameters") + "</nobr></td>\n"
            + "    <td class=\"value\">\n");
          seenAny = true;
        }
        String paramName = sn.getAttributeValue("name");
        String paramValue = sn.getValue();
        out.print(Encoder.bodyEscape(paramName) + " = " + Encoder.bodyEscape(paramValue) + "<br/>\n");
      }
    }

    if (seenAny) {
      out.print(
        "    </td>\n"
        + "  </tr>\n");
    } else {
      out.print(
        "  <tr><td class=\"message\" colspan=\"4\"><nobr>" + Messages.getBodyString(locale, "generic.NoParametersSpecified") + "</nobr></td></tr>\n");
    }

    out.print(
      "  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n");

    // Go through looking for access tokens
    i = 0;
    seenAny = false;
    while (i < ds.getChildCount()) {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("access")) {
        if (seenAny == false) {
          out.print(
            "  <tr>\n"
            + "    <td class=\"description\"><nobr>" + Messages.getBodyString(locale, "generic.AccessTokens") + "</nobr></td>\n"
            + "    <td class=\"value\">\n");
          seenAny = true;
        }
        String token = sn.getAttributeValue("token");
        out.print(Encoder.bodyEscape(token) + "<br/>\n");
      }
    }

    if (seenAny) {
      out.print(
        "    </td>\n"
        + "  </tr>\n");
    } else {
      out.print(
        "  <tr><td class=\"message\" colspan=\"4\"><nobr>" + Messages.getBodyString(locale, "generic.NoAccessTokensSpecified") + "</nobr></td></tr>\n");
    }
    out.print(
      "  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n");
  }

  private String getParam(ConfigParams parameters, String name, String def) {
    return parameters.getParameter(name) != null ? parameters.getParameter(name) : def;
  }

  private boolean copyParam(IPostParameters variableContext, ConfigParams parameters, String name) {
    String val = variableContext.getParameter(name);
    if (val == null) {
      return false;
    }
    parameters.setParameter(name, val);
    return true;
  }

  protected static String[] getAcls(Specification spec) {
    HashMap map = new HashMap();
    int i = 0;
    while (i < spec.getChildCount()) {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals("access")) {
        String token = sn.getAttributeValue("token");
        map.put(token, token);
      }
    }

    String[] rval = new String[map.size()];
    Iterator iter = map.keySet().iterator();
    i = 0;
    while (iter.hasNext()) {
      rval[i++] = (String) iter.next();
    }
    return rval;
  }

  protected static void handleIOException(IOException e)
    throws ManifoldCFException, ServiceInterruption {
    if (!(e instanceof java.net.SocketTimeoutException) && (e instanceof InterruptedIOException)) {
      throw new ManifoldCFException("Interrupted: " + e.getMessage(), e, ManifoldCFException.INTERRUPTED);
    }
    long currentTime = System.currentTimeMillis();
    throw new ServiceInterruption("IO exception: " + e.getMessage(), e, currentTime + 300000L,
      currentTime + 3 * 60 * 60000L, -1, false);
  }

  static class PreemptiveAuth implements HttpRequestInterceptor {

    private Credentials credentials;

    public PreemptiveAuth(Credentials creds) {
      this.credentials = creds;
    }

    @Override
    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
      request.addHeader(new BasicScheme(StandardCharsets.US_ASCII).authenticate(credentials, request, context));
    }
  }

  protected static class CheckThread extends Thread {

    protected HttpClient client;

    protected String url;

    protected Throwable exception = null;

    protected String result = "Unknown";

    public CheckThread(HttpClient client, String url) {
      super();
      setDaemon(true);
      this.client = client;
      this.url = url;
    }

    @Override
    public void run() {
      HttpGet method = new HttpGet(url);
      try {
        HttpResponse response = client.execute(method);
        try {
          if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            result = "Connection failed: " + response.getStatusLine().getReasonPhrase();
            return;
          }
          EntityUtils.consume(response.getEntity());
          result = "Connection OK";
        } finally {
          EntityUtils.consume(response.getEntity());
          method.releaseConnection();
        }
      } catch (IOException ex) {
        exception = ex;
      }
    }

    public Throwable getException() {
      return exception;
    }

    public String getResult() {
      return result;
    }
  }

  protected static class ExecuteSeedingThread extends Thread {

    protected final HttpClient client;

    protected final String url;

    protected final XThreadStringBuffer seedBuffer;

    protected Throwable exception = null;

    public ExecuteSeedingThread(HttpClient client, String url) {
      super();
      setDaemon(true);
      this.client = client;
      this.url = url;
      seedBuffer = new XThreadStringBuffer();
    }

    public XThreadStringBuffer getBuffer() {
      return seedBuffer;
    }

    public void finishUp()
      throws InterruptedException {
      seedBuffer.abandon();
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else if (thr instanceof Error) {
          throw (Error) thr;
        } else {
          throw new RuntimeException("Unhandled exception of type: " + thr.getClass().getName(), thr);
        }
      }
    }

    @Override
    public void run() {
      HttpGet method = new HttpGet(url.toString());

      try {
        HttpResponse response = client.execute(method);
        try {
          if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            exception = new ManifoldCFException("addSeedDocuments error - interface returned incorrect return code for: " + url + " - " + response.getStatusLine().toString());
            return;
          }

          try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            SAXParser parser = factory.newSAXParser();
            DefaultHandler handler = new SAXSeedingHandler(seedBuffer);
            parser.parse(response.getEntity().getContent(), handler);
          } catch (FactoryConfigurationError ex) {
            exception = new ManifoldCFException("addSeedDocuments error: " + ex.getMessage(), ex);
          } catch (ParserConfigurationException ex) {
            exception = new ManifoldCFException("addSeedDocuments error: " + ex.getMessage(), ex);
          } catch (SAXException ex) {
            exception = new ManifoldCFException("addSeedDocuments error: " + ex.getMessage(), ex);
          }
          seedBuffer.signalDone();
        } finally {
          EntityUtils.consume(response.getEntity());
          method.releaseConnection();
        }
      } catch (IOException ex) {
        exception = ex;
      }
    }

    public Throwable getException() {
      return exception;
    }
  }

  protected static class DocumentVersionThread extends Thread {

    protected final HttpClient client;

    protected final String url;

    protected Throwable exception = null;

    protected final String[] versions;

    protected final ConcurrentHashMap<String, Item> documentCache;

    protected final String[] documentIdentifiers;

    protected final String genericAuthMode;

    protected final String defaultRights;

    public DocumentVersionThread(HttpClient client, String url, String[] documentIdentifiers, String genericAuthMode, String defaultRights, ConcurrentHashMap<String, Item> documentCache) {
      super();
      setDaemon(true);
      this.client = client;
      this.url = url;
      this.documentCache = documentCache;
      this.documentIdentifiers = documentIdentifiers;
      this.genericAuthMode = genericAuthMode;
      this.defaultRights = defaultRights;
      this.versions = new String[documentIdentifiers.length];
      for (int i = 0; i < versions.length; i++) {
        versions[i] = null;
      }
    }

    @Override
    public void run() {
      try {
        HttpGet method = new HttpGet(url.toString());

        HttpResponse response = client.execute(method);
        try {
          if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            exception = new ManifoldCFException("addSeedDocuments error - interface returned incorrect return code for: " + url + " - " + response.getStatusLine().toString());
            return;
          }
          JAXBContext context;
          context = JAXBContext.newInstance(Items.class);
          Unmarshaller m = context.createUnmarshaller();
          Items items = (Items) m.unmarshal(response.getEntity().getContent());
          if (items.items != null) {
            for (Item item : items.items) {
              documentCache.put(item.id, item);
              for (int i = 0; i < versions.length; i++) {
                if (documentIdentifiers[i].equals(item.id)) {
                  if ("provided".equals(genericAuthMode)) {
                    versions[i] = item.getVersionString();
                  } else {
                    versions[i] = item.version + defaultRights;
                  }
                  break;
                }
              }
            }
          }
        } catch (JAXBException ex) {
          exception = ex;
        } finally {
          EntityUtils.consume(response.getEntity());
          method.releaseConnection();
        }
      } catch (Exception ex) {
        exception = ex;
      }
    }

    public String[] finishUp()
      throws ManifoldCFException, ServiceInterruption, IOException, InterruptedException {
      join();
      Throwable thr = exception;
      if (thr != null) {
        if (thr instanceof ManifoldCFException) {
          throw (ManifoldCFException) thr;
        } else if (thr instanceof ServiceInterruption) {
          throw (ServiceInterruption) thr;
        } else if (thr instanceof IOException) {
          throw (IOException) thr;
        } else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        } else if (thr instanceof Error) {
          throw (Error) thr;
        }
        throw new ManifoldCFException("getDocumentVersions error: " + thr.getMessage(), thr);
      }
      return versions;
    }
  }

  protected static class ExecuteProcessThread extends Thread {

    protected final HttpClient client;

    protected final String url;

    protected Throwable exception = null;

    protected XThreadInputStream threadStream;

    protected boolean abortThread = false;

    protected long streamLength = 0;

    public ExecuteProcessThread(HttpClient client, String url) {
      super();
      setDaemon(true);
      this.client = client;
      this.url = url;
    }

    @Override
    public void run() {
      try {
        HttpGet method = new HttpGet(url);
        HttpResponse response = client.execute(method);
        try {
          if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            exception = new ManifoldCFException("processDocuments error - interface returned incorrect return code for: " + url + " - " + response.getStatusLine().toString());
            return;
          }
          synchronized (this) {
            if (!abortThread) {
              streamLength = response.getEntity().getContentLength();
              threadStream = new XThreadInputStream(response.getEntity().getContent());
              this.notifyAll();
            }
          }

          if (threadStream != null) {
            // Stuff the content until we are done
            threadStream.stuffQueue();
          }
        } catch (Throwable ex) {
          exception = ex;
        } finally {
          EntityUtils.consume(response.getEntity());
          method.releaseConnection();
        }
      } catch (Throwable e) {
        exception = e;
      }
    }

    public InputStream getSafeInputStream() throws InterruptedException, IOException, ManifoldCFException {
      while (true) {
        synchronized (this) {
          if (exception != null) {
            throw new IllegalStateException("Check for response before getting stream");
          }
          checkException(exception);
          if (threadStream != null) {
            return threadStream;
          }
          wait();
        }
      }
    }

    public long getStreamLength() throws IOException, InterruptedException, ManifoldCFException {
      while (true) {
        synchronized (this) {
          if (exception != null) {
            throw new IllegalStateException("Check for response before getting stream");
          }
          checkException(exception);
          if (threadStream != null) {
            return streamLength;
          }
          wait();
        }
      }
    }

    protected synchronized void checkException(Throwable exception)
      throws IOException, ManifoldCFException {
      if (exception != null) {
        Throwable e = exception;
        if (e instanceof IOException) {
          throw (IOException) e;
        } else if (e instanceof ManifoldCFException) {
          throw (ManifoldCFException) e;
        } else if (e instanceof RuntimeException) {
          throw (RuntimeException) e;
        } else if (e instanceof Error) {
          throw (Error) e;
        } else {
          throw new RuntimeException("Unhandled exception of type: " + e.getClass().getName(), e);
        }
      }
    }

    public void finishUp()
      throws InterruptedException, IOException, ManifoldCFException {
      // This will be called during the finally
      // block in the case where all is well (and
      // the stream completed) and in the case where
      // there were exceptions.
      synchronized (this) {
        if (threadStream != null) {
          threadStream.abort();
        }
        abortThread = true;
      }
      join();
      checkException(exception);
    }

    public Throwable getException() {
      return exception;
    }
  }

  static public class SAXSeedingHandler extends DefaultHandler {

    protected XThreadStringBuffer seedBuffer;

    public SAXSeedingHandler(XThreadStringBuffer seedBuffer) {
      this.seedBuffer = seedBuffer;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if ("seed".equals(localName) && attributes.getValue("id") != null) {
        try {
          seedBuffer.add(attributes.getValue("id"));
        } catch (InterruptedException ex) {
          throw new SAXException("Adding seed failed: " + ex.getMessage(), ex);
        }
      }
    }
  }
}
