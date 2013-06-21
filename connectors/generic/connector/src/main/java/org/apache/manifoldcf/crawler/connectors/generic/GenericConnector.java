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
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.io.FileUtils;
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
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector;
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
    return cl;
  }

  @Override
  public String check() throws ManifoldCFException {
    HttpClient client = getClient();
    HttpGet method = new HttpGet(genericEntryPoint + "?" + ACTION_PARAM_NAME + "=" + ACTION_CHECK);
    try {
      HttpResponse response = client.execute(method);
      try {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
          return "Connection failed: " + response.getStatusLine().getReasonPhrase();
        }
        EntityUtils.consume(response.getEntity());
        return "Connection OK";
      } finally {
        EntityUtils.consume(response.getEntity());
        method.releaseConnection();
      }
    } catch (IOException ex) {
      return "Error: " + ex.getMessage();
    }
  }

  @Override
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime)
    throws ManifoldCFException, ServiceInterruption {

    HttpClient client = getClient();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    try {
      StringBuilder url = new StringBuilder(genericEntryPoint);
      url.append("?").append(ACTION_PARAM_NAME).append("=").append(ACTION_SEED);
      if (startTime > 0) {
        url.append("&startTime=").append(sdf.format(new Date(startTime)));
      }
      url.append("&endTime=").append(sdf.format(new Date(endTime)));
      for (int i = 0; i < spec.getChildCount(); i++) {
        SpecificationNode sn = spec.getChild(i);
        if (sn.getType().equals("param")) {
          try {
            String paramName = sn.getAttributeValue("name");
            String paramValue = sn.getValue();
            url.append("&").append(URLEncoder.encode(paramName, "UTF-8")).append("=").append(URLEncoder.encode(paramValue, "UTF-8"));
          } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(GenericConnector.class.getName()).log(Level.SEVERE, null, ex);
          }
        }
      }
      ExecuteSeedingThread seedingThread = new ExecuteSeedingThread(client, activities, url.toString());
      seedingThread.start();
      seedingThread.join();
      if (seedingThread.getException() != null) {
        Throwable thr = seedingThread.getException();
        if (thr instanceof ManifoldCFException) {
          if (((ManifoldCFException) thr).getErrorCode() == ManifoldCFException.INTERRUPTED) {
            throw new InterruptedException(thr.getMessage());
          }
          throw (ManifoldCFException) thr;
        } else if (thr instanceof ServiceInterruption) {
          throw (ServiceInterruption) thr;
        } else if (thr instanceof IOException) {
          throw (IOException) thr;
        } else if (thr instanceof RuntimeException) {
          throw (RuntimeException) thr;
        }
        throw new ManifoldCFException("addSeedDocuments error: " + thr.getMessage(), thr);
      }
    } catch (InterruptedException ex) {
      throw new ManifoldCFException("addSeedDocuments error: " + ex.getMessage(), ex);
    } catch (UnsupportedEncodingException ex) {
      throw new ManifoldCFException("addSeedDocuments error: " + ex.getMessage(), ex);
    } catch (IOException ex) {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Exception while seeding, retrying: " + ex.getMessage(), ex,
        currentTime + 300000L, //powtarzaj co pięć minut
        currentTime + 60L * 60000L, //powtarzaj przez 1 godzinę
        -1, //bez limitu powtórzeń
        true); //wyrzuć wyjątek jeśli nie uda się pobrać
    }
  }

  @Override
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobType, boolean usesDefaultAuthority)
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
    String[] rval = new String[documentIdentifiers.length];
    try {
      StringBuilder url = new StringBuilder(genericEntryPoint);
      url.append("?").append(ACTION_PARAM_NAME).append("=").append(ACTION_ITEMS);
      for (int i = 0; i < rval.length; i++) {
        url.append("&id[]=").append(URLEncoder.encode(documentIdentifiers[i], "UTF-8"));
        rval[i] = null;
      }
      for (int i = 0; i < spec.getChildCount(); i++) {
        SpecificationNode sn = spec.getChild(i);
        if (sn.getType().equals("param")) {
          String paramName = sn.getAttributeValue("name");
          String paramValue = sn.getValue();
          url.append("&").append(URLEncoder.encode(paramName, "UTF-8")).append("=").append(URLEncoder.encode(paramValue, "UTF-8"));
        }
      }
      HttpGet method = new HttpGet(url.toString());

      HttpResponse response = client.execute(method);
      try {
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
          throw new ManifoldCFException("addSeedDocuments error - interface returned incorrect return code");
        }
        JAXBContext context;
        context = JAXBContext.newInstance(Items.class);
        Unmarshaller m = context.createUnmarshaller();
        Items items = (Items) m.unmarshal(response.getEntity().getContent());
        for (Item item : items.items) {
          documentCache.put(item.id, item);
          for (int i = 0; i < rval.length; i++) {
            if (documentIdentifiers[i].equals(item.id)) {
              if ("provided".equals(genericAuthMode)) {
                rval[i] = item.getVersionString();
              } else {
                rval[i] = item.version + rights;
              }
              break;
            }
          }
        }
      } catch (JAXBException ex) {
        throw new ManifoldCFException("addSeedDocuments error - response is not a valid XML: " + ex.getMessage(), ex);
      } finally {
        EntityUtils.consume(response.getEntity());
        method.releaseConnection();
      }
    } catch (UnsupportedEncodingException ex) {
      throw new ManifoldCFException("getDocumentVersions error - invalid chars in id: " + ex.getMessage(), ex);
    } catch (IOException ex) {
      long currentTime = System.currentTimeMillis();
      throw new ServiceInterruption("Exception while seeding, retrying: " + ex.getMessage(), ex,
        currentTime + 300000L, //powtarzaj co pięć minut
        currentTime + 60L * 60000L, //powtarzaj przez 1 godzinę
        -1, //bez limitu powtórzeń
        true); //wyrzuć wyjątek jeśli nie uda się pobrać
    }
    return rval;
  }

  @Override
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities,
    DocumentSpecification spec, boolean[] scanOnly, int jobType)
    throws ManifoldCFException, ServiceInterruption {

    // Forced acls
    String[] acls = getAcls(spec);

    String genericAuthMode = "provided";
    for (int i = 0; i < spec.getChildCount(); i++) {
      SpecificationNode sn = spec.getChild(i);
      if (sn.getType().equals("genericAuthMode")) {
        genericAuthMode = sn.getValue();
        break;
      }
    }

    HttpClient client = getClient();
    for (int i = 0; i < documentIdentifiers.length; i++) {
      if (scanOnly[i]) {
        continue;
      }
      Item item = documentCache.get(documentIdentifiers[i]);
      if (item == null) {
        throw new ManifoldCFException("processDocuments error - no cache entry for: " + documentIdentifiers[i]);
      }

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
          doc.setACL(acl);
          doc.setDenyACL(new String[]{defaultAuthorityDenyToken});
        }
      } else {
        if (acls.length > 0) {
          doc.setACL(acls);
          doc.setDenyACL(new String[]{defaultAuthorityDenyToken});
        }
      }
      if (item.content != null) {
        try {
          File temp = File.createTempFile("manifold", ".tmp");
          temp.deleteOnExit();
          try {
            FileUtils.writeStringToFile(temp, item.content);
            FileInputStream is = new FileInputStream(temp);
            doc.setBinary(is, temp.length());
            activities.ingestDocument(documentIdentifiers[i], versions[i], item.url, doc);
            is.close();
          } finally {
            temp.delete();
          }
        } catch (IOException ex) {
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Exception while processing " + documentIdentifiers[i] + ", retrying: " + ex.getMessage(), ex,
            currentTime + 300000L, //powtarzaj co pięć minut
            currentTime + 60L * 60000L, //powtarzaj przez 1 godzinę
            -1, //bez limitu powtórzeń
            true); //wyrzuć wyjątek jeśli nie uda się pobrać
        }
      } else {
        try {
          StringBuilder url = new StringBuilder(genericEntryPoint);
          url.append("?").append(ACTION_PARAM_NAME).append("=").append(ACTION_ITEM);
          url.append("&id=").append(URLEncoder.encode(documentIdentifiers[i], "UTF-8"));
          for (int j = 0; j < spec.getChildCount(); j++) {
            SpecificationNode sn = spec.getChild(j);
            if (sn.getType().equals("param")) {
              String paramName = sn.getAttributeValue("name");
              String paramValue = sn.getValue();
              url.append("&").append(URLEncoder.encode(paramName, "UTF-8")).append("=").append(URLEncoder.encode(paramValue, "UTF-8"));
            }
          }
          HttpGet method = new HttpGet(url.toString());
          HttpResponse response = client.execute(method);
          try {
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
              throw new ManifoldCFException("processDocuments error - interface returned incorrect return code for: " + documentIdentifiers[i]);
            }

            doc.setBinary(response.getEntity().getContent(), response.getEntity().getContentLength());
            activities.ingestDocument(documentIdentifiers[i], versions[i], item.url, doc);
          } finally {
            EntityUtils.consume(response.getEntity());
            method.releaseConnection();
          }
        } catch (UnsupportedEncodingException ex) {
          throw new ManifoldCFException("processDocuments error - invalid chars in id: " + ex.getMessage(), ex);
        } catch (IOException ex) {
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Exception while processing " + documentIdentifiers[i] + ", retrying: " + ex.getMessage(), ex,
            currentTime + 300000L, //powtarzaj co pięć minut
            currentTime + 60L * 60000L, //powtarzaj przez 1 godzinę
            -1, //bez limitu powtórzeń
            true); //wyrzuć wyjątek jeśli nie uda się pobrać
        }
      }
    }
  }

  @Override
  public void releaseDocumentVersions(String[] documentIdentifiers, String[] versions) throws ManifoldCFException {
    for (int i = 0; i < documentIdentifiers.length; i++) {
      if (documentCache.containsKey(documentIdentifiers[i])) {
        documentCache.remove(documentIdentifiers[i]);
      }
    }
    super.releaseDocumentVersions(documentIdentifiers, versions);
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
      password = ManifoldCF.deobfuscate(getParam(parameters, "genericPassword", ""));
    } catch (ManifoldCFException ignore) {
    }

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
        + "</table>\n");
    } else {
      out.print("<input type=\"hidden\" name=\"genericEntryPoint\" value=\"" + Encoder.attributeEscape(server) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericLogin\" value=\"" + Encoder.attributeEscape(login) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericPassword\" value=\"" + Encoder.attributeEscape(password) + "\"/>\n");
    }
  }

  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException {

    copyParam(variableContext, parameters, "genericLogin");
    copyParam(variableContext, parameters, "genericEntryPoint");

    String password = variableContext.getParameter("genericPassword");
    if (password == null) {
      password = "";
    }
    parameters.setParameter("genericPassword", ManifoldCF.obfuscate(password));
    return null;
  }

  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException {
    String login = getParam(parameters, "genericLogin", "");
    String server = getParam(parameters, "genericEntryPoint", "");

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
      + "</table>\n");
  }

  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException {
    tabsArray.add(Messages.getString(locale, "generic.Parameters"));
    tabsArray.add(Messages.getString(locale, "generic.Security"));

    out.print(
      "<script type=\"text/javascript\">\n"
      + "<!--\n"
      + "function SpecOp(n, opValue, anchorvalue) {\n"
      + "  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"
      + "  postFormSetAnchor(anchorvalue);\n"
      + "}\n"
      + "\n"
      + "function checkSpecification() {\n"
      + "  return true;\n"
      + "}\n"
      + "\n"
      + "function SpecAddToken(anchorvalue) {\n"
      + "  if (editjob.spectoken.value == \"\")\n"
      + "  {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "generic.TypeInAnAccessToken") + "\");\n"
      + "    editjob.spectoken.focus();\n"
      + "    return;\n"
      + "  }\n"
      + "  SpecOp(\"accessop\",\"Add\",anchorvalue);\n"
      + "}\n"
      + "function SpecAddParam(anchorvalue) {\n"
      + "  if (editjob.specparamname.value == \"\")\n"
      + "  {\n"
      + "    alert(\"" + Messages.getBodyJavascriptString(locale, "generic.TypeInParamName") + "\");\n"
      + "    editjob.specparamname.focus();\n"
      + "    return;\n"
      + "  }\n"
      + "  SpecOp(\"paramop\",\"Add\",anchorvalue);\n"
      + "}\n"
      + "//-->\n"
      + "</script>\n");
  }

  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException {

    int k, i;

    if (tabName.equals(Messages.getString(locale, "generic.Parameters"))) {

      out.print("<table class=\"displaytable\">\n"
        + "<tr>"
        + "<th></th>"
        + "<th>" + Messages.getBodyString(locale, "generic.ParameterName") + "</th>"
        + "<th>" + Messages.getBodyString(locale, "generic.ParameterValue") + "</th>"
        + "</tr>");

      i = 0;
      k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("param")) {
          String paramDescription = "_" + Integer.toString(k);
          String paramOpName = "paramop" + paramDescription;
          String paramName = sn.getAttributeValue("name");
          String paramValue = sn.getValue();
          out.print(
            "  <tr>\n"
            + "    <td class=\"description\">\n"
            + "      <input type=\"hidden\" name=\"" + paramOpName + "\" value=\"\"/>\n"
            + "      <a name=\"" + "param_" + Integer.toString(k) + "\">\n"
            + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "generic.Delete") + "\" onClick='Javascript:SpecOp(\"" + paramOpName + "\",\"Delete\",\"param" + paramDescription + "\")' alt=\"" + Messages.getAttributeString(locale, "generic.DeleteParameter") + Integer.toString(k) + "\"/>\n"
            + "      </a>&nbsp;\n"
            + "    </td>\n"
            + "    <td class=\"value\">\n"
            + "      <input type=\"text\" name=\"specparamname" + paramDescription + "\" value=\"" + Encoder.attributeEscape(paramName) + "\"/>\n"
            + "    </td>\n"
            + "    <td class=\"value\">\n"
            + "      <input type=\"text\" name=\"specparamvalue" + paramDescription + "\" value=\"" + Encoder.attributeEscape(paramValue) + "\"/>\n"
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
        + "  <tr>\n"
        + "    <td class=\"description\">\n"
        + "      <input type=\"hidden\" name=\"paramcount\" value=\"" + Integer.toString(k) + "\"/>\n"
        + "      <input type=\"hidden\" name=\"paramop\" value=\"\"/>\n"
        + "      <a name=\"param_" + Integer.toString(k) + "\">\n"
        + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "generic.Add") + "\" onClick='Javascript:SpecAddParam(\"param_" + Integer.toString(k + 1) + "\")' alt=\"" + Messages.getAttributeString(locale, "generic.AddParameter") + "\"/>\n"
        + "      </a>&nbsp;\n"
        + "    </td>\n"
        + "    <td class=\"value\">\n"
        + "      <input type=\"text\" size=\"30\" name=\"specparamname\" value=\"\"/>\n"
        + "    </td>\n"
        + "    <td class=\"value\">\n"
        + "      <input type=\"text\" size=\"30\" name=\"specparamvalue\" value=\"\"/>\n"
        + "    </td>\n"
        + "  </tr>\n"
        + "</table>\n");
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
            "<input type=\"hidden\" name=\"" + "specparamname" + accessDescription + "\" value=\"" + Encoder.attributeEscape(paramName) + "\"/>\n"
            + "<input type=\"hidden\" name=\"" + "specparamvalue" + accessDescription + "\" value=\"" + Encoder.attributeEscape(paramValue) + "\"/>\n");
          k++;
        }
      }
      out.print("<input type=\"hidden\" name=\"paramcount\" value=\"" + Integer.toString(k) + "\"/>\n");
    }

    // Security tab
    String genericAuthMode = "provided";
    for (i = 0; i < ds.getChildCount(); i++) {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("genericAuthMode")) {
        genericAuthMode = sn.getValue();
      }
    }
    if (tabName.equals(Messages.getString(locale, "generic.Security"))) {
      out.print(
        "<table class=\"displaytable\">\n"
        + "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n");

      out.print("  <tr>\n"
        + "    <td class=\"description\">" + Messages.getBodyString(locale, "generic.AuthMode") + "</td>\n"
        + "    <td class=\"value\" >\n"
        + "      <input type=\"radio\" name=\"genericAuthMode\" value=\"provided\" " + ("provided".equals(genericAuthMode) ? "checked=\"checked\"" : "") + "/>" + Messages.getBodyString(locale, "generic.AuthModeProvided") + "<br/>\n"
        + "      <input type=\"radio\" name=\"genericAuthMode\" value=\"forced\" " + ("forced".equals(genericAuthMode) ? "checked=\"checked\"" : "") + "/>" + Messages.getBodyString(locale, "generic.AuthModeForced") + "<br/>\n"
        + "    </td>\n"
        + "  </tr>\n");
      // Go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access")) {
          String accessDescription = "_" + Integer.toString(k);
          String accessOpName = "accessop" + accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
            "  <tr>\n"
            + "    <td class=\"description\">\n"
            + "      <input type=\"hidden\" name=\"" + accessOpName + "\" value=\"\"/>\n"
            + "      <input type=\"hidden\" name=\"" + "spectoken" + accessDescription + "\" value=\"" + Encoder.attributeEscape(token) + "\"/>\n"
            + "      <a name=\"" + "token_" + Integer.toString(k) + "\">\n"
            + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "generic.Delete") + "\" onClick='Javascript:SpecOp(\"" + accessOpName + "\",\"Delete\",\"token_" + Integer.toString(k) + "\")' alt=\"" + Messages.getAttributeString(locale, "generic.DeleteToken") + Integer.toString(k) + "\"/>\n"
            + "      </a>&nbsp;\n"
            + "    </td>\n"
            + "    <td class=\"value\">\n"
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
        + "  <tr>\n"
        + "    <td class=\"description\">\n"
        + "      <input type=\"hidden\" name=\"tokencount\" value=\"" + Integer.toString(k) + "\"/>\n"
        + "      <input type=\"hidden\" name=\"accessop\" value=\"\"/>\n"
        + "      <a name=\"" + "token_" + Integer.toString(k) + "\">\n"
        + "        <input type=\"button\" value=\"" + Messages.getAttributeString(locale, "generic.Add") + "\" onClick='Javascript:SpecAddToken(\"token_" + Integer.toString(k + 1) + "\")' alt=\"" + Messages.getAttributeString(locale, "generic.AddAccessToken") + "\"/>\n"
        + "      </a>&nbsp;\n"
        + "    </td>\n"
        + "    <td class=\"value\">\n"
        + "      <input type=\"text\" size=\"30\" name=\"spectoken\" value=\"\"/>\n"
        + "    </td>\n"
        + "  </tr>\n"
        + "</table>\n");
    } else {
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount()) {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access")) {
          String accessDescription = "_" + Integer.toString(k);
          String token = "" + sn.getAttributeValue("token");
          out.print(
            "<input type=\"hidden\" name=\"" + "spectoken" + accessDescription + "\" value=\"" + Encoder.attributeEscape(token) + "\"/>\n");
          k++;
        }
      }
      out.print("<input type=\"hidden\" name=\"tokencount\" value=\"" + Integer.toString(k) + "\"/>\n");
      out.print("<input type=\"hidden\" name=\"genericAuthMode\" value=\"" + Encoder.attributeEscape(genericAuthMode) + "\"/>\n");
    }
  }

  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException {

    String xc = variableContext.getParameter("paramcount");
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
        String paramOpName = "paramop" + paramDescription;
        xc = variableContext.getParameter(paramOpName);
        if (xc != null && xc.equals("Delete")) {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String paramName = variableContext.getParameter("specparamname" + paramDescription);
        String paramValue = variableContext.getParameter("specparamvalue" + paramDescription);
        SpecificationNode node = new SpecificationNode("param");
        node.setAttribute("name", paramName);
        node.setValue(paramValue);
        ds.addChild(ds.getChildCount(), node);
        i++;
      }

      String op = variableContext.getParameter("paramop");
      if (op != null && op.equals("Add")) {
        String paramName = variableContext.getParameter("specparamname");
        String paramValue = variableContext.getParameter("specparamvalue");
        SpecificationNode node = new SpecificationNode("param");
        node.setAttribute("name", paramName);
        node.setValue(paramValue);
        ds.addChild(ds.getChildCount(), node);
      }
    }

    String redmineAuthMode = variableContext.getParameter("genericAuthMode");
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

    xc = variableContext.getParameter("tokencount");
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
        String accessOpName = "accessop" + accessDescription;
        xc = variableContext.getParameter(accessOpName);
        if (xc != null && xc.equals("Delete")) {
          // Next row
          i++;
          continue;
        }
        // Get the stuff we need
        String accessSpec = variableContext.getParameter("spectoken" + accessDescription);
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token", accessSpec);
        ds.addChild(ds.getChildCount(), node);
        i++;
      }

      String op = variableContext.getParameter("accessop");
      if (op != null && op.equals("Add")) {
        String accessspec = variableContext.getParameter("spectoken");
        SpecificationNode node = new SpecificationNode("access");
        node.setAttribute("token", accessspec);
        ds.addChild(ds.getChildCount(), node);
      }
    }

    return null;
  }

  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
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

  protected static String[] getAcls(DocumentSpecification spec) {
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

  static class PreemptiveAuth implements HttpRequestInterceptor {

    private Credentials credentials;

    public PreemptiveAuth(Credentials creds) {
      this.credentials = creds;
    }

    @Override
    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
      request.addHeader(BasicScheme.authenticate(credentials, "US-ASCII", false));
    }
  }

  protected static class ExecuteSeedingThread extends Thread {

    protected HttpClient client;

    protected String url;

    protected ISeedingActivity activities;

    protected Throwable exception = null;

    public ExecuteSeedingThread(HttpClient client, ISeedingActivity activities, String url) {
      super();
      setDaemon(true);
      this.client = client;
      this.url = url;
      this.activities = activities;
    }

    @Override
    public void run() {
      HttpGet method = new HttpGet(url.toString());

      try {
        HttpResponse response = client.execute(method);
        try {
          if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            exception = new ManifoldCFException("addSeedDocuments error - interface returned incorrect return code");
            return;
          }

          try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            SAXParser parser = factory.newSAXParser();
            DefaultHandler handler = new SAXSeedingHandler(activities);
            parser.parse(response.getEntity().getContent(), handler);
          } catch (FactoryConfigurationError ex) {
            exception = new ManifoldCFException("addSeedDocuments error: " + ex.getMessage(), ex);
          } catch (ParserConfigurationException ex) {
            exception = new ManifoldCFException("addSeedDocuments error: " + ex.getMessage(), ex);
          } catch (SAXException ex) {
            exception = new ManifoldCFException("addSeedDocuments error: " + ex.getMessage(), ex);
          }
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

  static public class SAXSeedingHandler extends DefaultHandler {

    protected ISeedingActivity activities;

    public SAXSeedingHandler(ISeedingActivity activities) {
      this.activities = activities;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if ("seed".equals(localName) && attributes.getValue("id") != null) {
        try {
          activities.addSeedDocument(attributes.getValue("id"));
        } catch (ManifoldCFException ex) {
          throw new SAXException("Adding seed failed: " + ex.getMessage(), ex);
        }
      }
    }
  }

  @XmlRootElement(name = "meta")
  public static class Meta {

    @XmlAttribute(name = "name")
    String name;

    @XmlValue
    String value;
  }

  @XmlRootElement(name = "item")
  public static class Item {

    @XmlAttribute(name = "id", required = true)
    String id;

    @XmlElement(name = "url", required = true)
    String url;

    @XmlElement(name = "version", required = true)
    String version;

    @XmlElement(name = "content")
    String content;

    @XmlElement(name = "mimetype")
    String mimeType;

    @XmlElement(name = "created")
    @XmlJavaTypeAdapter(DateAdapter.class)
    Date created;

    @XmlElement(name = "updated")
    @XmlJavaTypeAdapter(DateAdapter.class)
    Date updated;

    @XmlElement(name = "filename")
    String fileName;

    @XmlElementWrapper(name = "metadata")
    @XmlElements({
      @XmlElement(name = "meta", type = Meta.class)})
    List<Meta> metadata;

    @XmlElementWrapper(name = "auth")
    @XmlElements({
      @XmlElement(name = "token", type = String.class)})
    List<String> auth;

    public String getVersionString() {
      if (version == null) {
        return "";
      }
      StringBuilder sb = new StringBuilder(version);
      if (auth != null) {
        for (String t : auth) {
          sb.append("|").append(t);
        }
      }
      return sb.toString();
    }
  }

  @XmlRootElement(name = "items")
  public static class Items {

    @XmlElements({
      @XmlElement(name = "item", type = Item.class)})
    List<Item> items;
  }
}
