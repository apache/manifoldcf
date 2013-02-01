/* $Id: ElasticSearchSpecs.java 1299512 2012-03-12 00:58:38Z piergiorgio $ */

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

package org.apache.manifoldcf.agents.output.elasticsearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.json.JSONException;
import org.json.JSONObject;

public class ElasticSearchSpecs extends ElasticSearchParam
{

  /**
	 * 
	 */
  private static final long serialVersionUID = 1859653440572662025L;

  final public static ParameterEnum[] SPECIFICATIONLIST =
  { ParameterEnum.MAXFILESIZE, ParameterEnum.MIMETYPES,
      ParameterEnum.EXTENSIONS };

  final public static String ELASTICSEARCH_SPECS_NODE = "ELASTICSEARCH_SPECS_NODE";

  private Set<String> extensionSet;

  private Set<String> mimeTypeSet;

  /** Build a set of ElasticSearch parameters by reading an JSON object
   * 
   * @param json
   * @throws JSONException
   * @throws ManifoldCFException */
  public ElasticSearchSpecs(JSONObject json) throws JSONException,
      ManifoldCFException
  {
    super(SPECIFICATIONLIST);
    extensionSet = null;
    mimeTypeSet = null;
    for (ParameterEnum param : SPECIFICATIONLIST)
    {
      String value = null;
      value = json.getString(param.name());
      if (value == null)
        value = param.defaultValue;
      put(param, value);
    }
    extensionSet = createStringSet(getExtensions());
    mimeTypeSet = createStringSet(getMimeTypes());
  }

  /** Build a set of ElasticSearch parameters by reading an instance of
   * SpecificationNode.
   * 
   * @param node
   * @throws ManifoldCFException */
  public ElasticSearchSpecs(ConfigurationNode node) throws ManifoldCFException
  {
    super(SPECIFICATIONLIST);
    for (ParameterEnum param : SPECIFICATIONLIST)
    {
      String value = null;
      if (node != null)
        value = node.getAttributeValue(param.name());
      if (value == null)
        value = param.defaultValue;
      put(param, value);
    }
    extensionSet = createStringSet(getExtensions());
    mimeTypeSet = createStringSet(getMimeTypes());
  }

  public static void contextToSpecNode(IPostParameters variableContext,
      ConfigurationNode specNode)
  {
    for (ParameterEnum param : SPECIFICATIONLIST)
    {
      String p = variableContext.getParameter(param.name().toLowerCase());
      if (p != null)
        specNode.setAttribute(param.name(), p);
    }
  }

  /** @return a JSON representation of the parameter list */
  public JSONObject toJson()
  {
    return new JSONObject(this);
  }

  public long getMaxFileSize()
  {
    return Long.parseLong(get(ParameterEnum.MAXFILESIZE));
  }

  public String getMimeTypes()
  {
    return get(ParameterEnum.MIMETYPES);
  }

  public String getExtensions()
  {
    return get(ParameterEnum.EXTENSIONS);
  }

  private final static TreeSet<String> createStringSet(String content)
      throws ManifoldCFException
  {
    TreeSet<String> set = new TreeSet<String>();
    BufferedReader br = null;
    StringReader sr = null;
    try
    {
      sr = new StringReader(content);
      br = new BufferedReader(sr);
      String line = null;
      while ((line = br.readLine()) != null)
      {
        line = line.trim();
        if (line.length() > 0)
          set.add(line);
      }
      return set;
    } catch (IOException e)
    {
      throw new ManifoldCFException(e);
    } finally
    {
      if (br != null)
        IOUtils.closeQuietly(br);
    }
  }

  public boolean checkExtension(String extension)
  {
    if (extension == null)
      extension = "";
    return extensionSet.contains(extension);
  }

  public boolean checkMimeType(String mimeType)
  {
    if (mimeType == null)
      mimeType = "application/unknown";
    return mimeTypeSet.contains(mimeType);
  }
}
