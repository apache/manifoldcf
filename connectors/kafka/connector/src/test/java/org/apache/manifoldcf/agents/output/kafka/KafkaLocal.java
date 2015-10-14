package org.apache.manifoldcf.agents.output.kafka;

import java.io.IOException;
import java.util.Properties;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;

public class KafkaLocal {

  public KafkaServerStartable kafka;
  public ZooKeeperLocal zookeeper;

  public KafkaLocal(Properties kafkaProperties, Properties zkProperties) throws IOException, InterruptedException {
    KafkaConfig kafkaConfig = new KafkaConfig(kafkaProperties);

    //start local zookeeper
    System.out.println("starting local zookeeper...");
    zookeeper = new ZooKeeperLocal(zkProperties);
    System.out.println("done");

    //start local kafka broker
    kafka = new KafkaServerStartable(kafkaConfig);
    System.out.println("starting local kafka broker...");

    kafka.startup();
    System.out.println("done");
  }

  public void stop() {
    //stop kafka broker
    System.out.println("stopping kafka...");
    kafka.shutdown();
    System.out.println("done");
  }

}
