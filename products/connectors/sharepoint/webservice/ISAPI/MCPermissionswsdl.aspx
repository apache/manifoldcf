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
<wsdl:definitions xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:tm="http://microsoft.com/wsdl/mime/textMatching/" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/" xmlns:mime="http://schemas.xmlsoap.org/wsdl/mime/" xmlns:tns="http://microsoft.com/sharepoint/webpartpages/" xmlns:s="http://www.w3.org/2001/XMLSchema" xmlns:soap12="http://schemas.xmlsoap.org/wsdl/soap12/" xmlns:http="http://schemas.xmlsoap.org/wsdl/http/" targetNamespace="http://microsoft.com/sharepoint/webpartpages/" xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/">
  <wsdl:types>
    <s:schema elementFormDefault="qualified" targetNamespace="http://microsoft.com/sharepoint/webpartpages/">
      <s:element name="GetPermissionCollection">
        <s:complexType>
          <s:sequence>
            <s:element minOccurs="0" maxOccurs="1" name="objectName" type="s:string" />
            <s:element minOccurs="0" maxOccurs="1" name="objectType" type="s:string" />
          </s:sequence>
        </s:complexType>
      </s:element>
      <s:element name="GetPermissionCollectionResponse">
        <s:complexType>
          <s:sequence>
            <s:element minOccurs="0" maxOccurs="1" name="GetPermissionCollectionResult">
              <s:complexType mixed="true">
                <s:sequence>
                  <s:any />
                </s:sequence>
              </s:complexType>
            </s:element>
          </s:sequence>
        </s:complexType>
      </s:element>
    </s:schema>
  </wsdl:types>
  <wsdl:message name="GetPermissionCollectionSoapIn">
    <wsdl:part name="parameters" element="tns:GetPermissionCollection" />
  </wsdl:message>
  <wsdl:message name="GetPermissionCollectionSoapOut">
    <wsdl:part name="parameters" element="tns:GetPermissionCollectionResponse" />
  </wsdl:message>
  <wsdl:portType name="PermissionsSoap">
    <wsdl:operation name="GetPermissionCollection">
      <wsdl:documentation xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/">Returns the collection of permissions for a site, list, or list item.</wsdl:documentation>
      <wsdl:input message="tns:GetPermissionCollectionSoapIn" />
      <wsdl:output message="tns:GetPermissionCollectionSoapOut" />
    </wsdl:operation>
  </wsdl:portType>
  <wsdl:binding name="PermissionsSoap" type="tns:PermissionsSoap">
    <soap:binding transport="http://schemas.xmlsoap.org/soap/http" />
    <wsdl:operation name="GetPermissionCollection">
      <soap:operation soapAction="http://microsoft.com/sharepoint/webpartpages/GetPermissionCollection" style="document" />
      <wsdl:input>
        <soap:body use="literal" />
      </wsdl:input>
      <wsdl:output>
        <soap:body use="literal" />
      </wsdl:output>
    </wsdl:operation>
  </wsdl:binding>
  <wsdl:binding name="PermissionsSoap12" type="tns:PermissionsSoap">
    <soap12:binding transport="http://schemas.xmlsoap.org/soap/http" />
    <wsdl:operation name="GetPermissionCollection">
      <soap12:operation soapAction="http://microsoft.com/sharepoint/webpartpages/GetPermissionCollection" style="document" />
      <wsdl:input>
        <soap12:body use="literal" />
      </wsdl:input>
      <wsdl:output>
        <soap12:body use="literal" />
      </wsdl:output>
    </wsdl:operation>
  </wsdl:binding>
  <wsdl:service name="Permissions">
    <wsdl:port name="PermissionsSoap" binding="tns:PermissionsSoap">
      <soap:address location=<% SPHttpUtility.AddQuote(SPHttpUtility.HtmlEncode(SPWeb.OriginalBaseUrl(Request)),Response.Output); %> />
    </wsdl:port>
    <wsdl:port name="PermissionsSoap12" binding="tns:PermissionsSoap12">
      <soap12:address location=<% SPHttpUtility.AddQuote(SPHttpUtility.HtmlEncode(SPWeb.OriginalBaseUrl(Request)),Response.Output); %> />
    </wsdl:port>
  </wsdl:service>
</wsdl:definitions>