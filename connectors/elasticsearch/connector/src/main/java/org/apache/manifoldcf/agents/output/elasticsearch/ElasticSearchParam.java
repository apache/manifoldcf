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

import org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchParam.ParameterEnum;

/** 
 * Parameters data for the elasticsearch output connector.
*/
public class ElasticSearchParam extends HashMap<ParameterEnum, String>
{

  /** Parameters constants */
  public enum ParameterEnum
  {
    SERVERVERSION(""),
    
    SERVERLOCATION("http://localhost:9200/"),

    INDEXNAME("index"),

    INDEXTYPE("generictype"),

    USEMAPPERATTACHMENTS("true"),
    
    PIPELINENAME(""),

    CONTENTATTRIBUTENAME(""),

    CREATEDDATEATTRIBUTENAME(""),
    
    MODIFIEDDATEATTRIBUTENAME(""),
    
    INDEXINGDATEATTRIBUTENAME(""),
    
    MIMETYPEATTRIBUTENAME(""),
    
    FIELDLIST("");

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

  final public Map<String, String> buildMap()
  {
    Map<String, String> rval = new HashMap<String, String>();
    for (Map.Entry<ParameterEnum, String> entry : this.entrySet())
      rval.put(entry.getKey().name(), entry.getValue());
    return rval;
  }

}
