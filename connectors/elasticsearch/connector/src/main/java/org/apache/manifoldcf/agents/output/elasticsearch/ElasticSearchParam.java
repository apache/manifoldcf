/* $Id: ElasticSearchParam.java 1299512 2012-03-12 00:58:38Z piergiorgio $ */

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

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.connectorcommon.interfaces.IKeystoreManager;
import org.apache.manifoldcf.connectorcommon.interfaces.KeystoreManagerFactory;

import org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchParam.ParameterEnum;

/** 
 * Parameters data for the elasticsearch output connector.
*/
public class ElasticSearchParam extends HashMap<ParameterEnum, String>
{

  /** Parameters constants */
  public enum ParameterEnum
  {
    SERVERLOCATION("http://localhost:9200/"),
    INDEXNAME("index"),
    USERNAME(""),
    PASSWORD(""),
    SERVERKEYSTORE(""),
    INDEXTYPE("_doc"),
    USEINGESTATTACHMENT("false"),
    USEMAPPERATTACHMENTS("false"),
    PIPELINENAME(""),
    CONTENTATTRIBUTENAME("content"),
    URIATTRIBUTENAME("url"),
    CREATEDDATEATTRIBUTENAME("created"),
    MODIFIEDDATEATTRIBUTENAME("last-modified"),
    INDEXINGDATEATTRIBUTENAME("indexed"),
    MIMETYPEATTRIBUTENAME("mime-type"),
    
    FIELDLIST(""),
    ELASTICSEARCH_SOCKET_TIMEOUT("900000"),
    ELASTICSEARCH_CONNECTION_TIMEOUT("60000");

    final protected String defaultValue;

    private ParameterEnum(String defaultValue)
    {
      this.defaultValue = defaultValue;
    }
  }

  private static final long serialVersionUID = -1593234685772720029L;

  protected ElasticSearchParam(ParameterEnum[] params)
  {
    super(params.length);
  }

  final public Map<String, Object> buildMap(IHTTPOutput out) throws ManifoldCFException
  {
    Map<String, Object> rval = new HashMap<>();
    for (Map.Entry<ParameterEnum, String> entry : this.entrySet())
    {
      final String key = entry.getKey().name();
      final boolean isPassword = key.endsWith("PASSWORD");
      final boolean isKeystore = key.endsWith("KEYSTORE");
      if (isPassword) 
      {
        // Do not put passwords in plain text in forms
        rval.put(key, out.mapPasswordToKey(entry.getValue()));
      }
      else if (isKeystore)
      {
        String keystore = entry.getValue();
        IKeystoreManager localKeystore;
        if (keystore == null || keystore.length() == 0)
          localKeystore = KeystoreManagerFactory.make("");
        else
          localKeystore = KeystoreManagerFactory.make("",keystore);

        List<Map<String,String>> certificates = new ArrayList<Map<String,String>>();
        
        String[] contents = localKeystore.getContents();
        for (String alias : contents)
        {
          String description = localKeystore.getDescription(alias);
          if (description.length() > 128)
            description = description.substring(0,125) + "...";
          Map<String,String> certificate = new HashMap<String,String>();
          certificate.put("ALIAS", alias);
          certificate.put("DESCRIPTION", description);
          certificates.add(certificate);
        }
        rval.put(key, keystore);
        rval.put(key + "_LIST", certificates);
      }
      else
      {
        rval.put(key, entry.getValue());
      }
    }
    return rval;
  }

}
