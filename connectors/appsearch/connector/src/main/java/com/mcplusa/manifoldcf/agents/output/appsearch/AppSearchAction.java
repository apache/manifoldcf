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
package com.mcplusa.manifoldcf.agents.output.appsearch;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.HttpClient;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Locale;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.crawler.system.Logging;

public class AppSearchAction extends AppSearchConnection {

  public enum CommandEnum {
    _optimize, _refresh, _stats, _forcemerge, _none;
  }

  public AppSearchAction(HttpClient client, AppSearchConfig config)
	  throws ManifoldCFException {
    super(config, client);
  }

  public void executeGET(CommandEnum cmd, boolean checkConnection)
	  throws ManifoldCFException, ServiceInterruption {
    String command = cmd.toString();
    if (cmd.equals(CommandEnum._none)) {
      command = "";
    }

    StringBuffer url = getApiUrl(command);
    HttpGet method = new HttpGet(url.toString());
    call(method);
    String error = checkJson(jsonException);
    if (getResult() == Result.OK && error == null) {
      return;
    }
    setResult("JSONERROR", Result.ERROR, error);
    Logging.connectors.warn("AppSearch: Commit failed: " + getResponse());
  }

  public void executePOST(CommandEnum cmd, boolean checkConnection) throws ManifoldCFException, ServiceInterruption {
    String command = cmd.toString();
    if (cmd.equals(CommandEnum._none)) {
      command = "";
    }

    StringBuffer url = getApiUrl(command);
    HttpPost method = new HttpPost(url.toString());
    call(method);
    String error = checkJson(jsonException);
    if (getResult() == Result.OK && error == null) {
      return;
    }
    setResult("JSONERROR", Result.ERROR, error);
    Logging.connectors.warn("AppSearch: Commit failed: " + getResponse());
  }

  @Override
  protected void handleIOException(IOException e) throws ManifoldCFException, ServiceInterruption {
    if (e instanceof InterruptedIOException && !(e instanceof SocketTimeoutException)) {
      throw new ManifoldCFException(e.getMessage(), ManifoldCFException.INTERRUPTED);
    }
    setResult(e.getClass().getSimpleName().toUpperCase(Locale.ROOT), Result.ERROR, e.getMessage());
    long currentTime = System.currentTimeMillis();

    throw new ServiceInterruption("IO exception: " + e.getMessage(), e,
	    currentTime + 60000L,
	    currentTime + 1L * 60L * 60000L,
	    1, false);
  }

}
