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

package org.apache.manifoldcf.agents.output.kafka;

import java.io.File;

import java.util.Properties;
import org.junit.After;
import static org.junit.Assert.fail;
import org.junit.Before;

public class BaseITHSQLDB extends org.apache.manifoldcf.crawler.tests.BaseITHSQLDB {

  KafkaLocal kafka;

  protected String[] getConnectorNames() {
    return new String[]{"CMIS"};
  }

  protected String[] getConnectorClasses() {
    return new String[]{"org.apache.manifoldcf.crawler.tests.TestingRepositoryConnector"};
  }

  protected String[] getOutputNames() {
    return new String[]{"Kafka"};
  }

  protected String[] getOutputClasses() {
    return new String[]{"org.apache.manifoldcf.agents.output.kafka.KafkaOutputConnector"};
  }

  @Before
  public void setupKafka()
          throws Exception {
    Properties kafkaProperties = new Properties();
    Properties zkProperties = new Properties();

    String tmpDir = System.getProperty("java.io.tmpdir");
    File logDir = new File(tmpDir, "kafka-logs");
    logDir.mkdir();
    File zookeeperDir = new File(tmpDir, "zookeeper");
    zookeeperDir.mkdir();
            
    //load properties
    kafkaProperties.put("broker.id", "0");
    kafkaProperties.put("port", "9092");
    kafkaProperties.put("num.network.threads", "3");
    kafkaProperties.put("num.io.threads", "8");
    kafkaProperties.put("socket.send.buffer.bytes", "102400");
    kafkaProperties.put("socket.receive.buffer.bytes", "102400");
    kafkaProperties.put("socket.request.max.bytes", "104857600");
    kafkaProperties.put("log.dirs", logDir.getAbsolutePath());
    kafkaProperties.put("num.partitions", "1");
    kafkaProperties.put("num.recovery.threads.per.data.dir", "1");
    kafkaProperties.put("log.retention.hours", "168");
    kafkaProperties.put("log.segment.bytes", "1073741824");
    kafkaProperties.put("log.retention.check.interval.ms", "300000");
    kafkaProperties.put("log.cleaner.enable", "false");
    kafkaProperties.put("zookeeper.connect", "localhost:2181");
    kafkaProperties.put("zookeeper.connection.timeout.ms", "6000");

    zkProperties.put("dataDir", zookeeperDir.getAbsolutePath());
    zkProperties.put("clientPort", "2181");
    zkProperties.put("maxClientCnxns", "0");

    //kafkaProperties.load(Class.class.getResourceAsStream("/kafkalocal.properties"));
    //zkProperties.load(Class.class.getResourceAsStream("/zklocal.properties"));
    System.out.println("Kafka is starting...");

    //start kafka
    kafka = new KafkaLocal(kafkaProperties, zkProperties);
    Thread.sleep(5000);
  }

  @After
  public void cleanUpKafka() {
    kafka.stop();
  }

}
