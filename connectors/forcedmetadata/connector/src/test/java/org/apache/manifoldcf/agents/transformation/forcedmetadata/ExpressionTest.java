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

import org.apache.manifoldcf.agents.transformation.forcedmetadata.*;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import java.io.*;
import java.util.*;

import org.junit.*;
import static org.junit.Assert.*;

public class ExpressionTest
{

  @Test
  public void simpleExpressions()
    throws Exception {
    RepositoryDocument inputDoc = new RepositoryDocument();
    inputDoc.addField("stringfield",new String[]{"stringa","stringb","stringc"});
    inputDoc.addField("readerfield",new Reader[]{new StringReader("readera"),new StringReader("readerb")});
    inputDoc.addField("datefield",new Date[]{new Date(0L), new Date(100000000L)});
    FieldDataFactory fdf = new FieldDataFactory(inputDoc);
    try {
      arrayEquals(new String[]{"stringa","stringb","stringc"}, (String[])(ForcedMetadataConnector.processExpression("${stringfield}", fdf).getRawForm()));
      arrayEquals(new String[]{"prefixstringapostfix","prefixstringbpostfix","prefixstringcpostfix"}, (String[])(ForcedMetadataConnector.processExpression("prefix${stringfield}postfix", fdf).getRawForm()));
      arrayEquals(new Reader[]{new StringReader("readera"),new StringReader("readerb")}, (Reader[])(ForcedMetadataConnector.processExpression("${readerfield}", fdf).getRawForm()));
      // Second access of reader fields, without prior string conversion, also must work
      arrayEquals(new Reader[]{new StringReader("readera"),new StringReader("readerb")}, (Reader[])(ForcedMetadataConnector.processExpression("${readerfield}", fdf).getRawForm()));
      arrayEquals(new String[]{"prefixreaderapostfix","prefixreaderbpostfix"}, (String[])(ForcedMetadataConnector.processExpression("prefix${readerfield}postfix", fdf).getRawForm()));
      arrayEquals(new String[]{"prefixapostfix","prefixbpostfix","prefixcpostfix"}, (String[])(ForcedMetadataConnector.processExpression("prefix${stringfield|string([abc])|1}postfix", fdf).getRawForm()));
      arrayEquals(new String[]{"prefixApostfix","prefixBpostfix","prefixCpostfix"}, (String[])(ForcedMetadataConnector.processExpression("prefix${stringfield|string([abc])|1u}postfix", fdf).getRawForm()));
    } finally {
      fdf.close();
    }
  }
  
  protected static void arrayEquals(Reader[] expected, Reader[] actual)
    throws Exception {
    assertEquals(expected.length,actual.length);
    Set<String> expectedSet = new HashSet<String>();
    for (Reader expectedValue : expected) {
      expectedSet.add(readData(expectedValue));
    }
    for (Reader actualValue : actual) {
      assertEquals(true,expectedSet.contains(readData(actualValue)));
    }

  }
  
  protected static String readData(Reader r)
    throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] buffer = new char[65536];
    while (true) {
      int amt = r.read(buffer);
      if (amt == -1)
        break;
      sb.append(buffer,0,amt);
    }
    return sb.toString();
  }
  
  protected static void arrayEquals(String[] expected, String[] actual)
    throws Exception {
    assertEquals(expected.length,actual.length);
    Set<String> expectedSet = new HashSet<String>();
    for (String expectedValue : expected) {
      expectedSet.add(expectedValue);
    }
    for (String actualValue : actual) {
      assertEquals(true,expectedSet.contains(actualValue));
    }
  }
}
