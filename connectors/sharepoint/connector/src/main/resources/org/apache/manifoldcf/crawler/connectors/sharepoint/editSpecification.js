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

function s${SeqNum}_SpecRuleAddPath(anchorvalue)
{
  if (editjob.s${SeqNum}_spectype.value=="")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectATypeFirst'))");
    editjob.s${SeqNum}_spectype.focus();
  }
  else if (editjob.s${SeqNum}_specflavor.value=="")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectAnActionFirst'))");
    editjob.s${SeqNum}_specflavor.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_specop","Add",anchorvalue);
}
  
function s${SeqNum}_SpecPathReset(anchorvalue)
{
  SpecOp("specpathop","Reset",anchorvalue);
}
  
function s${SeqNum}_SpecPathAppendSite(anchorvalue)
{
  if (editjob.s${SeqNum}_specsite.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectASiteFirst'))");
    editjob.s${SeqNum}_specsite.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_specpathop","AppendSite",anchorvalue);
}

function s${SeqNum}_SpecPathAppendLibrary(anchorvalue)
{
  if (editjob.s${SeqNum}_speclibrary.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectALibraryFirst'))");
    editjob.s${SeqNum}_speclibrary.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_specpathop","AppendLibrary",anchorvalue);
}

function s${SeqNum}_SpecPathAppendList(anchorvalue)
{
  if (editjob.s${SeqNum}_speclist.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectAListFirst'))");
    editjob.s${SeqNum}_speclist.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_specpathop","AppendList",anchorvalue);
}

function s${SeqNum}_SpecPathAppendText(anchorvalue)
{
  if (editjob.s${SeqNum}_specmatch.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseProvideMatchTextFirst'))");
    editjob.s${SeqNum}_specmatch.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_specpathop","AppendText",anchorvalue);
}

function s${SeqNum}_SpecPathRemove(anchorvalue)
{
  s${SeqNum}_SpecOp("s${SeqNum}_specpathop","Remove",anchorvalue);
}

function s${SeqNum}_MetaRuleAddPath(anchorvalue)
{
  if (editjob.s${SeqNum}_metaflavor.value=="")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectAnActionFirst'))");
    editjob.s${SeqNum}_metaflavor.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_metaop","Add",anchorvalue);
}

function s${SeqNum}_MetaPathReset(anchorvalue)
{
  s${SeqNum}_SpecOp("s${SeqNum}_metapathop","Reset",anchorvalue);
}
  
function s${SeqNum}_MetaPathAppendSite(anchorvalue)
{
  if (editjob.s${SeqNum}_metasite.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectASiteFirst'))");
    editjob.s${SeqNum}_metasite.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_metapathop","AppendSite",anchorvalue);
}

function s${SeqNum}_MetaPathAppendLibrary(anchorvalue)
{
  if (editjob.s${SeqNum}_metalibrary.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectALibraryFirst'))");
    editjob.s${SeqNum}_metalibrary.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_metapathop","AppendLibrary",anchorvalue);
}

function s${SeqNum}_MetaPathAppendList(anchorvalue)
{
  if (editjob.s${SeqNum}_metalist.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSelectAListFirst'))");
    editjob.s${SeqNum}_metalist.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_metapathop","AppendList",anchorvalue);
}

function s${SeqNum}_MetaPathAppendText(anchorvalue)
{
  if (editjob.s${SeqNum}_metamatch.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseProvideMatchTextFirst'))");
    editjob.s${SeqNum}_metamatch.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_metapathop","AppendText",anchorvalue);
}

function s${SeqNum}_MetaPathRemove(anchorvalue)
{
  s${SeqNum}_SpecOp("s${SeqNum}_metapathop","Remove",anchorvalue);
}

function s${SeqNum}_SpecAddAccessToken(anchorvalue)
{
  if (editjob.s${SeqNum}_spectoken.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.AccessTokenCannotBeNull'))");
    editjob.s${SeqNum}_spectoken.focus();
  }
  else
    s${SeqNum}_SpecOp("s${SeqNum}_accessop","Add",anchorvalue);
}

function s${SeqNum}_SpecAddMapping(anchorvalue)
{
  if (editjob.s${SeqNum}_specmatch.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.MatchStringCannotBeEmpty'))");
    editjob.s${SeqNum}_specmatch.focus();
    return;
  }
  if (!isRegularExpression(editjob.s${SeqNum}_specmatch.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.MatchStringMustBeValidRegularExpression'))");
    editjob.s${SeqNum}_specmatch.focus();
    return;
  }
  s${SeqNum}_SpecOp("s${SeqNum}_specmappingop","Add",anchorvalue);
}

function s${SeqNum}_SpecOp(n, opValue, anchorvalue)
{
  eval("editjob."+n+".value = \""+opValue+"\"");
  postFormSetAnchor(anchorvalue);
}

//-->
</script>

