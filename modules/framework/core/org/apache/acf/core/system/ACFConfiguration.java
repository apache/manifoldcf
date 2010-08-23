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
package org.apache.acf.core.system;

import org.apache.acf.core.interfaces.*;
import java.util.*;
import java.io.*;

/** This class represents the configuration data read from the main ACF configuration
* XML file.
*/
public class ACFConfiguration extends Configuration
{
  public static final String _rcsid = "@(#)$Id$";

  /** Constructor.
  */
  public ACFConfiguration()
  {
    super("configuration");
  }

  /** Construct from XML.
  *@param xmlStream is the input XML stream.
  */
  public ACFConfiguration(InputStream xmlStream)
    throws ACFException
  {
    super("configuration");
    fromXML(xmlStream);
  }

  /** Create a new object of the appropriate class.
  */
  protected Configuration createNew()
  {
    return new ACFConfiguration();
  }
  
}
