/* $Id: DocClassSpec.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.filenet;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import java.util.*;

public class DocClassSpec
{
  public static final String _rcsid = "@(#)$Id: DocClassSpec.java 988245 2010-08-23 18:39:35Z kwright $";

  // Each doc class has a set of metadata fields that should be ingested with it, as well as a set of matches that
  // describe WHICH documents to select.
  protected boolean allMetadata = false;
  protected HashMap metadataFields = new HashMap();
  protected ArrayList matchItems = new ArrayList();

  public DocClassSpec()
  {
  }

  public DocClassSpec(SpecificationNode sn)
  {
    // Now, scan for metadata etc.
    String allmetadata = sn.getAttributeValue(org.apache.manifoldcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_ALLMETADATA);
    if (allmetadata == null || allmetadata.length() == 0)
      allmetadata = "false";
    int j;
    if (allmetadata.equals("false"))
    {
      j = 0;
      while (j < sn.getChildCount())
      {
        SpecificationNode node = sn.getChild(j++);
        if (node.getType().equals(org.apache.manifoldcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_METADATAFIELD))
        {
          String fieldName = node.getAttributeValue(org.apache.manifoldcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
          metadataFields.put(fieldName,fieldName);
        }
      }
    }
    else
      allMetadata = true;

    j = 0;
    while (j < sn.getChildCount())
    {
      SpecificationNode node = sn.getChild(j++);
      if (node.getType().equals(org.apache.manifoldcf.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MATCH))
      {
        String matchTypeString = node.getAttributeValue(org.apache.manifoldcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_MATCHTYPE);
        String matchField = node.getAttributeValue(org.apache.manifoldcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_FIELDNAME);
        String matchValue = node.getAttributeValue(org.apache.manifoldcf.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
        appendMatch(matchTypeString,matchField,matchValue);
      }
    }

  }

  /** Set metadata to "all metadata" */
  public void setAllMetadata(boolean value)
  {
    this.allMetadata = value;
  }

  /** Add a metadata field to include */
  public void setMetadataField(String fieldName)
  {
    metadataFields.put(fieldName, fieldName);
    this.allMetadata = false;
  }

  /** Add a match */
  public int appendMatch(String matchType, String matchField, String matchValue)
  {
    int rval = matchItems.size();
    matchItems.add(new MatchItem(matchType, matchField, matchValue));
    return rval;
  }

  /** Get 'all metadata' flag */
  public boolean getAllMetadata()
  {
    return allMetadata;
  }

  /** Get the list of metadata fields */
  public String[] getMetadataFields()
  {
    String[] rval = new String[metadataFields.size()];
    Iterator iter = metadataFields.keySet().iterator();
    int i = 0;
    while (iter.hasNext())
    {
      rval[i++] = (String)iter.next();
    }
    return rval;
  }

  /** Check if a metadata field is included */
  public boolean checkMetadataIncluded(String fieldName)
  {
    if (allMetadata)
      return true;
    return (metadataFields.get(fieldName) != null);
  }

  /** Get the number of matches */
  public int getMatchCount()
  {
    return matchItems.size();
  }

  /** For a given match, get its type */
  public String getMatchType(int matchIndex)
  {
    MatchItem mi = (MatchItem)matchItems.get(matchIndex);
    return mi.getMatchType();
  }

  /** For a given match, get its field name */
  public String getMatchField(int matchIndex)
  {
    MatchItem mi = (MatchItem)matchItems.get(matchIndex);
    return mi.getMatchField();
  }

  /** For a given match, get its match value */
  public String getMatchValue(int matchIndex)
  {
    MatchItem mi = (MatchItem)matchItems.get(matchIndex);
    return mi.getMatchValue();
  }

  protected static class MatchItem
  {
    String matchType;
    String matchField;
    String matchValue;

    public MatchItem(String matchType, String matchField, String matchValue)
    {
      this.matchType = matchType;
      this.matchField = matchField;
      this.matchValue = matchValue;
    }

    public String getMatchType()
    {
      return matchType;
    }

    public String getMatchField()
    {
      return matchField;
    }

    public String getMatchValue()
    {
      return matchValue;
    }
  }
}
