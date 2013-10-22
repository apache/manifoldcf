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
public abstract class SingleLevelErrorContext extends BaseProcessingContext
{
  protected static final String ERROR_NODE = "error";
  protected static final String ERROR_TYPE_LOGIN_NEEDED = "readapidenied";
  
  protected String nodeName;
  protected String errorType = null;
  
  public SingleLevelErrorContext(XMLStream theStream, String nodeName)
  {
    super(theStream);
    this.nodeName = nodeName;
  }

  public SingleLevelErrorContext(XMLStream theStream, String namespaceURI, String localName, String qName, Attributes atts, String nodeName)
  {
    super(theStream,namespaceURI,localName,qName,atts);
    this.nodeName = nodeName;
  }

  public boolean isLoginRequired()
  {
    return errorType != null && errorType.equals(ERROR_TYPE_LOGIN_NEEDED);
  }
  
  @Override
  protected XMLContext beginTag(String namespaceURI, String localName, String qName, Attributes atts)
    throws ManifoldCFException, ServiceInterruption
  {
    if (qName.equals(nodeName))
      return createChild(namespaceURI,localName,qName,atts);
    else if (qName.equals(ERROR_NODE))
    {
      // Parse error
      errorType = atts.getValue("code");
    }
    return super.beginTag(namespaceURI,localName,qName,atts);
  }
  
  protected abstract BaseProcessingContext createChild(String namespaceURI, String localName, String qName, Attributes atts);
  
  @Override
  protected void endTag()
    throws ManifoldCFException, ServiceInterruption
  {
    XMLContext theContext = theStream.getContext();
    String theTag = theContext.getQname();

    if (theTag.equals(nodeName))
    {
      BaseProcessingContext child = (BaseProcessingContext)theContext;
      finishChild(child);
    }
    else
      super.endTag();
  }
  
  protected abstract void finishChild(BaseProcessingContext child)
    throws ManifoldCFException;
  
}
