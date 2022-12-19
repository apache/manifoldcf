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

import org.apache.manifoldcf.core.interfaces.IDFactory;
import org.apache.manifoldcf.connectorcommon.interfaces.IKeystoreManager;
import org.apache.manifoldcf.connectorcommon.interfaces.KeystoreManagerFactory;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

import java.util.Locale;
import java.io.IOException;

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
    ParameterEnum.SERVERKEYSTORE,
    ParameterEnum.INDEXNAME,
    ParameterEnum.INDEXTYPE,
    ParameterEnum.USEINGESTATTACHMENT,
    ParameterEnum.USEMAPPERATTACHMENTS,
    ParameterEnum.PIPELINENAME,
    ParameterEnum.CONTENTATTRIBUTENAME,
    ParameterEnum.URIATTRIBUTENAME,
    ParameterEnum.CREATEDDATEATTRIBUTENAME,
    ParameterEnum.MODIFIEDDATEATTRIBUTENAME,
    ParameterEnum.INDEXINGDATEATTRIBUTENAME,
    ParameterEnum.MIMETYPEATTRIBUTENAME,
    ParameterEnum.ELASTICSEARCH_SOCKET_TIMEOUT,
    ParameterEnum.ELASTICSEARCH_CONNECTION_TIMEOUT
  };

  /** Build a set of ElasticSearchParameters by reading ConfigParams. If the
   * value returned by ConfigParams.getParameter is null, the default value is
   * set.
   * 
   * @param params */
  public ElasticSearchConfig(ConfigParams params)
  {
    super(CONFIGURATIONLIST);
    for (ParameterEnum param : CONFIGURATIONLIST)
    {
      final boolean isPassword = param.name().endsWith("PASSWORD");
      // Nothing special is needed for keystores here; the keystore in string format is the value
      // of the field
      //final boolean isKeystore = param.name().endsWith("KEYSTORE");
      String value;
      if (isPassword)
      {
        put(param, params.getObfuscatedParameter(param.name()), param.defaultValue);
      }
      else
      {
        put(param, params.getParameter(param.name()), param.defaultValue);
      }
    }
  }

  private void put(ParameterEnum param, String value, String defaultValue)
  {
    if (value == null) {
      put(param, defaultValue);
    } else {
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

  public final static String contextToConfig(IThreadContext threadContext,
      IPostParameters variableContext,
      ConfigParams parameters) throws ManifoldCFException
  {
    String rval = null;
    for (ParameterEnum param : CONFIGURATIONLIST)
    {
      final String paramName = param.name().toLowerCase(Locale.ROOT);
      final boolean isPassword = param.name().endsWith("PASSWORD");
      final boolean isKeystore = param.name().endsWith("KEYSTORE");
      if (isKeystore)
      {
        String keystoreValue = variableContext.getParameter(paramName); //parameters.getParameter(SharePointConfig.PARAM_SERVERKEYSTORE);
        IKeystoreManager mgr;
        if (keystoreValue != null && keystoreValue.length() > 0)
          mgr = KeystoreManagerFactory.make("",keystoreValue);
        else
          mgr = KeystoreManagerFactory.make("");

        // All the functionality needed to gather keystore-related variables must go here.
        // Specifically, we need to also handle add/delete signals from the UI as well as
        // preserving the original keystore and making sure it gets gathered if no other signal
        // is present.
        String configOp = variableContext.getParameter(paramName + "_op");
        if (configOp != null)
        {
          if (configOp.equals("Delete"))
          {
            String alias = variableContext.getParameter(paramName + "_alias");
            mgr.remove(alias);
          }
          else if (configOp.equals("Add"))
          {
            String alias = IDFactory.make(threadContext);
            byte[] certificateValue = variableContext.getBinaryBytes(paramName + "_certificate");
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
                // Don't report anything
              }
            }

            if (certError != null)
            {
              // Redirect to error page
              rval = "Illegal certificate: "+certError;
            }
          }
        }
        parameters.setParameter(param.name(), mgr.getString());
      }
      else
      {
        String p = variableContext.getParameter(paramName);
        if (p != null)
        {
          if (isPassword)
          {
            parameters.setObfuscatedParameter(param.name(), variableContext.mapKeyToPassword(p));
          }
          else
          {
            parameters.setParameter(param.name(), p);
          }
        }
      }
    }

    String useIngestAttachmentPresent = variableContext.getParameter("useingestattachment_present");
    if (useIngestAttachmentPresent != null)
    {
      String useIngestAttachment = variableContext.getParameter(ParameterEnum.USEINGESTATTACHMENT.name().toLowerCase(Locale.ROOT));
      if (useIngestAttachment == null || useIngestAttachment.length() == 0)
        useIngestAttachment = "false";
      parameters.setParameter(ParameterEnum.USEINGESTATTACHMENT.name(), useIngestAttachment);
    }

    String useMapperAttachmentsPresent = variableContext.getParameter("usemapperattachments_present");
    if (useMapperAttachmentsPresent != null)
    {
      String useMapperAttachments = variableContext.getParameter(ParameterEnum.USEMAPPERATTACHMENTS.name().toLowerCase(Locale.ROOT));
      if (useMapperAttachments == null || useMapperAttachments.length() == 0)
        useMapperAttachments = "false";
      parameters.setParameter(ParameterEnum.USEMAPPERATTACHMENTS.name(), useMapperAttachments);
    }

    return rval;
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

  final public IKeystoreManager getSSLKeystore()
    throws ManifoldCFException
  {
    final String packedKeystore = get(ParameterEnum.SERVERKEYSTORE);
    if (packedKeystore == null || packedKeystore.length() == 0)
    {
      return null;
    }
    return KeystoreManagerFactory.make("", packedKeystore);
  }
  
  final public String getElasticSearchSocketTimeout()
  {
    return get(ParameterEnum.ELASTICSEARCH_SOCKET_TIMEOUT);
  }

  final public String getElasticSearchConnectionTimeout()
  {
    return get(ParameterEnum.ELASTICSEARCH_CONNECTION_TIMEOUT);
  }  

  final public String getIndexName()
  {
    return get(ParameterEnum.INDEXNAME);
  }

  final public String getIndexType()
  {
    return get(ParameterEnum.INDEXTYPE);
  }

  final public Boolean getUseIngestAttachment()
  {
    return Boolean.valueOf(get(ParameterEnum.USEINGESTATTACHMENT));
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
