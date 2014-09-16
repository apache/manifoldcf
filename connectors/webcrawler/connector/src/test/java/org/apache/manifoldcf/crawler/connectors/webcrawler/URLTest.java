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

import org.apache.manifoldcf.crawler.connectors.webcrawler.WebURL;
import org.junit.*;
import static org.junit.Assert.*;

public class URLTest
{

  @Test
  public void absolutePath()
    throws Exception
  {
    WebURL parent = new WebURL("http://foo.com");
    WebURL resolved = parent.resolve("http://bar.com");
    assertEquals(resolved.toASCIIString(),"http://bar.com");
  }

  @Test
  public void relativePath()
    throws Exception
  {
    WebURL parent = new WebURL("http://foo.com/abc/def.html");
    WebURL resolved = parent.resolve("/def/ghi.html");
    assertEquals(resolved.toASCIIString(),"http://foo.com/def/ghi.html");
  }

  @Test
  public void noSlashDocument()
    throws Exception
  {
    WebURL parent = new WebURL("http://foo.com");
    WebURL resolved = parent.resolve("hello.pdf");
    assertEquals(resolved.toASCIIString(),"http://foo.com/hello.pdf");
  }

  @Test
  public void relativeQuery()
    throws Exception
  {
    WebURL parent = new WebURL("http://foo.com/abc/def/ghi.asmx?q=foo");
    WebURL resolved = parent.resolve("?q=bar");
    assertEquals(resolved.toASCIIString(),"http://foo.com/abc/def/ghi.asmx?q=bar");
  }

  @Test
  public void queryEscaping()
    throws Exception
  {
    WebURL parent = new WebURL("http://foo.com/abc/def/ghi.asmx?q=foo%3Dbar");
    WebURL resolved = parent.resolve("?q=bar%3Dfoo");
    assertEquals(resolved.toASCIIString(),"http://foo.com/abc/def/ghi.asmx?q=bar%3Dfoo");
  }


}
