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
package org.apache.manifoldcf.crawler.connectors.sharepoint;

import org.junit.*;
import static org.junit.Assert.*;

import java.util.*;

public class XMLGenTest
{

  @Test
  public void buildOrderedQueryTest()
    throws Exception
  {
    String orderedQuery = SPSProxyHelper.buildOrderedQuery("ID").get_any()[0].toString();
    assertEquals("<Query><OrderBy Override=\"TRUE\" UseIndexForOrderBy=\"TRUE\"><FieldRef Ascending=\"TRUE\" Name=\"ID\"/></OrderBy></Query>",orderedQuery);
  }
  
  @Test
  public void buildPagingQueryOptionsTest()
    throws Exception
  {
    String pagingXML = SPSProxyHelper.buildPagingQueryOptions("some next string").get_any()[0].toString();
    assertEquals("<QueryOptions><Paging ListItemCollectionPositionNext=\"some next string\"/><ViewAttributes Scope=\"Recursive\"/></QueryOptions>",pagingXML);
  }
  
  @Test
  public void buildViewFieldsTest()
    throws Exception
  {
    List<String> list = new ArrayList<String>();
    list.add("foo");
    list.add("bar");
    String viewFieldsXML = SPSProxyHelper.buildViewFields(list.toArray(new String[0])).get_any()[0].toString();
    assertEquals("<ViewFields><FieldRef Name=\"foo\"/><FieldRef Name=\"bar\"/></ViewFields>",viewFieldsXML);
  }
  
  @Test
  public void buildMatchQueryTest()
    throws Exception
  {
    String matchQuery = SPSProxyHelper.buildMatchQuery("foo","Text","bar").get_any()[0].toString();
    assertEquals("<Query><Where><Eq><FieldRef Name=\"foo\"/><Value Type=\"Text\">bar</Value></Eq></Where></Query>",matchQuery);
  }

}