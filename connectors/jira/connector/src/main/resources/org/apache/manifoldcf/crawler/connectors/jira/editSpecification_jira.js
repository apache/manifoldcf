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
function checkSpecificationForSave()
{
  if (editjob.jiraquery.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('JiraRepositoryConnector.SeedQueryCannotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('JiraRepositoryConnector.JiraQuery'))");
    editjob.jiraquery.focus();
    return false;
  }
  return true;
}
 
function SpecOp(n, opValue, anchorvalue)
{
  eval("editjob."+n+".value = \""+opValue+"\"");
  postFormSetAnchor(anchorvalue);
}

function SpecDeleteToken(i)
{
  SpecOp("accessop_"+i,"Delete","token_"+i);
}

function SpecAddToken(i)
{
  if (editjob.spectoken.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('JiraRepositoryConnector.TypeInAnAccessToken'))");
    editjob.spectoken.focus();
    return;
  }
  SpecOp("accessop","Add","token_"+i);
}

//-->
</script>
