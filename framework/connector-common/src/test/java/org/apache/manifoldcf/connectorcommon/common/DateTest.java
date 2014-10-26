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
package org.apache.manifoldcf.core.common;

import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class DateTest
{

  @Test
  public void iso8601()
    throws Exception
  {
    Date d = DateParser.parseISO8601Date("96-11-15T01:32:33.344GMT");
    assertNotNull(d);
    Date d2 = DateParser.parseISO8601Date("1996-11-15T01:32:33.344Z");
    assertNotNull(d2);
    assertEquals(d,d2);
    d = DateParser.parseISO8601Date("2012-11-15T01:32:33Z");
    assertNotNull(d);
    d = DateParser.parseISO8601Date("2012-11-15T01:32:33+0100");
    assertNotNull(d);
    d = DateParser.parseISO8601Date("2012-11-15T01:32:33-03:00");
    assertNotNull(d);
    d = DateParser.parseISO8601Date("2012-11-15T01:32:33GMT-03:00");
    assertNotNull(d);
    d = DateParser.parseISO8601Date("2012-11-15T01:32:33.001-04:00");
    assertNotNull(d);
    // Microsoft variation
    d = DateParser.parseISO8601Date("2014-06-03 11:21:37");
    assertNotNull(d);
  }


}
