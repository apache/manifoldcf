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

function AddForcedMetadata_$SeqNum()
{
  if (editjob.forcedmetadata_$SeqNum_name.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ForcedMetadata.ForcedMetadataNameMustNotBeNull'))");
    editjob.forcedmetadata_$SeqNum_name.focus();
    return;
  }
  document.editjob.forcedmetadata_$SeqNum_op.value="Add";
  postFormSetAnchor("forcedmetadata_$SeqNum_tag");
}
	
function DeleteForcedMetadata_$SeqNum(n)
{
  eval("document.editjob.forcedmetadata_$SeqNum_"+n+"_op.value = 'Delete'");
  if (n == 0)
    postFormSetAnchor("forcedmetadata_$SeqNum_tag");
  else
    postFormSetAnchor("forcedmetadata_$SeqNum_"+(n-1)+"_tag");
}

function checkSpecificationForSave_$SeqNum()
{
  return true;
}

function checkSpecification_$SeqNum()
{
  return true;
}

//-->
</script>
