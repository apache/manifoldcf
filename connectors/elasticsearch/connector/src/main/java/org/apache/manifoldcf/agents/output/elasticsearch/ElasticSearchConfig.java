/* $Id: ElasticSearchConfig.java 1299512 2012-03-12 00:58:38Z piergiorgio $ */

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

import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IPostParameters;

import java.util.Locale;

public class ElasticSearchConfig extends ElasticSearchParam
{

  /**
	 * 
	 */
  private static final long serialVersionUID = -2071296573398352538L;

  /** Parameters used for the configuration */
  final private static ParameterEnum[] CONFIGURATIONLIST =
  {
//    ParameterEnum.SERVERVERSION,
    ParameterEnum.SERVERLOCATION,
    ParameterEnum.USERNAME,
    ParameterEnum.PASSWORD,
    ParameterEnum.INDEXNAME,
    ParameterEnum.INDEXTYPE,
    ParameterEnum.USEMAPPERATTACHMENTS,
    ParameterEnum.PIPELINENAME,
    ParameterEnum.CONTENTATTRIBUTENAME,
    ParameterEnum.URIATTRIBUTENAME,
    ParameterEnum.CREATEDDATEATTRIBUTENAME,
    ParameterEnum.MODIFIEDDATEATTRIBUTENAME,
    ParameterEnum.INDEXINGDATEATTRIBUTENAME,
    ParameterEnum.MIMETYPEATTRIBUTENAME
  };

  /** Build a set of ElasticSearchParameters by reading ConfigParams. If the
   * value returned by ConfigParams.getParameter is null, the default value is
   * set.
   * 
   * @param paramList
   * @param params */
  public ElasticSearchConfig(ConfigParams params)
  {
    super(CONFIGURATIONLIST);
    for (ParameterEnum param : CONFIGURATIONLIST)
    {
      final boolean isPassword = param.name().endsWith("PASSWORD");
      String value;
      if (isPassword) {
        value = params.getObfuscatedParameter(param.name());
      }
      else
      {
        value = params.getParameter(param.name());
      }
      if (value == null)
      {
        value = param.defaultValue;
      }
      put(param, value);
    }
  }

  /** @return a unique identifier for one index on one ElasticSearch instance. */
  public String getUniqueIndexIdentifier()
  {
    StringBuffer sb = new StringBuffer();
    sb.append(getServerLocation());
    if (sb.charAt(sb.length() - 1) != '/')
      sb.append('/');
    sb.append(getIndexName());
    return sb.toString();
  }

  public final static void contextToConfig(IPostParameters variableContext,
      ConfigParams parameters)
  {
    for (ParameterEnum param : CONFIGURATIONLIST)
    {
      final String paramName = param.name().toLowerCase(Locale.ROOT);
      final boolean isPassword = param.name().endsWith("PASSWORD");
      String p = variableContext.getParameter(paramName);
      if (p != null)
      {
        if (isPassword)
        {
          parameters.setObfuscatedParameter(param.name(), p);
        }
        else
        {
          parameters.setParameter(param.name(), p);
        }
      }
    }

    String useMapperAttachmentsPresent = variableContext.getParameter("usemapperattachments_present");
    if (useMapperAttachmentsPresent != null)
    {
      String useMapperAttachments = variableContext.getParameter(ParameterEnum.USEMAPPERATTACHMENTS.name().toLowerCase(Locale.ROOT));
      if (useMapperAttachments == null || useMapperAttachments.length() == 0)
        useMapperAttachments = "false";
      parameters.setParameter(ParameterEnum.USEMAPPERATTACHMENTS.name(), useMapperAttachments);
    }
  }

  final public String getServerLocation()
  {
    return get(ParameterEnum.SERVERLOCATION);
  }  
  
  final public String getUserName()
  {
    return get(ParameterEnum.USERNAME);
  }
  
  final public String getPassword()
  {
    return get(ParameterEnum.PASSWORD);
  }

  final public String getIndexName()
  {
    return get(ParameterEnum.INDEXNAME);
  }

  final public String getIndexType()
  {
    return get(ParameterEnum.INDEXTYPE);
  }

  final public Boolean getUseMapperAttachments()
  {
    return Boolean.valueOf(get(ParameterEnum.USEMAPPERATTACHMENTS));
  }

  final public String getPipelineName()
  {
    return get(ParameterEnum.PIPELINENAME);
  }
  
  final public String getContentAttributeName()
  {
    return get(ParameterEnum.CONTENTATTRIBUTENAME);
  }

  final public String getUriAttributeName()
  {
    return get(ParameterEnum.URIATTRIBUTENAME);
  }

  final public String getCreatedDateAttributeName()
  {
    return get(ParameterEnum.CREATEDDATEATTRIBUTENAME);
  }

  final public String getModifiedDateAttributeName()
  {
    return get(ParameterEnum.MODIFIEDDATEATTRIBUTENAME);
  }

  final public String getIndexingDateAttributeName()
  {
    return get(ParameterEnum.INDEXINGDATEATTRIBUTENAME);
  }

  final public String getMimeTypeAttributeName()
  {
    return get(ParameterEnum.MIMETYPEATTRIBUTENAME);
  }

}
