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
        function checkConfig() {
            return true;
        }

function checkConfigForSave() {
    // if (editconnection.host) {
    //     if (editconnection.host.value == "") {
    //         SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('MongodbConnector.Parameters'))");
    //         alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('MongodbConnector.PleaseSupplyValidMongodbHostname'))");
    //         editconnection.host.focus();
    //         return false;
    //     }
    // }
    if(editconnection.host && editconnection.host.value.indexOf('/')!=-1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('MongodbConnector.PleaseSupplyValidMongodbHostname'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('MongodbConnector.Parameters'))");
        editconnection.host.focus();
        return false;
    }
    // if (editconnection.port) {
    //     if (editconnection.port.value == "") {
    //         alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('MongodbConnector.PleaseSupplyValidPortNumber'))");
    //         SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('MongodbConnector.Parameters'))");
    //         editconnection.port.focus();
    //         return false;
    //     }
    // }
    // if (editconnection.username) {
    //     if (editconnection.username.value == "") {
    //         alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('MongodbConnector.PleaseSupplyValidUserName'))");
    //         editconnection.username.focus();
    //         return false;
    //     }
    // }
    // if (editconnection.password) {
    //     if (editconnection.password.value == "") {
    //         alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('MongodbConnector.PleaseSupplyValidPassword'))");
    //         editconnection.password.focus();
    //         return false;
    //     }
    // }
    if (editconnection.database) {
        if (editconnection.database.value == "") {
            alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('MongodbConnector.PleaseSupplyValidDatabase'))");
            editconnection.database.focus();
            return false;
        }
    }
    if (editconnection.collection) {
        if (editconnection.collection.value == "") {
            alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('MongodbConnector.PleaseSupplyValidCollection'))");
            editconnection.collection.focus();
            return false;
        }
    }
    return true;
}
//-->
</script>
