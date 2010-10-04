/* $Id: FPSEPublish.java 988245 2010-08-23 18:39:35Z kwright $ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
* 
* http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.manifoldcf.crawler.connectors.sharepoint;

import org.apache.manifoldcf.core.interfaces.*;

import java.io.*;
import java.net.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.auth.*;

import org.apache.axis.EngineConfiguration;
import org.apache.axis.configuration.FileProvider;

import com.microsoft.schemas.sharepoint.soap.directory.*;

/**
* This is the class that contains various methods that will be invoked for testing the sharepoint connector.
* Add, modify, and delete methods are provided.
*/
public class FPSEPublish
{
        public static final String _rcsid = "@(#)$Id: FPSEPublish.java 988245 2010-08-23 18:39:35Z kwright $";

        protected String userName       = null;
        protected String passWord       = null;
        protected String domainName     = null;
        protected String serverProtocol = null;
        protected String serverName     = null;
        protected int    serverPort     = 80;
        protected String serverLocation = null;
        protected EngineConfiguration configuration  = null;

        private String webUrl = null;

        private String fileUrl = null;

        private static String currentHost = null;
        static {
                // Find the current host name
                try {
                        java.net.InetAddress addr = java.net.InetAddress.getLocalHost();

                        // Get hostname
                        currentHost = addr.getHostName();
                } catch (UnknownHostException e) {
                }
        }

        public FPSEPublish(String serverProtocol, String serverName, int serverPort, String serverLocation,
                String userName, String password, String domainName)
                throws ACFException
        {
                this.serverProtocol = serverProtocol;
                this.serverName = serverName;
                this.serverPort = serverPort;
                this.serverLocation = serverLocation;
                if (serverLocation.length() > 0 && !serverLocation.startsWith("/"))
                        serverLocation = "/" + serverLocation;
                this.userName = userName;
                this.passWord = password;
                this.domainName = domainName;
		String fileName = ACF.getProperty("org.apache.manifoldcf.sharepoint.wsddpath");
		if (fileName == null)
		    throw new ACFException("Missing org.apache.manifoldcf.sharepoint.wsddpath property!",ACFException.SETUP_ERROR);
                this.configuration = new FileProvider(fileName);
        }

        public void close()
                throws ACFException
        {
        }

        private String getAuthority()
        {
                return serverProtocol + "://" + serverName + ":" + serverPort ;
        }
        
        private String getBaseUrl()
        {
                return getAuthority() + serverLocation;
        }

        protected String makeFullURL(String siteURL)
        {
                // siteURL must always start with a slash, if not null.
                return getBaseUrl() + siteURL;
        }

        protected void UrlToWebUrl(String uri)
                throws ACFException
        {
                String actualURL = getBaseUrl() + "/" + uri;
                try
                {
                        URL myUri = new URL(actualURL);

                        String postBody = "method=url+to+web+url&url=" + myUri.getPath()
                                        + "&flags=0";
                        String response = SendRequest( getBaseUrl()
                                        + "/_vti_bin/shtml.dll/_vti_rpc", postBody);

                        webUrl = GetReturnValue(response, "webUrl").replaceAll(" ","%20");
                        if (webUrl == null)
                                throw new ACFException("Null web url for document described by '"+uri+"'");
                        fileUrl = GetReturnValue(response, "fileUrl");
                        if (fileUrl == null)
                                throw new ACFException("Null file url for document described by '"+uri+"'");
                        // System.out.println("File url = "+fileUrl);
                }
                catch (MalformedURLException e)
                {
                        throw new ACFException("Malformed URL: '"+actualURL+"'",e);
                }
        }

        public void removeDocument(String url)
                throws ACFException
        {
            try
            {
                UrlToWebUrl(url);

                String postBody = "method=remove+documents:6.0.n.nnn&service_name=/&url_list=[" + URLEncoder.encode(fileUrl, "UTF-8") + "]\n";
                byte[] postBytes = postBody.getBytes();

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                try
                {
                        stream.write(postBody.getBytes(), 0, postBody.getBytes().length);
                }
                finally
                {
                        stream.close();
                }
                SendRequest( getBaseUrl()       + "/_vti_bin/_vti_aut/author.dll", stream.toString());
            }
            catch (UnsupportedEncodingException e)
            {
                throw new ACFException("Unsupported encoding",e);
            }
            catch (IOException e)
            {
                throw new ACFException("IO exception",e);
            }
        }

