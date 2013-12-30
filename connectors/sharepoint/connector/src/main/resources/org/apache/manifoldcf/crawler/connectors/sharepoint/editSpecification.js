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
  // Does nothing right now.
  return true;
}

function SpecRuleAddPath(anchorvalue)
{
  if (editjob.spectype.value=="")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectATypeFirst'))");
    editjob.spectype.focus();
  }
  else if (editjob.specflavor.value=="")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectAnActionFirst'))");
    editjob.specflavor.focus();
  }
  else
    SpecOp("specop","Add",anchorvalue);
}
  
function SpecPathReset(anchorvalue)
{
  SpecOp("specpathop","Reset",anchorvalue);
}
  
function SpecPathAppendSite(anchorvalue)
{
  if (editjob.specsite.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectASiteFirst'))");
    editjob.specsite.focus();
  }
  else
    SpecOp("specpathop","AppendSite",anchorvalue);
}

function SpecPathAppendLibrary(anchorvalue)
{
  if (editjob.speclibrary.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectALibraryFirst'))");
    editjob.speclibrary.focus();
  }
  else
    SpecOp("specpathop","AppendLibrary",anchorvalue);
}

function SpecPathAppendList(anchorvalue)
{
  if (editjob.speclist.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectAListFirst'))");
    editjob.speclist.focus();
  }
  else
    SpecOp("specpathop","AppendList",anchorvalue);
}

function SpecPathAppendText(anchorvalue)
{
  if (editjob.specmatch.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseProvideMatchTextFirst'))");
    editjob.specmatch.focus();
  }
  else
    SpecOp("specpathop","AppendText",anchorvalue);
}

function SpecPathRemove(anchorvalue)
{
  SpecOp("specpathop","Remove",anchorvalue);
}

function MetaRuleAddPath(anchorvalue)
{
  if (editjob.metaflavor.value=="")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectAnActionFirst'))");
    editjob.metaflavor.focus();
  }
  else
    SpecOp("metaop","Add",anchorvalue);
}

function MetaPathReset(anchorvalue)
{
  SpecOp("metapathop","Reset",anchorvalue);
}
  
function MetaPathAppendSite(anchorvalue)
{
  if (editjob.metasite.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectASiteFirst'))");
    editjob.metasite.focus();
  }
  else
    SpecOp("metapathop","AppendSite",anchorvalue);
}

function MetaPathAppendLibrary(anchorvalue)
{
  if (editjob.metalibrary.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectALibraryFirst'))");
    editjob.metalibrary.focus();
  }
  else
    SpecOp("metapathop","AppendLibrary",anchorvalue);
}

function MetaPathAppendList(anchorvalue)
{
  if (editjob.metalist.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectAListFirst'))");
    editjob.metalist.focus();
  }
  else
    SpecOp("metapathop","AppendList",anchorvalue);
}

function MetaPathAppendText(anchorvalue)
{
  if (editjob.metamatch.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseProvideMatchTextFirst'))");
    editjob.metamatch.focus();
  }
  else
    SpecOp("metapathop","AppendText",anchorvalue);
}

function MetaPathRemove(anchorvalue)
{
  SpecOp("metapathop","Remove",anchorvalue);
}

function SpecAddAccessToken(anchorvalue)
{
  if (editjob.spectoken.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.AccessTokenCannotBeNull'))");
    editjob.spectoken.focus();
  }
  else
    SpecOp("accessop","Add",anchorvalue);
}

function SpecAddMapping(anchorvalue)
{
  if (editjob.specmatch.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.MatchStringCannotBeEmpty'))");
    editjob.specmatch.focus();
    return;
  }
  if (!isRegularExpression(editjob.specmatch.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.MatchStringMustBeValidRegularExpression'))");
    editjob.specmatch.focus();
    return;
  }
  SpecOp("specmappingop","Add",anchorvalue);
}

function SpecOp(n, opValue, anchorvalue)
{
  eval("editjob."+n+".value = \""+opValue+"\"");
  postFormSetAnchor(anchorvalue);
}

//-->
</script>

