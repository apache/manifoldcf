/* $Id: FilenetAddRemove.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.crawler.connectors.filenet;

import com.filenet.api.core.*;
import com.filenet.api.util.UserContext; 
import com.filenet.api.collection.*;
import com.filenet.api.meta.*;
import com.filenet.api.admin.*;
import com.filenet.api.query.*;
import com.filenet.api.util.*;
import com.filenet.api.exception.*;
import com.filenet.api.security.*;
import com.filenet.api.constants.*;
import com.filenet.api.property.*;

import java.io.*;
import java.util.*;

public class FilenetAddRemove
{
        public static final String _rcsid = "@(#)$Id: FilenetAddRemove.java 921329 2010-03-10 12:44:20Z kwright $";

        //Web service login module name
        private static final String PARAM_LOGIN_MODULE = "FileNetP8WSI";
        //Top level document class name
        private static final String PARAM_ROOT_DOC_CLASSNAME = "Document";

        private String userID = null;
        private String password = null;
        private String serverWsiURI=null;
        private String fnDomainString=null;
        private String objectStoreName=null;

        private Connection conn = null;
        private com.filenet.api.core.ObjectStore os = null;
        private Domain fnDomain = null;
        private UserContext uc =null;

        /** Create a session.
        *@param userID is the userID to use to establish the session.
        *@param password is the password to use to establish the session.
        *@param objectStore is the object store to use to establish the session.
        *@param serverWSIURI is the URI to use to get to the server's web services.
        */
        public FilenetAddRemove(String userID, String password, String domain, String objectStore, String serverWSIURI)
                throws Exception
        {
                this.userID = userID;
                this.password = password;
                this.fnDomainString = domain;
                this.serverWsiURI = serverWSIURI;
                this.objectStoreName = objectStore;
                
                // Now, set up the connection
                
                try
                {
                        conn = Factory.Connection.getConnection(serverWsiURI);
                        setConnectionCredentials();
                        //uc = UserContext.get();
                        //uc.setLocale(null);
                        //uc.pushSubject(UserContext.createSubject(conn, userName, password, PARAM_LOGIN_MODULE)); 
                        fnDomain = Factory.Domain.fetchInstance(conn,fnDomainString, null); 
                        if (fnDomain == null)
                                throw new Exception("Could not locate FileNet domain '"+fnDomain+"'");
                        os = Factory.ObjectStore.fetchInstance(fnDomain, objectStoreName, null); 
                        if (os == null)
                                throw new Exception("Could not locate FileNet objectstore '"+objectStoreName+"'");
                }
                catch(EngineRuntimeException e)
                {
                        Throwable e2 = e.getCause();
                        ExceptionCode code = e.getExceptionCode();
                        if (code.equals(ExceptionCode.TRANSPORT_WSI_NETWORK_ERROR))
                                throw new Exception("Transport error: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                        if (code.equals(ExceptionCode.SECURITY_WSI_NO_LOGIN_MODULES_SUCCEEDED))
                                throw new Exception("Login failure: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                        if (code.equals(ExceptionCode.API_INVALID_URI))
                                throw new Exception("Invalid URI error connecting to FileNet: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():"")+" This probably means your connection parameters are incorrect.",e);
                        throw new Exception("Runtime exception connecting to FileNet: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                }
                catch(Exception e)
                {
                        throw new Exception("Exception connecting to FileNet: "+e.getMessage(),e);
                }

        }


        //Add a document.  Return the document's "identifier" (which is what will be passed to delete or modify)
        public String addDoc(String filenetFolder, String filenetTitle, String localFileName, String documentClassName)
                throws Exception
        {
                setConnectionCredentials();
                try
                {
                        Folder objFolder = null;
                        if (filenetFolder != null && filenetFolder.length() > 0)
                                objFolder = (Folder)Factory.Folder.fetchInstance(os, filenetFolder, null);

                        //create an instance of document
                        com.filenet.api.core.Document objDoc = Factory.Document.createInstance(os,documentClassName);

                        //set properties. use the following syntax to set all other properties
                        objDoc.getProperties().putValue("DocumentTitle", filenetTitle);

                        //set doc content
                        ContentElementList  objContentElementList = (ContentElementList)Factory.ContentElement.createList();
                        ContentTransfer objContentTransfer        = (ContentTransfer)Factory.ContentTransfer.createInstance();
                        File f = new File(localFileName);
                        String canonicalPath = f.getCanonicalPath();
                        
                        java.io.FileInputStream fins              = new java.io.FileInputStream(f);
                        if(fins!=null)
                        {
                                try
                                {
                                        objContentTransfer.setCaptureSource(fins);
        
                                        objContentElementList.add(objContentTransfer);
                
                                        String sDownloadFileName = canonicalPath.substring(canonicalPath.lastIndexOf("/")+1);
                                        objContentTransfer.set_RetrievalName(sDownloadFileName);
                                        objDoc.set_ContentElements(objContentElementList);
        
                                        //Save document
                                        objDoc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
                                        objDoc.save(RefreshMode.REFRESH);
                                }
                                finally
                                {
                                        fins.close();
                                }

                                //file in a folder
                                if (objFolder != null)
                                {
                                        ReferentialContainmentRelationship drcr = (ReferentialContainmentRelationship)objFolder.file(objDoc, AutoUniqueName.AUTO_UNIQUE, null, DefineSecurityParentage.DEFINE_SECURITY_PARENTAGE);
                                        drcr.save (RefreshMode.REFRESH);
                                }
                                
                                return objDoc.get_Id().toString();
                        }
                        
                        // Signal that there was some kind of error.
                        return null;
                }
                catch (EngineRuntimeException e)
                {
                        Throwable e2 = e.getCause();
                        throw new Exception("Runtime exception adding FileNet document: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                }
                catch (IOException e)
                {
                        throw new Exception("IO exception: "+e.getMessage(),e);
                }
                catch(Exception e)
                {
                        throw new Exception("Exception adding FileNet document: "+e.getMessage(),e);
                }               
        }

        // Delete a document
        public void deleteDoc(String id)
                throws Exception
        {
                setConnectionCredentials();
                try
                {
                        //delete a document. follow the above code to get the document instance
                        com.filenet.api.core.Document objDoc = Factory.Document.fetchInstance(os,id, null);
                        if (objDoc != null)
                        {
                                objDoc.delete();
                                objDoc.save(RefreshMode.REFRESH);
                        }
                }
                catch (EngineRuntimeException e)
                {
                        Throwable e2 = e.getCause();
                        throw new Exception("Runtime exception deleting FileNet document: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                }
                catch(Exception e)
                {
                        throw new Exception("Exception deleting FileNet document: "+e.getMessage(),e);
                }               
        }
        
        // Modify document.  Returns an UPDATED document identifier.
        public String modifyDocument(String id, String filenetTitle, String localFileName)
                throws Exception
        {
                setConnectionCredentials();
                try
                {
                        //delete a document. follow the above code to get the document instance
                        com.filenet.api.core.Document objDoc = Factory.Document.fetchInstance(os,id, null);
                        if (objDoc != null)
                        {
                                objDoc.checkout(ReservationType.EXCLUSIVE,null,null,null);
                                // objDoc.checkout(ReservationType.getInstanceFromInt(ReservationType.EXCLUSIVE_AS_INT),null,null,null);
                                // Save the document's checked-out status???
                                objDoc.save(RefreshMode.REFRESH);
                                
                                // I guess this makes a writeable copy?
                                objDoc = (com.filenet.api.core.Document)objDoc.get_Reservation();

                                //set properties. use the following syntax to set all other properties
                                objDoc.getProperties().putValue("DocumentTitle", filenetTitle);

                                //set doc content
                                ContentElementList  objContentElementList = (ContentElementList)Factory.ContentElement.createList();
                                ContentTransfer objContentTransfer        = (ContentTransfer)Factory.ContentTransfer.createInstance();
                                File f = new File(localFileName);
                                String canonicalPath = f.getCanonicalPath();
                        
                                java.io.FileInputStream fins              = new java.io.FileInputStream(f);
                                if(fins!=null)
                                {
                                        try
                                        {
                                                objContentTransfer.setCaptureSource(fins);
        
                                                objContentElementList.add(objContentTransfer);
                
                                                String sDownloadFileName = canonicalPath.substring(canonicalPath.lastIndexOf("/")+1);
                                                objContentTransfer.set_RetrievalName(sDownloadFileName);
                                                objDoc.set_ContentElements(objContentElementList);
        
                                                //Save document
                                                objDoc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
                                                objDoc.save(RefreshMode.REFRESH);
                                        }
                                        finally
                                        {
                                                fins.close();
                                        }
                                
                                        return objDoc.get_Id().toString();
                                }
                        }
                        
                        // Signal that there was some kind of error.
                        return null;

                }
                catch (EngineRuntimeException e)
                {
                        Throwable e2 = e.getCause();
                        throw new Exception("Runtime exception modifying filenet document: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                }
                catch (IOException e)
                {
                        throw new Exception("IO exception: "+e.getMessage(),e);
                }
                catch(Exception e)
                {
                        throw new Exception("Exception modifying FileNet document: "+e.getMessage(),e);
                }               
        }

        // Set document permissions.
        public void setDocumentPermissions(String id, String[] userIDs)
                throws Exception
        {
                setConnectionCredentials();
                try
                {
                        //delete a document. follow the above code to get the document instance
                        com.filenet.api.core.Document objDoc = Factory.Document.fetchInstance(os,id, null);
                        if (objDoc != null)
                        {
                                // objDoc.checkout(ReservationType.EXCLUSIVE,null,null,null);
                                // objDoc.checkout(ReservationType.getInstanceFromInt(ReservationType.EXCLUSIVE_AS_INT),null,null,null);
                                // Save the document's checked-out status???
                                // objDoc.save(RefreshMode.REFRESH);
                                
                                // I guess this makes a writeable copy?
                                // objDoc = (com.filenet.api.core.Document)objDoc.get_Reservation();

                                AccessPermissionList list = Factory.AccessPermission.createList();
                                
                                int j = 0;
                                while (j < userIDs.length)
                                {
                                        String userID = userIDs[j++];
                                        AccessPermission ap = Factory.AccessPermission.createInstance();
                                        ap.set_GranteeName(userID);
                                        ap.set_AccessType(AccessType.getInstanceFromInt(AccessType.ALLOW_AS_INT));
                                        ap.set_AccessMask(new Integer(AccessLevel.FULL_CONTROL_AS_INT));
                                        list.add(ap);
                                }
                                
                                //set properties. use the following syntax to set all other properties
                                objDoc.set_Permissions(list);

                                //Save document
                                // objDoc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
                                objDoc.save(RefreshMode.REFRESH);
                        }
                        
                }
                catch (EngineRuntimeException e)
                {
                        Throwable e2 = e.getCause();
                        throw new Exception("Runtime exception modifying FileNet document permissions: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                }
                catch(Exception e)
                {
                        throw new Exception("Exception modifying FileNet document permissions: "+e.getMessage(),e);
                }               
        }

        // Lookup document.  Returns a CURRENT document identifier.
        public String lookupDocument(String filenetTitle, String documentClass)
                throws Exception
        {
                setConnectionCredentials();
                String sql = "SELECT Id FROM "+documentClass+" WITH EXCLUDESUBCLASSES WHERE [IsCurrentVersion] = TRUE AND [DocumentTitle] = '"+filenetTitle+"'";
                try
                {
                        SearchSQL sqlObject = new SearchSQL();
                        sqlObject.setQueryString(sql);
                        // System.out.println("Sql string is: "+sql);
                        SearchScope searchScope = new SearchScope(os);
            
                        // Uses fetchRows to test the SQL statement.
                        // System.out.println("Fetching rows");
                        RepositoryRowSet rowSet = searchScope.fetchRows(sqlObject, null, null, new Boolean(true));
                        Iterator iter = rowSet.iterator();
                        if (iter.hasNext())
                        {
                                // System.out.println("Found a row");
                                RepositoryRow row = (RepositoryRow) iter.next();
                                String docId = row.getProperties().get("Id").getIdValue().toString();
                                return docId;
                        }
                        return null;
                }
                catch (EngineRuntimeException e)
                {
                        Throwable e2 = e.getCause();
                        ExceptionCode code = e.getExceptionCode();
                        if (code.equals(ExceptionCode.TRANSPORT_WSI_NETWORK_ERROR))
                                throw new Exception("Transport error: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                        if (code.equals(ExceptionCode.SECURITY_WSI_NO_LOGIN_MODULES_SUCCEEDED))
                                throw new Exception("Login failure: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                        if (code.equals(ExceptionCode.E_ACCESS_DENIED))
                                throw new Exception("Access denied getting document information: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                        throw new Exception("Runtime exception getting matching object ids: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),e);
                }
                catch(Exception e)
                {
                        throw new Exception("Query failed: '" + sql + "':" + e.getMessage(),e);
                }
                
        }
        
        protected void setConnectionCredentials()
        {
                uc = UserContext.get();
                //uc.setLocale(null);
                uc.pushSubject(UserContext.createSubject(conn, userID, password, PARAM_LOGIN_MODULE)); 
        }

}
