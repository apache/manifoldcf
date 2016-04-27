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
function checkConfig()
{ 
  
  if (editconnection.amazons3_proxy_port.value != "" && !isInteger(editconnection.amazons3_proxy_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Amazons3AuthorityConnector.Amazons3ProxyPortMustBeAnInteger'))");
    editconnection.amazons3_proxy_port.focus();
    return false;
  }

  if (editconnection.amazons3_proxy_host.value != "" && editconnection.amazons3_proxy_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Amazons3AuthorityConnector.Amazons3ProxyHostMustNotIncludeSlash'))");
    editconnection.amazons3_proxy_host.focus();
    return false;
  }
  
  return true;
}

function checkConfigForSave()
{
  
  
  if (editconnection.aws_access_key.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Amazons3AuthorityConnector.Amazons3AccessKeyShouldNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Amazons3AuthorityConnector.Server'))");
    editconnection.aws_access_key.focus();
    return false;
  }
  
  if (editconnection.aws_secret_key.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Amazons3AuthorityConnector.Amazons3SecretKeyShouldNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Amazons3AuthorityConnector.Server'))");
    editconnection.aws_secret_key.focus();
    return false;
  }
  
  return true;
}
//-->
</script>