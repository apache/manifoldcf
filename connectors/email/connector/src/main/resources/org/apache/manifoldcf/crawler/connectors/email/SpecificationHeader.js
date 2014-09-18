<!--
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements. See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<script type="text/javascript">
<!--

function s${SeqNum}_checkSpecification()
{
  if (s${SeqNum}_checkDocumentsTab() == false)
    return false;
  if (s${SeqNum}_checkMetadataTab() == false)
    return false;
  return true;
}
 
function s${SeqNum}_SpecOp(n, opValue, anchorvalue)
{
  eval("editjob."+n+".value = \""+opValue+"\"");
  postFormSetAnchor(anchorvalue);
}

function s${SeqNum}_checkDocumentsTab()
{
  return true;
}

function s${SeqNum}_checkMetadataTab()
{
  return true;
}

function s${SeqNum}_FindDelete(n)
{
  s${SeqNum}_SpecOp("s${SeqNum}_findop_"+n, "Delete", "s${SeqNum}_find_"+n);
}

function s${SeqNum}_FindAdd(n)
{
  if (editjob.s${SeqNum}_findname.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.PleaseSelectAMetadataName'))");
    editjob.s${SeqNum}_findname.focus();
    return;
  }
  if (editjob.s${SeqNum}_findvalue.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.ValueCannotBeBlank'))");
    editjob.s${SeqNum}_findvalue.focus();
    return;
  }
  s${SeqNum}_SpecOp("s${SeqNum}_findop", "Add", "s${SeqNum}_find_"+n);
}

//-->
</script>
