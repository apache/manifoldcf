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
package org.apache.manifoldcf.core.lockmanager;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

public class ZooKeeperInstance {
  protected final int zkPort;
  protected final File tempDir;

  protected ZooKeeperThread zookeeperThread = null;

  public ZooKeeperInstance(final int zkPort, final File tempDir) {
    this.zkPort = zkPort;
    this.tempDir = tempDir;
  }

  public void start() throws Exception {
    final Properties startupProperties = new Properties();
    startupProperties.setProperty("tickTime", "2000");
    startupProperties.setProperty("dataDir", tempDir.toString());
    startupProperties.setProperty("clientPort", Integer.toString(zkPort));
    startupProperties.setProperty("admin.enableServer", "false");

    final QuorumPeerConfig quorumConfiguration = new QuorumPeerConfig();
    quorumConfiguration.parseProperties(startupProperties);

    final ServerConfig configuration = new ServerConfig();
    configuration.readFrom(quorumConfiguration);

    zookeeperThread = new ZooKeeperThread(configuration);
    zookeeperThread.start();
    // We have no way of knowing whether zookeeper is alive or not, but the
    // client is supposed to know about that. But it doesn't, so wait for 5 seconds
    Thread.sleep(5000L);
  }

  public void stop() throws Exception {
    while (true) {
      if (zookeeperThread == null)
        break;
      else if (!zookeeperThread.isAlive()) {
        final Throwable e = zookeeperThread.finishUp();
        if (e != null) {
          if (e instanceof RuntimeException)
            throw (RuntimeException) e;
          else if (e instanceof Exception)
            throw (Exception) e;
          else if (e instanceof Error)
            throw (Error) e;
        }
        zookeeperThread = null;
      } else {
        // This isn't the best way to kill zookeeper but it's the only way
        // we've got.
        zookeeperThread.interrupt();
        Thread.sleep(1000L);
      }
    }
  }

  protected static class ZooKeeperThread extends Thread {
    protected final ServerConfig config;

    protected Throwable exception = null;

    public ZooKeeperThread(final ServerConfig config) {
      this.config = config;
    }

    @Override
    public void run() {
      try {
        final ZooKeeperServerMain server = new ZooKeeperServerMain();
        server.runFromConfig(config);
      } catch (final IOException e) {
        // Ignore IOExceptions, since that seems to be normal when shutting
        // down zookeeper via thread.interrupt()
      } catch (final Throwable e) {
        exception = e;
      }
    }

    public Throwable finishUp() throws InterruptedException {
      join();
      return exception;
    }
  }

}
