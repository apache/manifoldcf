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
package org.apache.manifoldcf.crawler.connectors.webcrawler;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.fuzzyml.*;
import java.util.*;

/** This class recognizes and interprets all meta tags */
public class MetaParseState extends ScriptParseState
{
  protected IMetaTagHandler handler;

  public MetaParseState(IMetaTagHandler handler)
  {
    super();
    this.handler = handler;
  }

  @Override
  protected boolean noteNonscriptTag(String tagName, Map<String,String> attributes)
    throws ManifoldCFException
  {
    if (super.noteNonscriptTag(tagName,attributes))
      return true;
    if (tagName.equals("meta"))
    {
      handler.noteMetaTag(attributes);
    }
    return false;
  }

}
