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
package org.apache.manifoldcf.filesystem_tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

import org.apache.manifoldcf.core.tests.HTMLTester;

/** Basic UI navigation tests */
public class NavigationUI extends BaseUIDerby
{

  @Test
  public void createConnectionsAndJob()
    throws Exception
  {
    testerInstance.newTest();
    
    HTMLTester.Window window;
    HTMLTester.Link link;
    HTMLTester.Form form;
    HTMLTester.Textarea textarea;
    HTMLTester.Selectbox selectbox;
    HTMLTester.Button button;
    
    window = testerInstance.openMainWindow("http://localhost:8346/mcf-crawler-ui/index.jsp",Locale.US);
    
    // Define an output connection via the UI
    link = window.findLink(testerInstance.createStringDescription("List output connections"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Add an output connection"));
    link.click();
    // Fill in a name
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    textarea = form.findTextarea(testerInstance.createStringDescription("connname"));
    textarea.setValue(testerInstance.createStringDescription("MyOutputConnection"));
    link = window.findLink(testerInstance.createStringDescription("Type tab"));
    link.click();
    // Select a type
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("classname"));
    selectbox.selectValue(testerInstance.createStringDescription("org.apache.manifoldcf.agents.output.nullconnector.NullConnector"));
    button = window.findButton(testerInstance.createStringDescription("Continue to next page"));
    button.click();
    // Visit the Throttling tab
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Throttling tab"));
    link.click();
    // Go back to the Name tab
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Name tab"));
    link.click();
    // Now save the connection.
    window = testerInstance.findWindow(null);
    button = window.findButton(testerInstance.createStringDescription("Save this output connection"));
    button.click();
    
    
    // MHL
    
    // Delete the output connection
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("List output connections"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Delete MyOutputConnection"));
    link.click();
    
    testerInstance.executeTest();
  }
  
}
