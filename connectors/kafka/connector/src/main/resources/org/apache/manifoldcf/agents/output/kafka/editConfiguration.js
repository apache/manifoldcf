<!--
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<script type="text/javascript">
<!--
function checkConfig() {
  if (editconnection.ip) {
    if (editconnection.ip.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('KafkaConnector.PleaseSupplyValidKafkaLocation'))");
      editconnection.ip.focus();
      return false;
    }
  }
  if (editconnection.port) {
    if (editconnection.port.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('KafkaConnector.PleaseSupplyValidPort'))");
      editconnection.port.focus();
      return false;
    }
  }
  if (editconnection.topic) {
    if (editconnection.topic.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('KafkaConnector.PleaseSupplyValidTopic'))");
      editconnection.topic.focus();
      return false;
    }
  }
  return true;
}

function checkConfigForSave() {
  if (editconnection.ip) {
    if (editconnection.ip.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('KafkaConnector.PleaseSupplyValidIP'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('KafkaConnector.Parameters'))");
      editconnection.ip.focus();
      return false;
    }
  }
  if (editconnection.port) {
    if (editconnection.port.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('KafkaConnector.PleaseSupplyValidPort'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('KafkaConnector.Parameters'))");
      editconnection.port.focus();
      return false;
    }
  }
  if (editconnection.topic) {
    if (editconnection.topic.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('KafkaConnector.PleaseSupplyValidTopic'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('KafkaConnector.Parameters'))");
      editconnection.topic.focus();
      return false;
    }
  }
  return true;
}
//-->
</script>
