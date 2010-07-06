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
package org.apache.lcf.core.system;

import org.apache.lcf.core.interfaces.*;
import java.util.*;
import java.io.*;

/** This class represents the configuration data read from the main LCF configuration
* XML file.
*/
public class LCFConfiguration extends Configuration
{
  public static final String _rcsid = "@(#)$Id$";

  /** Constructor.
  */
  public LCFConfiguration()
  {
    super();
  }

  /** Construct from XML.
  *@param xmlStream is the input XML stream.
  */
  public LCFConfiguration(InputStream xmlStream)
    throws LCFException
  {
    super(xmlStream);
  }

  /** Return the root node type.
  *@return the node type name.
  */
  protected String getRootNodeLabel()
  {
    return "configuration";
  }
  
  /** Create a new object of the appropriate class.
  */
  protected Configuration createNew()
  {
    return new LCFConfiguration();
  }
  
}
