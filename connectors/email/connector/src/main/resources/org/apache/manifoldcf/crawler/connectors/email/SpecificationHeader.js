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
function checkSpecification()
{
  if (checkDocumentsTab() == false)
      return false;
    if (checkMetadataTab() == false)
      return false;
    return true;
}
 
function SpecOp(n, opValue, anchorvalue)
{
  eval("editjob."+n+".value = \""+opValue+"\"");
  postFormSetAnchor(anchorvalue);
}

function checkDocumentsTab()
{
  return true;
}

function checkMetadataTab()
{
  return true;
}

function FindDelete(n)
{
  SpecOp("findop_"+n, "Delete", "find_"+n);
}

function FindAdd(n)
{
  if (editjob.findname.value == "")
  {
    alert("Please select a metadata name first.");
    editjob.findname.focus();
    return;
  }
  if (editjob.findvalue.value == "")
  {
    alert("Metadata value cannot be blank.");
    editjob.findvalue.focus();
    return;
  }
  SpecOp("findop", "Add", "find_"+n);
}
//-->
</script>