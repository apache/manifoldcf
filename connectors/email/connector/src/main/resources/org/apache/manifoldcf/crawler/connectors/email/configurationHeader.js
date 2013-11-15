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
function checkConfigForSave()
    {
      if (editconnection.server.value == "")
      {
        alert("Enter a email server url");
        SelectTab("Server");
        editconnection.server.focus();
        return false;
      }
      return true;
    }

    function addProperty()
        {
          postFormSetAnchor("property");    //Repost the form and send the browser to property anchor
        }

    function SpecOp(n, opValue, anchorvalue)
        {
          eval("editconnection."+n+".value = \""+opValue+"\"");
          postFormSetAnchor(anchorvalue);
        }



    function FindDelete(n)
        {
          SpecOp("findop_"+n, "Delete", "find_"+n);
        }

    function FindAdd(n)
        {
          if (editconnection.findname.value == "")
          {
            alert("Please select a metadata name first.");
            editconnection.findname.focus();
            return;
          }
          if (editconnection.findvalue.value == "")
          {
            alert("value cannot be blank.");
            editconnection.findvalue.focus();
            return;
          }
          SpecOp("findop", "Add", "find_"+n);
        }
//-->
</script>