        public void writeDocument(String url, String fileName, String metaInfo)
                throws ACFException
        {
            try
            {
                UrlToWebUrl(url);

                if (null == metaInfo)
                        metaInfo = "";

                File inputFile = new File(fileName);
                if (!inputFile.exists())
                        throw new FileNotFoundException("Could not find file" + fileName);

                String postBody = "method=put+document&service_name=&document=[document_name="
                                + URLEncoder.encode(fileUrl, "UTF-8")
                                + ";meta_info=["
                                + metaInfo
                                + "]]&put_option=overwrite&comment=&keep_checked_out=false\n";
                //postBody = URLEncoder.encode(postBody);
                byte[] postBytes = postBody.getBytes();

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                try
                {
                        stream.write(postBytes, 0, postBytes.length);

                        FileInputStream fs = new FileInputStream(inputFile);
                        try
                        {
                                byte[] b = new byte[32768];
                                while (true)
                                {
                                        int amt = fs.read(b, 0, b.length);
                                        if (amt == -1)
                                                break;
                                        if (amt == 0)
                                                continue;
                                        stream.write(b, 0, amt);
                                }
                        }
                        finally
                        {
                                fs.close();
                        }
                }
                finally
                {
                        stream.close();
                }
                SendRequest( getAuthority() + webUrl
                                + "/_vti_bin/_vti_aut/author.dll", stream.toString());
            }
            catch (FileNotFoundException e)
            {
                throw new ACFException("File not found",e);
            }
            catch (UnsupportedEncodingException e)
            {
                throw new ACFException("Unsupported encoding",e);
            }
            catch (IOException e)
            {
                throw new ACFException("IO exception",e);
            }
        }

        public void checkinDocument(String uri)
                throws ACFException
        {
                UrlToWebUrl(uri);

                try
                {
                        String postBody = "method=checkin+document&service_name=&document_name="
                                + URLEncoder.encode(fileUrl, "UTF-8") + "&comment=&keep_checked_out=false";
                        SendRequest( getAuthority() + webUrl
                                + "/_vti_bin/_vti_aut/author.dll", postBody);
                }
                catch (UnsupportedEncodingException e)
                {
                        throw new ACFException("Unsupported encoding",e);
                }

        }

        public void addUser( String siteUrl, String lib, String user)
                throws ACFException
        {
                try
                {
                        PermissionsWS wsProxy = new PermissionsWS( makeFullURL(siteUrl), domainName + "\\" + userName, passWord, configuration);
                        PermissionsSoap ps = wsProxy.getPermissionsSoap();
        
                        ps.addPermission( lib, "List", user, "user", 0x00000001 | 0x00000002 | 0x04000000 );
                }
                catch (org.apache.axis.AxisFault e)
                {
                        throw new ACFException("Error adding user '"+user+"' to '"+siteUrl+"' library '"+lib+"'; axis fault: "+e.dumpToString(),e);
                }
                catch (Exception e)
                {
                        throw new ACFException("Error adding user '"+user+"' to '"+siteUrl+"' library '"+lib+"'",e);
                }
        }
    
        public void delUser( String siteUrl, String lib, String user)
                throws ACFException
        {
                try
                {
                        PermissionsWS wsProxy = new PermissionsWS( makeFullURL(siteUrl), domainName + "\\" + userName, passWord, configuration);
                        PermissionsSoap ps = wsProxy.getPermissionsSoap();

                        ps.removePermission( lib, "List", user, "user" );
                }
                catch (org.apache.axis.AxisFault e)
                {
                        throw new ACFException("Error removing user '"+user+"' to '"+siteUrl+"' library '"+lib+"'; axis fault: "+e.dumpToString(),e);
                }
                catch (Exception e)
                {
                        throw new ACFException("Error removing user '"+user+"' from '"+siteUrl+"' library '"+lib+"'",e);
                }
        }

        public void addSiteUser( String siteUrl, String alias, String displayName, String email, String group)
                throws ACFException
        {
                try
                {
                        UserGroupWS wsProxy = new UserGroupWS( makeFullURL(siteUrl), domainName + "\\" + userName, passWord, configuration);
                        UserGroupSoap ugs = wsProxy.getUserGroupSoap();
                        try
                        {
                                GetRoleInfoResponseGetRoleInfoResult result = ugs.getRoleInfo( group );
                                ugs.addUserToRole( group, displayName, alias, email, "" );
                        }
                        catch (org.apache.axis.AxisFault af)
                        {
                                try
                                {
                                        ugs.addUserToGroup(group, displayName, alias, email, "");
                                }
                                catch (org.apache.axis.AxisFault e)
                                {
                                        throw new ACFException("Error adding site user '"+alias+"' to site '"+siteUrl+"' in group '" + group + "'; axis fault: "+e.dumpToString(),e);
                                }
                        }
                }
                catch (Exception e)
                {
                        throw new ACFException("Error adding site user '"+alias+"' to '"+siteUrl,e);
                }
        }
    
