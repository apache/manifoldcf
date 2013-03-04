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
package org.apache.manifoldcf.crawler.connectors.alfresco;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.alfresco.webservice.types.NamedValue;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

/**
 * Utility class dedicatd to manage Alfresco properties
 * @author Piergiorgio Lucidi
 *
 */
public class PropertiesUtils {

  private static final String PROP_CONTENT_PREFIX_1 = "contentUrl";
  private static final String PROP_CONTENT_PREFIX_2 = "ContentData";
  private static final String PROP_CONTENT_SEP = "|";
  private static final String PROP_MIMETYPE_SEP = "=";
  
  public static String[] getPropertyValues(NamedValue[]  properties, String qname){
    String[] propertyValues = null;
    for(NamedValue property : properties){
      if(property.getName().endsWith(qname)){
        if(property.getIsMultiValue()){
          propertyValues = property.getValues();
        } else {
          propertyValues = new String[]{property.getValue()};
        }
      }
    }
    return propertyValues;
  }
  
  public static void ingestProperties(RepositoryDocument rd, NamedValue[] properties) throws ManifoldCFException{
    for(NamedValue property : properties){
      if(property.getIsMultiValue()){
        String[] values = property.getValues();
        if(values!=null){
          for (String value : values) {
            rd.addField(property.getName(), value);
          }
        }
      } else {
        rd.addField(property.getName(), property.getValue());
      }
    }
  }
  
  /**
   * 
   * @param properties
   * @return a list of binary properties for the current node
   */
  public static List<NamedValue> getContentProperties(NamedValue[] properties){
    List<NamedValue> contentProperties = new ArrayList<NamedValue>();
    if(properties!=null){
      for (NamedValue property : properties) {
        if(property!=null){
          if(property.getIsMultiValue()!=null){
            if(!property.getIsMultiValue()){
              if(StringUtils.isNotEmpty(property.getValue())){
                if(property.getValue().startsWith(PROP_CONTENT_PREFIX_1)
                    || property.getValue().startsWith(PROP_CONTENT_PREFIX_2)){
                    contentProperties.add(property);
                }
              }
            }
          }
        }
      }
    }
    return contentProperties;
  }
  
  /**
   * Build the Alfresco node identifier
   * @param properties
   * @return the node reference for the current document
   */
  public static String getNodeReference(NamedValue[] properties){
    String nodeReference = StringUtils.EMPTY;
    String storeProtocol = StringUtils.EMPTY;
    String storeId = StringUtils.EMPTY;
    String uuid = StringUtils.EMPTY;
    if(properties!=null){
      for (NamedValue property : properties) {
        if(Constants.PROP_STORE_PROTOCOL.equals(property.getName())){
          storeProtocol = property.getValue();
        } else if(Constants.PROP_STORE_ID.equals(property.getName())){
          storeId = property.getValue();
        } else if(Constants.PROP_NODE_UUID.equals(property.getName())){
          uuid = property.getValue();
        }
      }
    }
    if(StringUtils.isNotEmpty(storeProtocol)
        && StringUtils.isNotEmpty(storeId)
        && StringUtils.isNotEmpty(uuid)) {
      nodeReference = storeProtocol+"://"+storeId+"/"+uuid;
    }
    return nodeReference;
  }
  
  /**
   * 
   * @param properties
   * @return version label of the latest version of the node
   */
  public static String getVersionLabel(NamedValue[] properties){
    String[] versionLabelList = PropertiesUtils.getPropertyValues(properties, Constants.PROP_VERSION_LABEL);
    String versionLabel = StringUtils.EMPTY;
    if(versionLabelList!=null && versionLabelList.length>0){
      versionLabel = versionLabelList[0];
    }
    return versionLabel;
  }
  
  /**
   * This method returns the mimetype of the default content defined for the node.
   * Notice that more than one binary can be defined in a custom model of Alfresco and also that 
   * it could exist some contents that don't have a binary
   * @param contentProperties
   * @return mimetype of the default content property
   */
  public static String getMimeType(List<NamedValue> contentProperties){
    if(contentProperties!=null && contentProperties.size()>0){
      Iterator<NamedValue> i = contentProperties.iterator();
      while(i.hasNext()){
        NamedValue contentProperty = i.next();
        if(Constants.PROP_CONTENT.equals(contentProperty.getName())){
          String defaultContentPropertyValue = contentProperty.getValue();
          String[] contentSplitted = StringUtils.split(defaultContentPropertyValue, PROP_CONTENT_SEP);
          String[] mimeTypeSplitted = StringUtils.split(contentSplitted[1], PROP_MIMETYPE_SEP);
          return mimeTypeSplitted[1];
        }
      }
    }
    return StringUtils.EMPTY;
  }
  
}
