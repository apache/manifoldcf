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
package org.apache.manifoldcf.agents.output.elasticsearch.tests;

import org.junit.After;
import org.junit.Before;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.impl.client.HttpClients;
import java.io.IOException;
import java.io.File;
  

/**  
 *  Base integration tests class for Elastic Search tested against a CMIS repository
 *  @author Piergiorgio Lucidi
 * 
 * */
public class BaseITHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseITHSQLDB
{

  final static boolean isUnix;
  static {
    final String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      isUnix = false;
    } else {
      isUnix = true;
    }
  }

  protected String[] getConnectorNames()
  {
    return new String[]{"CMIS"};
  }
  
  protected String[] getConnectorClasses()
  {
    return new String[]{"org.apache.manifoldcf.crawler.tests.TestingRepositoryConnector"};
  }

  protected String[] getOutputNames()
  {
    return new String[]{"ElasticSearch"};
  }
  
  protected String[] getOutputClasses()
  {
    return new String[]{"org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchConnector"};
  }

  Process esTestProcess = null;
  
  @Before
  public void setupElasticSearch()
    throws Exception
  {
    final ProcessBuilder pb = new ProcessBuilder();
    
    final File absFile = new File(".").getAbsoluteFile();
    System.out.println("ES working directory is '"+absFile+"'");
    pb.directory(absFile);
    
    if (isUnix) {
      pb.command("bash", "-c", "../test-materials/elasticsearch-7.6.2/bin/elasticsearch -q");
      System.out.println("Unix process");
    } else {
      pb.command("cmd.exe", "..\\test-materials\\elasticsearch-7.6.2\\bin\\elasticsearch.bat -q");
      System.out.println("Windows process");
    }

    File log = new File("log");
    pb.redirectErrorStream(true);
    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
    esTestProcess = pb.start();
    System.out.println("ElasticSearch is starting...");
    //the default port is 9200
    
    waitForElasticSearch();
    
    System.out.println("ElasticSearch is started on port 9200");
  }
  
  public void waitForElasticSearch() {
    for (int i = 0 ; i < 10 ; i++) {
      CloseableHttpClient httpclient = HttpClients.createDefault();
      HttpGet httpGet = new HttpGet("http://127.0.0.1:9200/");
      try {
        CloseableHttpResponse response1 = httpclient.execute(httpGet);
        try {
          System.out.println("Response from ES: "+response1.getStatusLine());
          HttpEntity entity1 = response1.getEntity();
          // do something useful with the response body
          // and ensure it is fully consumed
          EntityUtils.consume(entity1);
        } finally {
          response1.close();
        }
        System.out.println("ES came up!");
        return;
      } catch (IOException e) {
        // Wait 500ms and try again
        System.out.println("Didn't reach ES; waiting...");
        try {
          Thread.sleep(500);
        } catch (InterruptedException e1) {
          break;
        }
      }
    }
    throw new IllegalStateException("ES didn't come up.");
  }
  
  @After
  public void cleanUpElasticSearch(){
    if (esTestProcess != null) {
      esTestProcess.destroy();
    }
  }
  
}
