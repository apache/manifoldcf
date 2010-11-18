// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Read the file "readme.txt" in this directory for a description of what this
// web service does and why it is needed.
using System;
using System.Data;
using System.Web;
using System.Collections;
using System.Web.Services;
using System.Web.Services.Protocols;
using System.ComponentModel;
using System.Security.Permissions;
using System.Xml;
using System.Diagnostics;
using Microsoft.SharePoint;
using System.Net;
using System.Security.Cryptography.X509Certificates;
using System.Net.Security;

namespace MetaCarta.SharePoint.SoapServer
{
    [WebService(Namespace = "http://microsoft.com/sharepoint/webpartpages/"),
     WebServiceBinding(ConformsTo = WsiProfiles.BasicProfile1_1),
     PermissionSet(SecurityAction.InheritanceDemand, Name = "FullTrust"),
     PermissionSet(SecurityAction.LinkDemand, Name = "FullTrust")]
    public class Permissions : System.Web.Services.WebService
    {
        #region Private Fields

        private readonly string itemType = "item";

        #endregion

        #region Public Methods

        [WebMethod(Description = "Returns the collection of permissions for a site, list, or list item.")]
        public XmlNode GetPermissionCollection(string objectName, string objectType)
        {
            XmlNode retVal = null;

            try
            {
                // Only handle requests for "item".  Send all other requests to the SharePoint web service.
                if (objectType.ToLower().Equals(itemType))
                {
                    retVal = GetItemPermissions(objectName);
                }
                else
                {
                    ServicePointManager.ServerCertificateValidationCallback +=
                        new RemoteCertificateValidationCallback(ValidateCertificate);

                    using (SPPermissionsService.Permissions service = new SPPermissionsService.Permissions())
                    {
                        service.Url = SPContext.Current.Web.Url + "/_vti_bin/Permissions.asmx";
                        service.Credentials = System.Net.CredentialCache.DefaultCredentials;

                        retVal = service.GetPermissionCollection(objectName, objectType);
                    }
                }
            }
            catch (SoapException soapEx)
            {
                throw soapEx;
            }
            catch (Exception ex)
            {
                EventLog.WriteEntry("MCPermissions.asmx", ex.Message);
                throw RaiseException(ex.Message, "1000", ex.Source);
            }

            return retVal;
        }

        #endregion

        #region Private Methods

        /// <summary>
        /// Given the name of a list item, return an XML fragment describing the set of permissions
        /// for the specified list item.
        /// </summary>
        /// <param name="itemName">A string containing the name of a list item</param>
        /// <returns>An XML fragment</returns>
        private XmlNode GetItemPermissions(string itemName)
        {
            XmlNode retVal = null;

            if (string.IsNullOrEmpty(itemName))
                throw RaiseException("Parameter 'objectName' cannot be null or empty.", "2000", "GetPermissionCollection");

            using (SPWeb site = SPContext.Current.Web)
            {
                SPListItem item = site.GetListItem(itemName);

                if (item.RoleAssignments.Count > 0)
                {
                    XmlDocument doc = new XmlDocument();
                    retVal = doc.CreateElement("GetPermissionCollection", 
                        "http://schemas.microsoft.com/sharepoint/soap/directory/");
                    XmlNode permissionsNode = doc.CreateElement("Permissions");

                    // A list item can have one or more role assignments.  Each role assignment
                    // represents a member (user or group) with one or more permissions.  
                    // The code below creates a Permission node for every member-permission assignment.
                    foreach (SPRoleAssignment assignment in item.RoleAssignments)
                    {
                        SPPrincipal member = assignment.Member;

                        foreach (SPRoleDefinition roleDefinition in assignment.RoleDefinitionBindings)
                        {
                            XmlNode permissionNode = CreatePermissionNode(doc, member, roleDefinition);
                            permissionsNode.AppendChild(permissionNode);
                        }

                        retVal.AppendChild(permissionsNode);
                    }
                }
            }

            return retVal;
        }

        private XmlNode CreatePermissionNode(XmlDocument doc, SPPrincipal member, SPRoleDefinition roleDefinition)
        {
            XmlNode retVal = doc.CreateElement("Permission");

            XmlAttribute memberIdAttribute = doc.CreateAttribute("MemberID");
            memberIdAttribute.Value = member.ID.ToString();
            retVal.Attributes.Append(memberIdAttribute);

            XmlAttribute maskAttribute = doc.CreateAttribute("Mask");
            int mask = (int)roleDefinition.BasePermissions;
            maskAttribute.Value = mask.ToString();
            retVal.Attributes.Append(maskAttribute);

            XmlAttribute memberIsUserAttribute = doc.CreateAttribute("MemberIsUser");
            memberIsUserAttribute.Value = member is SPUser ? "True" : "False";
            retVal.Attributes.Append(memberIsUserAttribute);

            XmlAttribute memberGlobalAttribute = doc.CreateAttribute("MemberGlobal");
            string isGlobalValue = "True";
            if (member is SPUser)
            {
                SPUser user = member as SPUser;
                if (!user.IsDomainGroup)
                    isGlobalValue = "False";
            }

            memberGlobalAttribute.Value = isGlobalValue;
            retVal.Attributes.Append(memberGlobalAttribute);

            if (member is SPUser)
            {
                SPUser user = member as SPUser;
                XmlAttribute userLoginAttribute = doc.CreateAttribute("UserLogin");
                userLoginAttribute.Value = user.LoginName;
                retVal.Attributes.Append(userLoginAttribute);
            }
            else
            {
                XmlAttribute groupNameAttribute = doc.CreateAttribute("GroupName");
                groupNameAttribute.Value = member.Name;
                retVal.Attributes.Append(groupNameAttribute);
            }
            return retVal;
        }

        private SoapException RaiseException(string errorMessage, string errorNumber, string errorSource)
        {
            SoapException retVal = null;

            XmlDocument doc = new XmlDocument();
            XmlNode root = doc.CreateNode(XmlNodeType.Element, SoapException.DetailElementName.Name,
                SoapException.DetailElementName.Namespace);

            XmlNode errorNode = doc.CreateNode(XmlNodeType.Element, "Error", 
                SoapException.DetailElementName.Namespace);
            XmlNode errorNumberNode = doc.CreateNode(XmlNodeType.Element, "ErrorNumber", 
                SoapException.DetailElementName.Namespace);
            errorNumberNode.InnerText = errorNumber;
            XmlNode errorMessageNode = doc.CreateNode(XmlNodeType.Element, "ErrorMessage", 
                SoapException.DetailElementName.Namespace);
            errorMessageNode.InnerText = errorMessage;
            XmlNode errorSourceNode = doc.CreateNode(XmlNodeType.Element, "ErrorSource", 
                SoapException.DetailElementName.Namespace);
            errorSourceNode.InnerText = errorSource;

            errorNode.AppendChild(errorNumberNode);
            errorNode.AppendChild(errorMessageNode);
            errorNode.AppendChild(errorSourceNode);
            root.AppendChild(errorNode);

            retVal = new SoapException(errorMessage, SoapException.ClientFaultCode, Context.Request.Url.AbsoluteUri, root);

            return retVal;
        }

        public static bool ValidateCertificate(object sender, X509Certificate certificate, X509Chain chain, SslPolicyErrors sslPolicyErrors)
        {
            return true;
        }

        #endregion
    }
}