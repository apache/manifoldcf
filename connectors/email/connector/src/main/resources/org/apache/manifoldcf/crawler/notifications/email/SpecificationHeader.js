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

function s${SeqNum}_checkSpecificationForSave()
{
  if (s${SeqNum}_checkMessageTabForSave() == false)
    return false;
  return true;
}

function s${SeqNum}_SpecOp(n, opValue, anchorvalue)
{
  eval("editjob."+n+".value = \""+opValue+"\"");
  postFormSetAnchor(anchorvalue);
}

function s${SeqNum}_checkMessageTabForSave()
{
  if (editjob.s${SeqNum}_finished_subject.value != "" || editjob.s${SeqNum}_finished_body.value != "")
  {
    if (editjob.s${SeqNum}_finished_to.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.ToFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_finished_to.focus();
      return false;
    }
    if (editjob.s${SeqNum}_finished_from.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.FromFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_finished_from.focus();
      return false;
    }
  }

  if (editjob.s${SeqNum}_erroraborted_subject.value != "" || editjob.s${SeqNum}_erroraborted_body.value != "")
  {
    if (editjob.s${SeqNum}_erroraborted_to.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.ToFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_erroraborted_to.focus();
      return false;
    }
    if (editjob.s${SeqNum}_erroraborted_from.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.FromFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_erroraborted_from.focus();
      return false;
    }
  }

  if (editjob.s${SeqNum}_manualaborted_subject.value != "" || editjob.s${SeqNum}_manualaborted_body.value != "")
  {
    if (editjob.s${SeqNum}_manualaborted_to.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.ToFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_manualaborted_to.focus();
      return false;
    }
    if (editjob.s${SeqNum}_manualaborted_from.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.FromFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_manualaborted_from.focus();
      return false;
    }
  }
  
  if (editjob.s${SeqNum}_manualpaused_subject.value != "" || editjob.s${SeqNum}_manualpaused_body.value != "")
  {
    if (editjob.s${SeqNum}_manualpaused_to.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.ToFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_manualpaused_to.focus();
      return false;
    }
    if (editjob.s${SeqNum}_manualpaused_from.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.FromFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_manualpaused_from.focus();
      return false;
    }
  }

  if (editjob.s${SeqNum}_schedulepaused_subject.value != "" || editjob.s${SeqNum}_schedulepaused_body.value != "")
  {
    if (editjob.s${SeqNum}_schedulepaused_to.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.ToFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_schedulepaused_to.focus();
      return false;
    }
    if (editjob.s${SeqNum}_schedulepaused_from.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.FromFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_schedulepaused_from.focus();
      return false;
    }
  }

  if (editjob.s${SeqNum}_restarted_subject.value != "" || editjob.s${SeqNum}_restarted_body.value != "")
  {
    if (editjob.s${SeqNum}_restarted_to.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.ToFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_restarted_to.focus();
      return false;
    }
    if (editjob.s${SeqNum}_restarted_from.value == "")
    {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('EmailConnector.FromFieldCannotBeBlank'))");
      SelectSequencedTab("$Encoder.attributeJavascriptEscape($ResourceBundle.getString('EmailConnector.Message'))",${SeqNum})
      editjob.s${SeqNum}_restarted_from.focus();
      return false;
    }
  }

  return true;
}

//-->
</script>
