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
package org.apache.manifoldcf.core.interfaces;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.*;

/** This tester exercises the configuration hierarchy, and assures that the conversion
* to/from XML and JSON works properly.
*/
public class ConfigurationTest {
  
  private final static String testData = "{\"job\":{\"_children_\":[{\"_type_\":\"expiration_interval\",\"_value_\":\"infinite\"},{\"_type_\":\"hopcount_mode\",\"_value_\":\"accurate\"},{\"_type_\":\"document_specification\",\"_children_\":[{\"_type_\":\"security\",\"_value_\":\"\",\"_attribute_value\":\"off\"},{\"_type_\":\"parentfoldersecurity\",\"_value_\":\"\",\"_attribute_value\":\"off\"},{\"_type_\":\"startpoint\",\"include\":[{\"_attribute_filespec\":\"*\",\"_value_\":\"\",\"_attribute_type\":\"directory\"},{\"_attribute_filespec\":\"*.msg\",\"_value_\":\"\",\"_attribute_type\":\"file\"}],\"_attribute_path\":\"attribute path one\",\"_value_\":\"\"},{\"_type_\":\"startpoint\",\"include\":[{\"_attribute_filespec\":\"*\",\"_value_\":\"\",\"_attribute_type\":\"directory\"},{\"_attribute_filespec\":\"*.msg\",\"_value_\":\"\",\"_attribute_type\":\"file\"}],\"_attribute_path\":\"attribute path two\",\"_value_\":\"\"},{\"_type_\":\"sharesecurity\",\"_value_\":\"\",\"_attribute_value\":\"off\"}]},{\"_type_\":\"description\",\"_value_\":\"unit test: job\"},{\"_type_\":\"priority\",\"_value_\":\"5\"},{\"_type_\":\"schedule\",\"_children_\":[{\"_type_\":\"requestminimum\",\"_value_\":\"false\"},{\"_type_\":\"dayofmonth\",\"value\":[\"1\",\"15\"]}]},{\"_type_\":\"recrawl_interval\",\"_value_\":\"infinite\"},{\"_type_\":\"run_mode\",\"_value_\":\"scan once\"},{\"_type_\":\"reseed_interval\",\"_value_\":\"infinite\"},{\"_type_\":\"start_mode\",\"_value_\":\"manual\"},{\"_type_\":\"id\",\"_value_\":\"1505233221607\"},{\"_type_\":\"repository_connection\",\"_value_\":\"unit test: Repository connection\"},{\"_type_\":\"pipelinestage\",\"_children_\":[{\"_type_\":\"stage_isoutput\",\"_value_\":\"true\"},{\"_type_\":\"stage_id\",\"_value_\":\"0\"},{\"_type_\":\"stage_specification\"},{\"_type_\":\"stage_connectionname\",\"_value_\":\"unit test: Output connection\"}]},{\"_type_\":\"pipelinestage\",\"_children_\":[{\"_type_\":\"stage_isoutput\",\"_value_\":\"true\"},{\"_type_\":\"stage_id\",\"_value_\":\"1\"},{\"_type_\":\"stage_specification\"},{\"_type_\":\"stage_connectionname\",\"_value_\":\"unit test: Output connection\"},{\"_type_\":\"stage_prerequisite\",\"_value_\":\"0\"}]}]}}";
  

  @Test
  public void testNakedValue()
    throws ManifoldCFException {
      
    // Deserialize first
    final Configuration object2 = new Configuration();
    object2.fromJSON(testData);

    // Now, reserialize
    final String jsonResult = object2.toJSON();

    // Can't compare in this way; ordering of the input is not consistent.
    //Assert.assertEquals(jsonResult, testData);
    Assert.assertEquals(jsonResult.length(), testData.length());
  }
  
  @Test
  public void testBackAndForth()
    throws ManifoldCFException {
    // Create a Configuration structure
    ConfigurationNode connectionObject;
    ConfigurationNode child;
    Configuration requestObject;
    Configuration result;

    connectionObject = new ConfigurationNode("repositoryconnection");
    
    child = new ConfigurationNode("name");
    child.setValue("File Connection");
    connectionObject.addChild(connectionObject.getChildCount(),child);
      
    child = new ConfigurationNode("class_name");
    child.setValue("org.apache.manifoldcf.crawler.connectors.filesystem.FileConnector");
    connectionObject.addChild(connectionObject.getChildCount(),child);
      
    child = new ConfigurationNode("description");
    child.setValue("File Connection");
    connectionObject.addChild(connectionObject.getChildCount(),child);

    child = new ConfigurationNode("max_connections");
    child.setValue("100");
    connectionObject.addChild(connectionObject.getChildCount(),child);

    requestObject = new Configuration();
    requestObject.addChild(0,connectionObject);
    
    final String jsonResult = requestObject.toJSON();
    final String validation = "{\"repositoryconnection\":{\"_children_\":[{\"_type_\":\"name\",\"_value_\":\"File Connection\"},{\"_type_\":\"class_name\",\"_value_\":\"org.apache.manifoldcf.crawler.connectors.filesystem.FileConnector\"},{\"_type_\":\"description\",\"_value_\":\"File Connection\"},{\"_type_\":\"max_connections\",\"_value_\":\"100\"}]}}";
    Assert.assertEquals(validation.length(), jsonResult.length());
    
    Configuration object2 = new Configuration();
    object2.fromJSON(jsonResult);
    
    final String newResult = object2.toJSON();
    Assert.assertEquals(jsonResult.length(), newResult.length());
    //Assert.assertTrue(requestObject.equals(object2));
  }
  
}
