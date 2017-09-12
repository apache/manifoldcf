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
  
  private final static String testData = "{\"job\":{\"id\":\"1505233221607\",\"description\":\"unit test: job\",\"repository_connection\":\"unit test: Repository connection\",\"document_specification\":{\"security\":{\"_value_\":\"\",\"_attribute_value\":\"off\"},\"parentfoldersecurity\":{\"_value_\":\"\",\"_attribute_value\":\"off\"},\"startpoint\":[{\"_value_\":\"\",\"_attribute_path\":\"attribute path one\",\"include\":[{\"_value_\":\"\",\"_attribute_filespec\":\"*\",\"_attribute_type\":\"directory\"},{\"_value_\":\"\",\"_attribute_filespec\":\"*.msg\",\"_attribute_type\":\"file\"}]},{\"_value_\":\"\",\"_attribute_path\":\"attribute path two\",\"include\":[{\"_value_\":\"\",\"_attribute_filespec\":\"*\",\"_attribute_type\":\"directory\"},{\"_value_\":\"\",\"_attribute_filespec\":\"*.msg\",\"_attribute_type\":\"file\"}]}],\"sharesecurity\":{\"_value_\":\"\",\"_attribute_value\":\"off\"}},\"pipelinestage\":[{\"stage_id\":\"0\",\"stage_isoutput\":\"true\",\"stage_connectionname\":\"unit test: Output connection\",\"stage_specification\":{}},{\"stage_id\":\"1\",\"stage_prerequisite\":\"0\",\"stage_isoutput\":\"true\",\"stage_connectionname\":\"unit test: Output connection\",\"stage_specification\":{}}],\"start_mode\":\"manual\",\"run_mode\":\"scan once\",\"hopcount_mode\":\"accurate\",\"priority\":\"5\",\"recrawl_interval\":\"infinite\",\"expiration_interval\":\"infinite\",\"reseed_interval\":\"infinite\",\"schedule\":{\"requestminimum\":\"false\",\"dayofmonth\":{\"value\":[\"1\",\"15\"]}}}}";

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
    
    Assert.assertEquals("{\"repositoryconnection\":{\"name\":\"File Connection\",\"class_name\":\"org.apache.manifoldcf.crawler.connectors.filesystem.FileConnector\",\"description\":\"File Connection\",\"max_connections\":\"100\"}}".length(), jsonResult.length());
    
    Configuration object2 = new Configuration();
    object2.fromJSON(jsonResult);
    
    final String newResult = object2.toJSON();
    Assert.assertEquals(jsonResult.length(), newResult.length());
    //Assert.assertTrue(requestObject.equals(object2));
  }
  
}
