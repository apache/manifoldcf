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

function s${SeqNum}_AddForcedMetadata()
{
  if (editjob.s${SeqNum}_forcedmetadata_name.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ForcedMetadata.ForcedMetadataNameMustNotBeNull'))");
    editjob.s${SeqNum}_forcedmetadata_name.focus();
    return;
  }
  document.editjob.s${SeqNum}_forcedmetadata_op.value="Add";
  postFormSetAnchor("s${SeqNum}_forcedmetadata_tag");
}
	
function s${SeqNum}_DeleteForcedMetadata(n)
{
  eval("document.editjob.s${SeqNum}_forcedmetadata_"+n+"_op.value = 'Delete'");
  if (n == 0)
    postFormSetAnchor("s${SeqNum}_forcedmetadata_tag");
  else
    postFormSetAnchor("s${SeqNum}_forcedmetadata_"+(n-1)+"_tag");
}

function s${SeqNum}_checkSpecificationForSave()
{
  return true;
}

function s${SeqNum}_checkSpecification()
{
  return true;
}

//-->
</script>
