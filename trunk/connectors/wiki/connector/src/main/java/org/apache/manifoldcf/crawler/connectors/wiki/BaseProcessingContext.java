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
package org.apache.manifoldcf.crawler.connectors.wiki;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;

import org.xml.sax.Attributes;

import org.apache.manifoldcf.agents.common.XMLStream;
import org.apache.manifoldcf.agents.common.XMLContext;

/** Abstract class representing an api/query context.  Create one of these
* and pass it into the general parse for the desired response parsing behavior.
*/
public abstract class BaseProcessingContext extends XMLContext
{
  public BaseProcessingContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts)
  {
    super(theStream,namespaceURI,localName,qName,atts);
  }

  public BaseProcessingContext(XMLStream theStream)
  {
    super(theStream);
  }

  @Override
  protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
    throws ManifoldCFException, ServiceInterruption
  {
    return super.beginTag(namespaceURI,localName,qName,atts);
  }
  
  @Override
  protected void endTag()
    throws ManifoldCFException, ServiceInterruption
  {
    super.endTag();
  }
    
  /** Process this data */
  protected void process()
    throws ManifoldCFException
  {
  }
}
