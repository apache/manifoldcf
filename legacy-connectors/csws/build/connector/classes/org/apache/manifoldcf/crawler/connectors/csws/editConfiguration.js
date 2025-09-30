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

    function checkConfig()
    {
      if (editconnection.serverport.value != "" && !isInteger(editconnection.serverport.value))
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.AValidNumberIsRequired'))");
        editconnection.serverport.focus();
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

      if (editconnection.authenticationservicepath.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterTheAuthenticationServicePath'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.authenticationservicepath.focus();
        return false;
      }
      if (editconnection.authenticationservicepath.value.substring(0,1) != "/")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.TheAuthenticationServicePathMustBeginWithACharacter'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.authenticationservicepath.focus();
        return false;
      }
      if (editconnection.documentmanagementservicepath.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterTheDocumentManagementServicePath'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.documentmanagementservicepath.focus();
        return false;
      }
      if (editconnection.documentmanagementservicepath.value.substring(0,1) != "/")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.TheDocumentManagementServicePathMustBeginWithACharacter'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.documentmanagementservicepath.focus();
        return false;
      }
      if (editconnection.memberserviceservicepath.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterTheMemberServiceServicePath'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.memberserviceservicepath.focus();
        return false;
      }
      if (editconnection.memberserviceservicepath.value.substring(0,1) != "/")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.TheMemberServiceServicePathMustBeginWithACharacter'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.memberserviceservicepath.focus();
        return false;
      }
      if (editconnection.contentserviceservicepath.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterTheContentServiceServicePath'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.contentserviceservicepath.focus();
        return false;
      }
      if (editconnection.contentserviceservicepath.value.substring(0,1) != "/")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.TheContentServiceServicePathMustBeginWithACharacter'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.contentserviceservicepath.focus();
        return false;
      }
      if (editconnection.searchserviceservicepath.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterTheSearchServiceServicePath'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.searchserviceservicepath.focus();
        return false;
      }
      if (editconnection.searchserviceservicepath.value.substring(0,1) != "/")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.TheSearchServiceServicePathMustBeginWithACharacter'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.searchserviceservicepath.focus();
        return false;
      }
      if (editconnection.datacollection.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.DataCollectionIsRequired'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.Server'))");
        editconnection.datacollection.focus();
        return false;
      }

      if (editconnection.viewprotocol.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.SelectAViewProtocol'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.DocumentView'))");
        editconnection.viewprotocol.focus();
        return false;
      }
      if (editconnection.viewcgipath.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.EnterTheViewCgiPathToLivelink'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('CswsConnector.DocumentView'))");
        editconnection.viewcgipath.focus();
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