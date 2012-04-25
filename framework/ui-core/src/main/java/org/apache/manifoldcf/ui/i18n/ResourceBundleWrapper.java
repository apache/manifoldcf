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
package org.apache.manifoldcf.ui.i18n;

import java.util.ResourceBundle;
import java.util.Locale;

public class ResourceBundleWrapper
{
  protected ResourceBundle resourceBundle;
  protected String bundleName;
  protected Locale locale;
  
  /** Constructor
  */
  public ResourceBundleWrapper(ResourceBundle resourceBundle, String bundleName,
    Locale locale)
  {
    this.resourceBundle = resourceBundle;
    this.bundleName = bundleName;
    this.locale = locale;
  }
  
  /** Get a string with appropruate handling of exceptions
  */
  public String getString(String messageKey)
  {
    return Messages.getString(resourceBundle,bundleName,locale,messageKey);
  }
  
  /** Same thing with arguments
  */
  public String getString(String messageKey, Object[] args)
  {
    return Messages.getString(resourceBundle,bundleName,locale,messageKey,args);
  }
  
}