        public void delSiteUser( String siteUrl, String alias )
                throws ACFException
        {
                try
                {
                        UserGroupWS wsProxy = new UserGroupWS( makeFullURL(siteUrl), domainName + "\\" + userName, passWord, configuration);
                        UserGroupSoap ugs = wsProxy.getUserGroupSoap();

                        ugs.removeUserFromSite( alias );
                }
                catch (org.apache.axis.AxisFault e)
                {
                        throw new ACFException("Error removing site user '"+alias+"' from '"+siteUrl+"'; axis fault: "+e.dumpToString(),e);
                }
                catch (Exception e)
                {
                        throw new ACFException("Error removing site user '"+alias+"' from '"+siteUrl+"'",e);
                }

        }

        public void setDocsMetaInfo(String uri, String metaInfo)
                throws ACFException
        {
            try
            {
                UrlToWebUrl(uri);

                String postBody = "method=set+document+meta-info&service_name=&document_name="
                                + URLEncoder.encode(fileUrl, "UTF-8") + "&meta_info=[" + URLEncoder.encode(metaInfo, "UTF-8") + "]";
                SendRequest( getAuthority() + webUrl
                                + "/_vti_bin/_vti_aut/author.dll", postBody);
            }
            catch (UnsupportedEncodingException e)
            {
                throw new ACFException("Unsupported encoding",e);
            }
        }

        private String SendRequest(String uri, String postBody)
                throws ACFException
        {

                try
                {
                        HttpClient httpClient = new HttpClient();

                        Credentials credentials = new NTCredentials(userName, passWord, currentHost, domainName);
                        httpClient.getState().setCredentials( new AuthScope( serverName, serverPort, null ), credentials );

                        PostMethod method = new PostMethod(uri);
                        method.addRequestHeader( "Content-Type", "application/x-www-form-urlencoded");
                        method.addRequestHeader("X-Vermeer-Content-Type", "application/x-www-form-urlencoded");
                        method.setRequestBody(postBody);

                        int returnCode = httpClient.executeMethod( method );

                        String responseText =  method.getResponseBodyAsString();

                        // System.err.println( "Response Received: " + returnCode +":" + responseText );
                        return responseText;
                }
                catch (Exception e)
                {
                        throw new ACFException("Error sending request to '"+uri+"'",e);
                }

        }


        private static String GetReturnValue(String response, String key)
        {
                int start = response.indexOf(key + "=");
                if (-1 == start)
                {
                        System.err.println("Expected response key '"+key+"' not found in '"+response+"'");
                        return null;
                }
                else
                        start += key.length() + 1;

                int end = response.indexOf("\n", start);
                return response.substring(start, end);
        }


     
    /**
     * SharePoint Permissions Service Wrapper Class
     */
    protected static class PermissionsWS extends PermissionsLocator
    {
        private java.net.URL endPoint;

        private String userName;

        private String password;

        public PermissionsWS( String siteUrl, String userName, String password, EngineConfiguration engineConfiguration ) throws java.net.MalformedURLException
        {
            super(engineConfiguration);
            endPoint = new java.net.URL( siteUrl + "/_vti_bin/Permissions.asmx" );
            this.userName = userName;
            this.password = password;
        }

        public com.microsoft.schemas.sharepoint.soap.directory.PermissionsSoap getPermissionsSoap()
                throws javax.xml.rpc.ServiceException
        {
            try
            {
                com.microsoft.schemas.sharepoint.soap.directory.PermissionsSoapStub _stub = new com.microsoft.schemas.sharepoint.soap.directory.PermissionsSoapStub(
                        endPoint, this );
                _stub.setPortName( getPermissionsSoapWSDDServiceName() );
                _stub.setUsername( userName );
                _stub.setPassword( password );
                return _stub;
            }
            catch (org.apache.axis.AxisFault e)
            {
                throw new javax.xml.rpc.ServiceException("Axis fault: "+e.dumpToString(),e);
            }
        }
    }

    /**
     * SharePoint UserGroup Service Wrapper Class
     */
    protected static class UserGroupWS extends UserGroupLocator    
    {
        private java.net.URL endPoint;

        private String userName;

        private String password;

        public UserGroupWS( String siteUrl, String userName, String password, EngineConfiguration engineConfiguration ) throws java.net.MalformedURLException
        {
            super(engineConfiguration);
            endPoint = new java.net.URL( siteUrl + "/_vti_bin/usergroup.asmx" );
            this.userName = userName;
            this.password = password;
        }

        public com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoap getUserGroupSoap()
                throws javax.xml.rpc.ServiceException
        {
            try
            {
                com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoapStub _stub = new com.microsoft.schemas.sharepoint.soap.directory.UserGroupSoapStub(
                        endPoint, this );
                _stub.setPortName( getUserGroupSoapWSDDServiceName() );
                _stub.setUsername( userName );
                _stub.setPassword( password );
                return _stub;
            }
            catch ( org.apache.axis.AxisFault e )
            {
                throw new javax.xml.rpc.ServiceException("Axis fault: "+e.dumpToString(), e );
            }
        }
    }
}
