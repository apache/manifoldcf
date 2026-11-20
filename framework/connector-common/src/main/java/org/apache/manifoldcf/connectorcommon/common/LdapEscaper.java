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
package org.apache.manifoldcf.connectorcommon.common;

/**
 * Helper class to encode LDAP names and filter values.
 */
public class LdapEscaper {

  private LdapEscaper() {
    // private
  }

  /**
   * Escape data for use in a LDAP distinguished name (DN).
   *
   * @param name the data to escape for a DN.
   *
   * @return name escaped for use in a DN.
   */
  public static String escapeDN(String name) {
    return org.apache.manifoldcf.core.util.LdapEscaper.escapeDN(name);
  }

  /**
   * Escape data for use in a LDAP filter query.
   *
   * @param filter the data to escape for a filter query.
   *
   * @return name escaped for use in a filter query.
   */
  public static String escapeFilter(String filter) {
    return org.apache.manifoldcf.core.util.LdapEscaper.escapeFilter(filter);
  }
}
