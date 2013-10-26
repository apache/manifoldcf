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
package org.apache.manifoldcf.activedirectory_tests;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.ManifoldCF;

import java.io.*;
import java.util.*;
import org.junit.*;

import org.apache.manifoldcf.core.tests.HTMLTester;

/** Basic UI navigation tests */
public class NavigationDerbyUI extends BaseUIDerby
{

  @Test
  public void createConnectionsAndJob()
    throws Exception
  {
    testerInstance.newTest(Locale.US);
    
    HTMLTester.Window window;
    HTMLTester.Link link;
    HTMLTester.Form form;
    HTMLTester.Textarea textarea;
    HTMLTester.Selectbox selectbox;
    HTMLTester.Button button;
    HTMLTester.Radiobutton radiobutton;
    HTMLTester.Loop loop;
    
    window = testerInstance.openMainWindow("http://localhost:8346/mcf-crawler-ui/index.jsp");

    // Login
    form = window.findForm(testerInstance.createStringDescription("loginform"));
    textarea = form.findTextarea(testerInstance.createStringDescription("userID"));
    textarea.setValue(testerInstance.createStringDescription("admin"));
    textarea = form.findTextarea(testerInstance.createStringDescription("password"));
    textarea.setValue(testerInstance.createStringDescription("admin"));
    button = window.findButton(testerInstance.createStringDescription("Login"));
    button.click();

    // Define an authority connection via the UI
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("List authority groups"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Add new authority group"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editgroup"));
    textarea = form.findTextarea(testerInstance.createStringDescription("groupname"));
    textarea.setValue(testerInstance.createStringDescription("MyAuthorityConnection"));
    button = window.findButton(testerInstance.createStringDescription("Save this authority group"));
    button.click();

    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("List authorities"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Add a new connection"));
    link.click();
    // Fill in a name
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    textarea = form.findTextarea(testerInstance.createStringDescription("connname"));
    textarea.setValue(testerInstance.createStringDescription("MyAuthorityConnection"));
    link = window.findLink(testerInstance.createStringDescription("Type tab"));
    link.click();
    // Select a type
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("classname"));
    selectbox.selectValue(testerInstance.createStringDescription("org.apache.manifoldcf.authorities.authorities.activedirectory.ActiveDirectoryAuthority"));
    selectbox = form.findSelectbox(testerInstance.createStringDescription("authoritygroup"));
    selectbox.selectValue(testerInstance.createStringDescription("MyAuthorityConnection"));
    button = window.findButton(testerInstance.createStringDescription("Continue to next page"));
    button.click();
    // Server tab
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Domain Controller tab"));
    link.click();
    window = testerInstance.findWindow(null);
    form = window.findForm(testerInstance.createStringDescription("editconnection"));
    textarea = form.findTextarea(testerInstance.createStringDescription("dcrecord_domaincontrollername"));
    textarea.setValue(testerInstance.createStringDescription("localhost"));
    textarea = form.findTextarea(testerInstance.createStringDescription("dcrecord_username"));
    textarea.setValue(testerInstance.createStringDescription("foo"));
    button = window.findButton(testerInstance.createStringDescription("Add rule to end of list"));
    button.click();
    window = testerInstance.findWindow(null);

    // Go back to the Name tab
    link = window.findLink(testerInstance.createStringDescription("Name tab"));
    link.click();
    // Now save the connection.
    window = testerInstance.findWindow(null);
    button = window.findButton(testerInstance.createStringDescription("Save this authority connection"));
    button.click();

    // Delete the authority connection
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("List authorities"));
    link.click();
    window = testerInstance.findWindow(null);
    link = window.findLink(testerInstance.createStringDescription("Delete MyAuthorityConnection"));
    link.click();

    testerInstance.executeTest();
  }
  
}
