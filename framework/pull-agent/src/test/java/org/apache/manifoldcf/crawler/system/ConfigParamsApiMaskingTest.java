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
package org.apache.manifoldcf.crawler.system;

import org.apache.manifoldcf.core.interfaces.ConfigNode;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.ConfigurationNode;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigParamsApiMaskingTest {

  @Test
  public void testFormatMasksObfuscatedParametersAndProcessPreservesThem() {
    final ConfigParams stored = new ConfigParams();
    stored.setParameter("server", "ldap.example.com");
    stored.setObfuscatedParameter("ldapBindPass", "top-secret");

    final ConfigurationNode connectionNode = new ConfigurationNode("connection");
    ManifoldCF.formatConfigParams(connectionNode, stored);

    assertEquals(1, connectionNode.getChildCount());
    final ConfigurationNode configNode = connectionNode.findChild(0);
    String maskedServer = null;
    String maskedPass = null;
    for (int i = 0; i < configNode.getChildCount(); i++) {
      final ConfigurationNode cn = configNode.findChild(i);
      final String name = cn.getAttributeValue("name");
      if ("server".equals(name)) {
        maskedServer = cn.getValue();
      } else if ("ldapBindPass".equals(name)) {
        maskedPass = cn.getValue();
      }
    }
    assertEquals("ldap.example.com", maskedServer);
    assertEquals(ConfigParams.OBFUSCATED_MASK, maskedPass);

    // Simulate API PUT round-trip modifying non-secret field while echoing back masked password
    final ConfigurationNode updatedConfig = new ConfigurationNode("configuration");
    final ConfigNode updatedServer = new ConfigNode("_PARAMETER_");
    updatedServer.setAttribute("name", "server");
    updatedServer.setValue("ldap2.example.com");
    updatedConfig.addChild(0, updatedServer);

    final ConfigNode echoedPass = new ConfigNode("_PARAMETER_");
    echoedPass.setAttribute("name", "ldapBindPass");
    echoedPass.setValue(ConfigParams.OBFUSCATED_MASK);
    updatedConfig.addChild(1, echoedPass);

    ManifoldCF.processConfigParams(stored, updatedConfig);

    assertEquals("ldap2.example.com", stored.getParameter("server"));
    assertEquals("top-secret", stored.getObfuscatedParameter("ldapBindPass"));
  }
}
