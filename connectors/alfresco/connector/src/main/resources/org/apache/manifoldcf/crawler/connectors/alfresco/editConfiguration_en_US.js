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
    alert("The username must be not null");
    SelectTab("Server");
    editconnection.username.focus();
    return false;
  }
  if (editconnection.password.value == "")
  {
    alert("The password must be not null");
    SelectTab("Server");
    editconnection.password.focus();
    return false;
  }
  if (editconnection.server.value =="")
  {
    alert("Server name must be not null");
    SelectTab("Server");
    editconnection.server.focus();
    return false;
  }
  else if(!editconnection.server.value.indexOf('/')==-1)
  {
    alert("Server name can't contain the character '/'");
    SelectTab("Server");
    editconnection.server.focus();
    return false;
  }
  if (editconnection.port.value == "")
  {
    alert("The port must be not null");
    SelectTab("Server");
    editconnection.port.focus();
    return false;
  }
  else if (!isInteger(editconnection.port.value))
  {
    alert("The server port must be a valid integer");
    SelectTab("Server");
    editconnection.port.focus();
    return false;
  }
  if(editconnection.path.value == "")
  {
    alert("Path must be not null");
    SelectTab("Server");
    editconnection.path.focus();
    return false;
  }
  return true;
}
// -->
</script>