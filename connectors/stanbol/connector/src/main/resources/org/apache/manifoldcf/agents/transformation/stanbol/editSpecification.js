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
function s${SEQNUM}_checkSpecification()
{
  return true;
}


function s${SEQNUM}_addFieldMapping()
{
  if (editjob.s${SEQNUM}_fieldmapping_source.value == "")
  {
    alert("$Encoder.bodyEscape($ResourceBundle.getString('StanbolEnhancer.NoFieldNameSpecified'))");
    editjob.s${SEQNUM}_fieldmapping_source.focus();
    return;
  }
  editjob.s${SEQNUM}_fieldmapping_op.value="Add";
  postFormSetAnchor("s${SEQNUM}_fieldmapping");
}

function s${SEQNUM}_deleteFieldMapping(i)
{
  // Set the operation
  eval("editjob.s${SEQNUM}_fieldmapping_op_"+i+".value=\"Delete\"");
  // Submit
  if (editjob.s${SEQNUM}_fieldmapping_count.value==i)
    postFormSetAnchor("s${SEQNUM}_fieldmapping");
  else
    postFormSetAnchor("s${SEQNUM}_fieldmapping_"+i)
  // Undo, so we won't get two deletes next time
  eval("editjob.s${SEQNUM}_fieldmapping_op_"+i+".value=\"Continue\"");
}


//ldpath prefix mappings
function s${SEQNUM}_addPrefixMapping()
{
  if (editjob.s${SEQNUM}_prefixmapping_source.value == "")
  {
    alert("$Encoder.bodyEscape($ResourceBundle.getString('StanbolEnhancer.NoFieldNameSpecified'))");
    editjob.s${SEQNUM}_prefixmapping_source.focus();
    return;
  }
  editjob.s${SEQNUM}_prefixmapping_op.value="Add";
  postFormSetAnchor("s${SEQNUM}_prefixmapping");
}

function s${SEQNUM}_deletePrefixMapping(i)
{
  // Set the operation
  eval("editjob.s${SEQNUM}_prefixmapping_op_"+i+".value=\"Delete\"");
  // Submit
  if (editjob.s${SEQNUM}_prefixmapping_count.value==i)
    postFormSetAnchor("s${SEQNUM}_prefixmapping");
  else
    postFormSetAnchor("s${SEQNUM}_prefixmapping_"+i)
  // Undo, so we won't get two deletes next time
  eval("editjob.s${SEQNUM}_prefixmapping_op_"+i+".value=\"Continue\"");
}

//ldpath field mappings
function s${SEQNUM}_addLdpathFieldMapping()
{
  if (editjob.s${SEQNUM}_ldpathfieldmapping_source.value == "")
  {
    alert("$Encoder.bodyEscape($ResourceBundle.getString('StanbolEnhancer.NoFieldNameSpecified'))");
    editjob.s${SEQNUM}_ldpathfieldmapping_source.focus();
    return;
  }
  editjob.s${SEQNUM}_ldpathfieldmapping_op.value="Add";
  postFormSetAnchor("s${SEQNUM}_ldpathfieldmapping");
}
//CHANGE BELOW METHOD
function s${SEQNUM}_deleteLdpathFieldMapping(i)
{
  // Set the operation
  eval("editjob.s${SEQNUM}_ldpathfieldmapping_op_"+i+".value=\"Delete\"");
  // Submit
  if (editjob.s${SEQNUM}_ldpathfieldmapping_count.value==i)
    postFormSetAnchor("s${SEQNUM}_ldpathfieldmapping");
  else
    postFormSetAnchor("s${SEQNUM}_ldpathfieldmapping_"+i)
  // Undo, so we won't get two deletes next time
  eval("editjob.s${SEQNUM}_ldpathfieldhmapping_op_"+i+".value=\"Continue\"");
}


//document final field mappings
function s${SEQNUM}_addDocumentFieldMapping()
{
  if (editjob.s${SEQNUM}_docfieldmapping_source.value == "")
  {
    alert("$Encoder.bodyEscape($ResourceBundle.getString('StanbolEnhancer.NoFieldNameSpecified'))");
    editjob.s${SEQNUM}_docfieldmapping_source.focus();
    return;
  }
  editjob.s${SEQNUM}_docfieldmapping_op.value="Add";
  postFormSetAnchor("s${SEQNUM}_docfieldmapping");
}
//CHANGE BELOW METHOD
function s${SEQNUM}_deleteDocumentFieldMapping(i)
{
  // Set the operation
  eval("editjob.s${SEQNUM}_docfieldmapping_op_"+i+".value=\"Delete\"");
  // Submit
  if (editjob.s${SEQNUM}_docfieldmapping_count.value==i)
    postFormSetAnchor("s${SEQNUM}_docfieldmapping");
  else
    postFormSetAnchor("s${SEQNUM}_docfieldmapping_"+i)
  // Undo, so we won't get two deletes next time
  eval("editjob.s${SEQNUM}_docfieldmapping_op_"+i+".value=\"Continue\"");
}

//-->
</script>
