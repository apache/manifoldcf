package org.apache.manifoldcf.crawler.connectors.alfresco;

import java.util.ArrayList;
import java.util.List;

import org.alfresco.webservice.types.NamedValue;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

public class PropertiesUtils {

  private static final String PROP_CONTENT_PREFIX = "ContentData";
  
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
                if(property.getValue().startsWith(PROP_CONTENT_PREFIX)){
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
  
}
