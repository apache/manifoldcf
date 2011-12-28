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
package org.apache.manifoldcf.agents.output.solr;

import java.util.Locale;

public class Messages extends org.apache.manifoldcf.core.i18n.Messages
{
  public static final String DEFAULT_BUNDLE_NAME="org.apache.manifoldcf.agents.output.solr.common";

  /** Constructor - do no instantiate
  */
  private Messages()
  {
  }
  
  // These four have limited applicability since they are all local to the core jar, which generally does not render
  // text.
  
  public static String getString(String messageKey)
  {
    return getString(DEFAULT_BUNDLE_NAME, Locale.getDefault(), messageKey, null);
  }
  
  public static String getString(String messageKey, Object[] args)
  {
    return getString(DEFAULT_BUNDLE_NAME, Locale.getDefault(), messageKey, args);
  }
  
  public static String getString(Locale locale, String messageKey)
  {
    return getString(DEFAULT_BUNDLE_NAME, locale, messageKey, null);
  }
  
  public static String getString(Locale locale, String messageKey, Object[] args)
  {
    return getString(DEFAULT_BUNDLE_NAME, locale, messageKey, args);
  }
  
  // More general methods which allow bundlenames and class loaders to be specified.
  
  public static String getString(String bundleName, String messageKey)
  {
    return getString(bundleName, Locale.getDefault(), messageKey, null);
  }

  public static String getString(ClassLoader classLoader, String bundleName, String messageKey)
  {
    return getString(classLoader, bundleName, Locale.getDefault(), messageKey, null);
  }
  
  public static String getString(String bundleName, String messageKey, Object[] args)
  {
    return getString(bundleName, Locale.getDefault(), messageKey, args);
  }

  public static String getString(ClassLoader classLoader, String bundleName, String messageKey, Object[] args)
  {
    return getString(classLoader, bundleName, Locale.getDefault(), messageKey, args);
  }
  
  public static String getString(String bundleName, Locale locale, String messageKey)
  {
    return getString(bundleName, locale, messageKey, null);
  }

  public static String getString(ClassLoader classLoader, String bundleName, Locale locale, String messageKey)
  {
    return getString(classLoader, bundleName, locale, messageKey, null);
  }
  
  public static String getString(String bundleName, Locale locale, String messageKey, Object[] args)
  {
    return getString(Messages.class.getClassLoader(), bundleName, locale, messageKey, args);
  }
  
}

