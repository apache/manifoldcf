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

import org.apache.manifoldcf.core.system.ManifoldCF;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigParamsObfuscationTest {

  @Test
  public void testSetObfuscatedParameterMarksNodeAndRoundTrips() {
    final ConfigParams cp = new ConfigParams();
    cp.setParameter("host", "db.example.com");
    cp.setObfuscatedParameter("bindPass", "s3cr3t");

    assertEquals("db.example.com", cp.getParameter("host"));
    assertEquals("s3cr3t", cp.getObfuscatedParameter("bindPass"));

    boolean foundHost = false;
    boolean foundPass = false;
    for (int i = 0; i < cp.getChildCount(); i++) {
      final ConfigurationNode cn = cp.findChild(i);
      if ("host".equals(cn.getAttributeValue("name"))) {
        foundHost = true;
        assertFalse(ConfigParams.isObfuscatedNode(cn));
      } else if ("bindPass".equals(cn.getAttributeValue("name"))) {
        foundPass = true;
        assertTrue(ConfigParams.isObfuscatedNode(cn));
        assertEquals("true", cn.getAttributeValue(ConfigParams.ATTR_OBFUSCATED));
      }
    }
    assertTrue(foundHost);
    assertTrue(foundPass);
  }

  @Test
  public void testLegacyNameHeuristicDetectsUnmarkedPasswordNodes() {
    final ConfigNode legacyNode = new ConfigNode("_PARAMETER_");
    legacyNode.setAttribute("name", "Database password");
    legacyNode.setValue("obfuscatedValue");
    assertTrue(ConfigParams.isObfuscatedNode(legacyNode));

    final ConfigNode normalNode = new ConfigNode("_PARAMETER_");
    normalNode.setAttribute("name", "Server name");
    normalNode.setValue("localhost");
    assertFalse(ConfigParams.isObfuscatedNode(normalNode));
  }

  @Test
  public void testObfuscateAndDeobfuscateRoundTrip() throws Exception {
    final String encoded = ManifoldCF.obfuscate("my-secret-value");
    assertNotNull(encoded);
    assertNotEquals("my-secret-value", encoded);
    assertEquals("my-secret-value", ManifoldCF.deobfuscate(encoded));
  }
}
