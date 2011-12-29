/* $Id$ */

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
package org.apache.manifoldcf.ui.i18n;

import java.util.Map;
import java.util.Iterator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;

import org.apache.manifoldcf.ui.util.Encoder;

public class Messages
{
  /** Write a resource to HTTP output, specifying what to substitute, and more importantly, how.
  * ${PARAM_NAME} will be substituted directly with the value.
  * ${PARAM_NAME:A} will be substituted with HTML attribute-escaped value.
  * ${PARAM_NAME:B} will be substituted with HTML body-escaped value.
  * ${PARAM_NAME:AJ} will be substituted with HTML attribute + Javascript escaped value.
  * ${PARAM_NAME:BJ} will be substituted with HTML body + Javascript escaped value.
  */
  public static void outputResource(IHTTPOutput output, Class classInstance,
    String resourceKey, Map<String,String> substitutionParameters, boolean mapToUpperCase)
    throws ManifoldCFException
  {
    try
    {
      InputStream is = classInstance.getResourceAsStream(resourceKey);
      if (is == null)
        throw new ManifoldCFException("Missing resource '"+resourceKey+"'");
      try
      {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null)
        {
          if (substitutionParameters != null)
          {
            Iterator<String> i = substitutionParameters.keySet().iterator();
            boolean parsedLine = false;
            while(i.hasNext())
            {
              String key = i.next();
              String value = substitutionParameters.get(key);
              if (mapToUpperCase)
                key = key.toUpperCase();
              if (value == null)
                value = "";
              //System.out.println("Processed key = '"+key+"', processed value = '"+value+"'");
              // We replace 4x, with 4 different replacement strings
              line = doReplace(line,"${"+key+"}",value);
              line = doReplace(line,"${"+key+":A}",Encoder.attributeEscape(value));
              line = doReplace(line,"${"+key+":B}",Encoder.bodyEscape(value));
              line = doReplace(line,"${"+key+":AJ}",Encoder.attributeJavascriptEscape(value));
              line = doReplace(line,"${"+key+":BJ}",Encoder.bodyJavascriptEscape(value));
            }
          }
          output.println(line);
        }
      }
      finally
      {
        is.close();
      }
    }
    catch (IOException e)
    {
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  private static String doReplace(String line, String key, String value)
  {
    if (line.indexOf(key) == -1)
    {
      return line;
    }
    StringBuilder sb = new StringBuilder();
    int index = 0;
    while (true)
    {
      int newIndex = line.indexOf(key,index);
      if (newIndex == -1)
      {
        sb.append(line.substring(index));
        break;
      }
      sb.append(line.substring(index,newIndex)).append(value);
      index = newIndex + key.length();
    }
    return sb.toString();
  }
  

}
