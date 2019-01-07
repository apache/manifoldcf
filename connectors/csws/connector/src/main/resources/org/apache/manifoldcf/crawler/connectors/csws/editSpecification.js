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

    function ${SEQPREFIX}SpecOp(n, opValue, anchorvalue)
    {
      eval("editjob."+n+".value = \""+opValue+"\"");
      postFormSetAnchor(anchorvalue);
    }

    function ${SEQPREFIX}SpecAddToPath(anchorvalue)
    {
    console.log(anchorvalue);
      if (editjob.${SEQPREFIX}pathaddon.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.SelectAFolderFirst'))");
        editjob.${SEQPREFIX}pathaddon.focus();
        return;
      }

      ${SEQPREFIX}SpecOp("${SEQPREFIX}pathop","AddToPath",anchorvalue);
    }

    function ${SEQPREFIX}SpecAddFilespec(anchorvalue)
    {
      if (editjob.${SEQPREFIX}specfile.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.TypeInAFileSpecification'))");
        editjob.${SEQPREFIX}specfile.focus();
        return;
      }
      ${SEQPREFIX}SpecOp("${SEQPREFIX}fileop","Add",anchorvalue);
    }

    function ${SEQPREFIX}SpecAddToken(anchorvalue)
    {
      if (editjob.${SEQPREFIX}spectoken.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.TypeInAnAccessToken'))");
        editjob.${SEQPREFIX}spectoken.focus();
        return;
      }
      ${SEQPREFIX}SpecOp("${SEQPREFIX}accessop","Add",anchorvalue);
    }

    function ${SEQPREFIX}SpecAddToMetadata(anchorvalue)
    {
      if (editjob.${SEQPREFIX}metadataaddon.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.SelectAFolderFirst'))");
        editjob.${SEQPREFIX}metadataaddon.focus();
        return;
      }
      ${SEQPREFIX}SpecOp("${SEQPREFIX}metadataop","AddToPath",anchorvalue);
    }

    function ${SEQPREFIX}SpecSetWorkspace(anchorvalue)
    {
      if (editjob.${SEQPREFIX}metadataaddon.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.SelectAWorkspaceFirst'))");
        editjob.${SEQPREFIX}metadataaddon.focus();
        return;
      }
      ${SEQPREFIX}SpecOp("${SEQPREFIX}metadataop","SetWorkspace",anchorvalue);
    }

    function ${SEQPREFIX}SpecAddCategory(anchorvalue)
    {
      if (editjob.${SEQPREFIX}categoryaddon.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.SelectACategoryFirst'))");
        editjob.${SEQPREFIX}categoryaddon.focus();
        return;
      }
      ${SEQPREFIX}SpecOp("${SEQPREFIX}metadataop","AddCategory",anchorvalue);
    }

    function ${SEQPREFIX}SpecAddMetadata(anchorvalue)
    {
      if (editjob.${SEQPREFIX}attributeselect.value == "" && editjob.${SEQPREFIX}attributeall.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.SelectAtLeastOneAttributeFirst'))");
        editjob.${SEQPREFIX}attributeselect.focus();
        return;
      }
      ${SEQPREFIX}SpecOp("${SEQPREFIX}metadataop","Add",anchorvalue);
    }

    function ${SEQPREFIX}SpecAddMapping(anchorvalue)
    {
      if (editjob.${SEQPREFIX}specmatch.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.MatchStringCannotBeEmpty'))");
        editjob.${SEQPREFIX}specmatch.focus();
        return;
      }
      if (!isRegularExpression(editjob.${SEQPREFIX}specmatch.value))
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.MatchStringMustBeValidRegularExpression'))");
        editjob.${SEQPREFIX}specmatch.focus();
        return;
      }
      ${SEQPREFIX}SpecOp("${SEQPREFIX}specmappingop","Add",anchorvalue);
    }
    //-->
</script>