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
package org.apache.manifoldcf.core.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class LdapEscaperTest {

  @Test
  public void testEscapeFilterSpecialCharacters() {
    assertEquals("", LdapEscaper.escapeFilter(null));
    assertEquals("", LdapEscaper.escapeFilter(""));
    assertEquals("jdoe", LdapEscaper.escapeFilter("jdoe"));
    assertEquals("\\2a\\29\\28uid=admin\\29\\28|\\28x=", LdapEscaper.escapeFilter("*)(uid=admin)(|(x="));
    assertEquals("a\\5cb\\00c", LdapEscaper.escapeFilter("a\\b\u0000c"));
  }

  @Test
  public void testLiteralReplacementPreservesEscapesAndDollarSigns() {
    final String template = "(&(objectClass=inetOrgPerson)(uid={0}))";
    final String input = "*)(uid=$1)\\admin";
    final String filter = template.replace("{0}", LdapEscaper.escapeFilter(input));
    assertEquals("(&(objectClass=inetOrgPerson)(uid=\\2a\\29\\28uid=$1\\29\\5cadmin))", filter);
  }

  @Test
  public void testEscapeDNSpecialCharacters() {
    assertEquals("", LdapEscaper.escapeDN(null));
    assertEquals("", LdapEscaper.escapeDN(""));
    assertEquals("\\ leading\\ ", LdapEscaper.escapeDN(" leading "));
    assertEquals("\\#hash", LdapEscaper.escapeDN("#hash"));
    assertEquals("a\\,b\\+c\\\"d\\\\e\\<f\\>g\\;h\\00", LdapEscaper.escapeDN("a,b+c\"d\\e<f>g;h\u0000"));
  }
}
