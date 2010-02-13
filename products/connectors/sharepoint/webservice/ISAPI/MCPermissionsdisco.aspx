<% // Licensed to the Apache Software Foundation (ASF) under one or more      %>
<% // contributor license agreements. See the NOTICE file distributed with    %>
<% // this work for additional information regarding copyright ownership.     %>
<% // The ASF licenses this file to You under the Apache License, Version 2.0 %>
<% // (the "License"); you may not use this file except in compliance with    %>
<% // the License. You may obtain a copy of the License at                    %>
<% //                                                                         %>
<% // http://www.apache.org/licenses/LICENSE-2.0                              %>
<% //                                                                         %>
<% // Unless required by applicable law or agreed to in writing, software     %>
<% // distributed under the License is distributed on an "AS IS" BASIS,       %>
<% // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.%>
<% // See the License for the specific language governing permissions and     %>
<% // limitations under the License.                                          %>

<%@ Page Language="C#" Inherits="System.Web.UI.Page" %> 
<%@ Assembly Name="Microsoft.SharePoint, Version=11.0.0.0, Culture=neutral, PublicKeyToken=71e9bce111e9429c" %> 
<%@ Import Namespace="Microsoft.SharePoint.Utilities" %> 
<%@ Import Namespace="Microsoft.SharePoint" %> 
<% Response.ContentType = "text/xml"; %>
<discovery xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns="http://schemas.xmlsoap.org/disco/">
  <contractRef ref=<% SPHttpUtility.AddQuote(SPHttpUtility.HtmlEncode(SPWeb.OriginalBaseUrl(Request) + "?wsdl"),Response.Output); %>
   docRef=<% SPHttpUtility.AddQuote(SPHttpUtility.HtmlEncode(SPWeb.OriginalBaseUrl(Request)),Response.Output); %>
   xmlns="http://schemas.xmlsoap.org/disco/scl/" />
  <soap address=<% SPHttpUtility.AddQuote(SPHttpUtility.HtmlEncode(SPWeb.OriginalBaseUrl(Request)),Response.Output); %>
    xmlns:q1="http://microsoft.com/sharepoint/webpartpages/" binding="q1:PermissionsSoap" xmlns="http://schemas.xmlsoap.org/disco/soap/" />
  <soap address=<% SPHttpUtility.AddQuote(SPHttpUtility.HtmlEncode(SPWeb.OriginalBaseUrl(Request)),Response.Output); %> 
    xmlns:q2="http://microsoft.com/sharepoint/webpartpages/" binding="q2:PermissionsSoap12" xmlns="http://schemas.xmlsoap.org/disco/soap/" />
</discovery>