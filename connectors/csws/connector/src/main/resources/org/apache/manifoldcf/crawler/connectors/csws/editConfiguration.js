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
    function ServerDeleteCertificate(aliasName)
    {
      editconnection.serverkeystorealias.value = aliasName;
      editconnection.serverconfigop.value = "Delete";
      postForm();
    }

    function ServerAddCertificate()
    {
      if (editconnection.servercertificate.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.ChooseACertificateFile'))");
        editconnection.servercertificate.focus();
      }
      else
      {
        editconnection.serverconfigop.value = "Add";
        postForm();
      }
    }

    function IngestDeleteCertificate(aliasName)
    {
      editconnection.ingestkeystorealias.value = aliasName;
      editconnection.ingestconfigop.value = "Delete";
      postForm();
    }

    function IngestAddCertificate()
    {
      if (editconnection.ingestcertificate.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.ChooseACertificateFile'))");
        editconnection.ingestcertificate.focus();
      }
      else
      {
        editconnection.ingestconfigop.value = "Add";
        postForm();
      }
    }

    function checkConfig()
    {
      if (editconnection.serverport.value != "" && !isInteger(editconnection.serverport.value))
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.AValidNumberIsRequired'))");
        editconnection.serverport.focus();
        return false;
      }
      if (editconnection.ingestport.value != "" && !isInteger(editconnection.ingestport.value))
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.AValidNumberOrBlankIsRequired'))");
        editconnection.ingestport.focus();
        return false;
      }
      if (editconnection.viewport.value != "" && !isInteger(editconnection.viewport.value))
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.AValidNumberOrBlankIsRequired'))");
        editconnection.viewport.focus();
        return false;
      }
      return true;
    }

    function checkConfigForSave()
    {
      if (editconnection.servername.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterALivelinkServerName'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.servername.focus();
        return false;
      }
      if (editconnection.serverport.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.AServerPortNumberIsRequired'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.serverport.focus();
        return false;
      }
      if (editconnection.serverhttpcgipath.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterTheServerCgiPathToLivelink'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.serverhttpcgipath.focus();
        return false;
      }
      if (editconnection.serverhttpcgipath.value.substring(0,1) != "/")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.TheServerCgiPathMustBeginWithACharacter'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.serverhttpcgipath.focus();
        return false;
      }
      if (editconnection.viewprotocol.value == "" && editconnection.ingestprotocol.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.SelectAViewProtocol'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.DocumentView'))");
        editconnection.viewprotocol.focus();
        return false;
      }
      if (editconnection.viewcgipath.value == "" && editconnection.ingestcgipath.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterTheViewCgiPathToLivelink'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.DocumentView'))");
        editconnection.viewcgipath.focus();
        return false;
      }
      if (editconnection.ingestcgipath.value != "" && editconnection.ingestcgipath.value.substring(0,1) != "/")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.TheIngestCgiPathMustBeBlankOrBeginWithACharacter'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.DocumentAccess'))");
        editconnection.ingestcgipath.focus();
        return false;
      }
      if (editconnection.viewcgipath.value != "" && editconnection.viewcgipath.value.substring(0,1) != "/")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.TheViewCgiPathMustBeBlankOrBeginWithACharacter'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.DocumentView'))");
        editconnection.viewcgipath.focus();
        return false;
      }
      return true;
    }

    //-->
</script>