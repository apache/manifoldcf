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
function checkConfig()
{
  return true;
}
 
function checkConfigForSave()
{
  if (editconnection.username.value == "")
  {
    alert("ユーザ名を入力してください");
    SelectTab("サーバ");
    editconnection.username.focus();
    return false;
  }
  if (editconnection.password.value == "")
  {
    alert("パスワードを入力してください");
    SelectTab("サーバ");
    editconnection.password.focus();
    return false;
  }
  if (editconnection.binding.value == "")
  {
    alert("バイディングを入力してください");
    SelectTab("サーバ");
    editconnection.binding.focus();
    return false;
  }
  if (editconnection.server.value ==""){
    alert("サーバ名を入力してください");
    SelectTab("サーバ");
    editconnection.server.focus();
    return false;
  }
  if(!editconnection.server.value.indexOf('/')==-1) {
    alert("サーバ名には文字「/」を含めないでください");
    SelectTab("サーバ");
    editconnection.server.focus();
    return false;
  }
  if (editconnection.port.value == "")
  {
    alert("ポート番号を入力してください");
    SelectTab("サーバ");
    editconnection.port.focus();
    return false;
  }
  if (!isInteger(editconnection.port.value)){
    alert("サーバポートには整数を入力してください");
    SelectTab("サーバ");
    editconnection.port.focus();
    return false;
  }
  if(editconnection.path.value == ""){
    alert("パスを入力してください");
    SelectTab("サーバ");
    editconnection.path.focus();
    return false;
  }
  return true;
}
//-->
</script>
