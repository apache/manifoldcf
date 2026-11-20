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

/**
 * Helper class to encode LDAP distinguished names (RFC 4514) and search filter values (RFC 4515).
 */
public class LdapEscaper {

  private LdapEscaper() {
    // utility class
  }

  /**
   * Escape data for use in an LDAP distinguished name (DN) per RFC 4514.
   *
   * @param name the data to escape for a DN.
   * @return name escaped for use in a DN.
   */
  public static String escapeDN(final String name) {
    if (name == null || name.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    if ((name.charAt(0) == ' ') || (name.charAt(0) == '#')) {
      sb.append('\\');
    }
    for (int i = 0; i < name.length(); i++) {
      final char curChar = name.charAt(i);
      switch (curChar) {
        case '\\':
          sb.append("\\\\");
          break;
        case ',':
          sb.append("\\,");
          break;
        case '+':
          sb.append("\\+");
          break;
        case '"':
          sb.append("\\\"");
          break;
        case '<':
          sb.append("\\<");
          break;
        case '>':
          sb.append("\\>");
          break;
        case ';':
          sb.append("\\;");
          break;
        case '\u0000':
          sb.append("\\00");
          break;
        default:
          sb.append(curChar);
      }
    }
    if ((name.length() > 1) && (name.charAt(name.length() - 1) == ' ')) {
      sb.insert(sb.length() - 1, '\\');
    }
    return sb.toString();
  }

  /**
   * Escape data for use in an LDAP search filter value per RFC 4515.
   *
   * @param filter the data to escape for a filter query.
   * @return value escaped for use in a filter query.
   */
  public static String escapeFilter(final String filter) {
    if (filter == null || filter.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < filter.length(); i++) {
      final char curChar = filter.charAt(i);
      switch (curChar) {
        case '\\':
          sb.append("\\5c");
          break;
        case '*':
          sb.append("\\2a");
          break;
        case '(':
          sb.append("\\28");
          break;
        case ')':
          sb.append("\\29");
          break;
        case '\u0000':
          sb.append("\\00");
          break;
        default:
          sb.append(curChar);
      }
    }
    return sb.toString();
  }
}
