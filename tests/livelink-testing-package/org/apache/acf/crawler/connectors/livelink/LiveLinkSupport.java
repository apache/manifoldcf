/* $Id$ */

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
package org.apache.acf.crawler.connectors.livelink;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;

import java.io.*;
import java.util.*;

import com.opentext.api.*;

/** This is the Livelink implementation of the IRepositoryConnectr interface.
* The original Volant code forced there to be one livelink session per JVM, with
* lots of buggy synchronization present to try to enforce this.  This implementation
* is multi-session.  However, since it is possible that the Volant restriction was
* indeed needed, I have attempted to structure things to allow me to turn on
* single-session if needed.
*
* For livelink, the document identifiers are the object identifiers.
*
*/
public class LiveLinkSupport
{
        public static final String _rcsid = "@(#)$Id$";

        // Data required for maintaining livelink connection
        private LAPI_DOCUMENTS LLDocs = null;
        private LAPI_ATTRIBUTES LLAttributes = null;
        private LAPI_USERS LLUsers = null;
        private LLSERVER llServer;
        private int LLENTWK_VOL;
        private int LLENTWK_ID;
        private int LLCATWK_VOL;
        private int LLCATWK_ID;

        protected final static String CATEGORY_NAME = "CATEGORY";
        protected final static String ENTWKSPACE_NAME = "ENTERPRISE";

        /** Constructor.
        */
        public LiveLinkSupport(String serverName, int serverPort, String serverUserName, String serverPassword)
                throws ACFException
        {

                llServer = new LLSERVER(serverName,serverPort,serverUserName,serverPassword);

                try
                {
                        LLDocs = new LAPI_DOCUMENTS(llServer.getLLSession());
                        LLAttributes = new LAPI_ATTRIBUTES(llServer.getLLSession());
                        LLUsers = new LAPI_USERS(llServer.getLLSession());

                        LLValue entinfo = new LLValue().setAssoc();

                        int status;
                        status = LLDocs.AccessEnterpriseWS(entinfo);
                        if (status == 0)
                        {
                                this.LLENTWK_ID = entinfo.toInteger("ID");
                                this.LLENTWK_VOL = entinfo.toInteger("VolumeID");
                        }

                        entinfo = new LLValue().setAssoc();
                        status = LLDocs.AccessCategoryWS(entinfo);
                        if (status == 0)
                        {
                                this.LLCATWK_ID = entinfo.toInteger("ID");
                                this.LLCATWK_VOL = entinfo.toInteger("VolumeID");
                        }
                }
                catch (Exception e)
                {
                        throw new ACFException("Connection failed: "+e.getMessage()+"; Details: "+llServer.getErrors(), e, ACFException.SETUP_ERROR);
                }
        }

        /** Close the connection.  Call this before discarding the repository connector.
        */
        public void close()
                throws ACFException
        {
                llServer.disconnect();
                llServer = null;
                LLDocs = null;
                LLAttributes = null;
        }

        /** Look up a document.
        */
        public String lookupDocument(String LLpathString)
                throws ACFException
        {
                VolumeAndId volID = findPath(LLpathString);
                if (volID == null)
                        return null;
                LLValue objInfo = getObjectInfo(volID.getVolumeID(), volID.getPathId());
                if (objInfo == null)
                        return null;
                return Integer.toString(objInfo.toInteger( "ID" ));
        }


        /** Add a document.
        */
        public String addDocument(String LLpathString, String name, String filename)
                throws ACFException
        {
                VolumeAndId volID = findPath(LLpathString + "/" + name);
                if (volID == null)
                {
                        // Add document
                        volID = findPath(LLpathString);
                        if (volID == null)
                                throw new ACFException("No such path: '"+LLpathString+"'");

                        LLValue objInfo = new LLValue().setAssocNotSet();
                        LLValue versionInfo = new LLValue().setAssocNotSet();
                        int status = LLDocs.AddDocument( volID.getVolumeID(), volID.getPathId(),
                                    name, filename, objInfo, versionInfo );

                        if ( status != 0 )
                        {
                                throw new ACFException("Error adding document: "+llServer.getErrors());
                        }

                        return Integer.toString(objInfo.toInteger( "ID" ));
                }
                else
                {
                        // Existing document.  Modify it
                        LLValue versionInfo = new LLValue().setAssocNotSet();
                        int status = LLDocs.CreateVersion( volID.getVolumeID(),
                                volID.getPathId(), filename, versionInfo );

                        if ( status != 0 )
                        {
                                throw new ACFException("Error updating document: "+llServer.getErrors());
                        }
        
                        return Integer.toString(volID.getPathId());
                }
        }

        /** Delete a document.
        */
        public void deleteDocument(String LLpathString)
                throws ACFException
        {
                VolumeAndId volID = findPath(LLpathString);
                if (volID == null)
                        // Not there, silently return
                        return;

                // Delete document
                int status = LLDocs.DeleteObject( volID.getVolumeID(), volID.getPathId() );

                if ( status != 0 )
                {
                        throw new ACFException("Error deleting document: "+llServer.getErrors());
                }
        }

        /** Modify a document.
        */
        public String modifyDocument(String LLpathString, String filename)
                throws ACFException
        {
                VolumeAndId volID = findPath(LLpathString);
                if (volID == null)
                        throw new ACFException("No such path: '"+LLpathString+"'");

                // Add document
                LLValue versionInfo = new LLValue().setAssocNotSet();
                int status = LLDocs.CreateVersion( volID.getVolumeID(),
                        volID.getPathId(), filename, versionInfo );

                if ( status != 0 )
                {
                        throw new ACFException("Error updating document: "+llServer.getErrors());
                }

                return Integer.toString(volID.getPathId());

        }

        /** Add metadata value for a document.
        */
        public void setMetadataValue(String LLpathString, String categoryPathString, String attributeName, String attributeValue)
                throws ACFException
        {
                VolumeAndId volID = findPath(LLpathString);
                if (volID == null)
                        throw new ACFException("No such path: '"+LLpathString+"'");
                
                // Start at root
                RootValue rv = new RootValue(categoryPathString);

                // Get the object id of the category the path describes
                int catObjectID = getCategoryId(rv);
                if (catObjectID == -1)
                        throw new ACFException("No such category: '"+categoryPathString+"'");

                try
                {
                        // Set up the right llvalues

                        // Object ID
                        LLValue objIDValue = new LLValue().setAssoc();
                        objIDValue.add("ID", volID.getPathId());
                        // Current version, so don't set the "Version" field

                        // CatID
                        LLValue catIDValue = new LLValue().setAssoc();
                        catIDValue.add("ID", catObjectID);
                        catIDValue.add("Type", LAPI_ATTRIBUTES.CATEGORY_TYPE_LIBRARY);

                        LLValue rvalue = new LLValue();

                        int status = LLDocs.FetchCategoryVersion(catIDValue,rvalue);
                        // If either the object is wrong, or the object does not have the specified category, return null.
                        if (status == 103101 || status == 107205)
                                throw new ACFException("Can't fetch category attributes for category "+Integer.toString(catObjectID));

                        if (status != 0)
                        {
                                throw new ACFException("Error retrieving category version: "+Integer.toString(status)+": "+llServer.getErrors());
                        }

                        // Now, try to set the revised value
                        LLValue children = new LLValue();
                        children.setList();
                        children.add(attributeValue);
                        status = LLAttributes.AttrSetValues(rvalue,attributeName,
                                0,null,children);

                        if (status != 0)
                        {
                                throw new ACFException("Error setting attribute value: "+Integer.toString(status)+": "+llServer.getErrors());
                        }

                        status = LLDocs.SetObjectAttributesEx(objIDValue,rvalue);
                        // If either the object is wrong, or the object does not have the specified category, return null.
                        if (status == 103101 || status == 107205)
                                throw new ACFException("Can't set attributes for object "+Integer.toString(volID.getPathId())+
                                        " category "+Integer.toString(catObjectID));

                        if (status != 0)
                        {
                                throw new ACFException("Error setting category version: "+Integer.toString(status)+": "+llServer.getErrors());
                        }
                }
                catch (com.opentext.api.LLIllegalOperationException e)
                {
                        throw new ACFException("Livelink: Unexpected illegal operation error: "+e.getMessage(),e);
                }
                catch (com.opentext.api.LLIOException e)
                {
                        // Server went down.  Throw a service interruption, and try again in 5 minutes.
                        throw new ACFException("IO Exception: "+e.getMessage(),e);
                }
        }

        /** Add see/seecontent rights for a user for a document.
        */
        public void addDocumentRights(String LLpathString, String userName, String domainName)
                throws ACFException
        {
                VolumeAndId volID = findPath(LLpathString);
                if (volID == null)
                        throw new ACFException("No such path: '"+LLpathString+"'");
                int userID;

                LLValue userInfo = new LLValue().setAssocNotSet();

                // Now, go through and find the right users
                try
                {
                        int status = LLUsers.GetUserInfoEx(userName,domainName,userInfo);
                        if (status != 0)
                                throw new ACFException("Error getting user info: "+llServer.getErrors());
                        userID = userInfo.toInteger("ID");
                        // First, remove right of the world to see this document
                        status = LLDocs.SetObjectRight(volID.getVolumeID(),volID.getPathId(),
                                LAPI_DOCUMENTS.RIGHT_UPDATE,LAPI_DOCUMENTS.RIGHT_WORLD, 0, 0);
                        if (status != 0)
                                System.err.println("Error returned clearing guest permissions: "+Integer.toString(status)+": "+llServer.getErrors());
                        status = LLDocs.SetObjectRight(volID.getVolumeID(),volID.getPathId(),
                                LAPI_DOCUMENTS.RIGHT_UPDATE,LAPI_DOCUMENTS.RIGHT_GROUP, 0, 0);
                        if (status != 0)
                                System.err.println("Error returned clearing default group permissions: "+Integer.toString(status)+": "+llServer.getErrors());
                        status = LLDocs.SetObjectRight(volID.getVolumeID(),volID.getPathId(),
                                LAPI_DOCUMENTS.RIGHT_ADD,userID,LAPI_DOCUMENTS.PERM_SEE | LAPI_DOCUMENTS.PERM_SEECONTENTS, 0);
                        if (status != 0)
                        {
                                status = LLDocs.SetObjectRight(volID.getVolumeID(),volID.getPathId(),
                                        LAPI_DOCUMENTS.RIGHT_UPDATE,userID,LAPI_DOCUMENTS.PERM_SEE | LAPI_DOCUMENTS.PERM_SEECONTENTS, 0);
                                if (status != 0)
                                        throw new ACFException("Error setting permissions: "+llServer.getErrors());
                        }
                }
                catch (ACFException e)
                {
                        throw e;
                }
                catch (Exception e)
                {
                        throw new ACFException("Exception looking up user '"+userName+"'",e);
                }

        }

        // Protected methods

        /** Get the volume and id, given a qualified path.
        */
        protected VolumeAndId findPath(String pathString)
                throws ACFException
        {
                RootValue rv = new RootValue(pathString);

                // Get the volume, object id of the folder/project the path describes
                return getPathId(rv);
        }


        // Protected methods and classes

        /** Rootvalue version of getCategoryId.
        */
        protected int getCategoryId(RootValue rv)
                throws ACFException
        {
                return getCategoryId(rv.getRootValue(),rv.getRemainderPath());
        }

        /**
        * Returns the category ID specified by the path name.
        * @param objInfo a value object containing information about root folder (or workspace) above the specified object
        * @param startPath is the folder name, ending in a category name (a string with slashes as separators)
        */              
        protected int getCategoryId(LLValue objInfo, String startPath)
                throws ACFException
        {
                // This is where the results go
                LLValue children = (new LLValue());

                // Grab the volume ID and starting object
                int obj = objInfo.toInteger("ID");
                int vol = objInfo.toInteger("VolumeID");

                // Pick apart the start path.  This is a string separated by slashes.
                if (startPath.length() == 0)
                        return -1;

                int charindex = 0;
                while (charindex < startPath.length())
                {
                        StringBuffer currentTokenBuffer = new StringBuffer();
                        // Find the current token
                        while (charindex < startPath.length())
                        {
                                char x = startPath.charAt(charindex++);
                                if (x == '/')
                                        break;
                                if (x == '\\')
                                {
                                        // Attempt to escape what follows
                                        x = startPath.charAt(charindex);
                                        charindex++;
                                }
                                currentTokenBuffer.append(x);
                        }
                        String subFolder = currentTokenBuffer.toString();
                        String filterString;

                        // We want only folders that are children of the current object and which match the specified subfolder
                        if (charindex < startPath.length())
                                filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
                                        " or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ")";
                        else
                                filterString = "SubType="+LAPI_DOCUMENTS.CATEGORYSUBTYPE;

                        filterString += " and Name='" + subFolder + "'";

                        try
                        {
                                int status = LLDocs.ListObjects(vol, obj, null, filterString, LAPI_DOCUMENTS.PERM_SEECONTENTS, children);
                                if (status != 0)
                                        throw new ACFException("Error finding category children: "+Integer.toString(status)+": "+llServer.getErrors());

                                // If there is one child, then we are okay.
                                if (children.size() == 1)
                                {
                                        // New starting point is the one we found.
                                        obj = children.toInteger(0, "ID");
                                        int subtype = children.toInteger(0, "SubType");
                                        if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
                                        {
                                                vol = obj;
                                                obj = -obj;
                                        }
                                }
                                else
                                {
                                        // Couldn't find the path.  Instead of throwing up, return null to indicate
                                        // illegal node.
                                        throw new ACFException("Error: no children!");
                                }
                        }
                        catch (com.opentext.api.LLIllegalOperationException e)
                        {
                                throw new ACFException("Livelink: Unexpected illegal operation error: "+e.getMessage(),e);
                        }
                        catch (com.opentext.api.LLIOException e)
                        {
                                // Server went down.  Throw a service interruption, and try again in 5 minutes.
                                throw new ACFException("IO exception: "+e.getMessage(),e);
                        }
                }
                return obj;
        }

        /** 
        * Returns an Assoc value object containing information
        * about the specified object.
        * @param vol is the volume id (which comes from the project)
        * @param id the object ID 
        * @return LLValue the LAPI value object, or null if object has been deleted (or doesn't exist)
        */
        protected LLValue getObjectInfo(int vol, int id) throws ACFException
        {
                LLValue objinfo = new LLValue().setAssocNotSet();
                int status = LLDocs.GetObjectInfo(vol,id,objinfo);

                if (status == 103101)
                        return null;

                if (status != 0)
                        throw new ACFException("Error retrieving document object: "+llServer.getErrors());
                
                return objinfo;
        }

        /** RootValue version of getPathId.
        */
        protected VolumeAndId getPathId(RootValue rv)
                throws ACFException
        {
                return getPathId(rv.getRootValue(),rv.getRemainderPath());
        }

        /**
        * Returns the object ID specified by the path name.
        * @param objInfo a value object containing information about root folder (or workspace) above the specified object
        * @param startPath is the folder name (a string with dots as separators)
        */              
        protected VolumeAndId getPathId(LLValue objInfo, String startPath)
                throws ACFException
        {
                // This is where the results go
                LLValue children = (new LLValue());

                // Grab the volume ID and starting object
                int obj = objInfo.toInteger("ID");
                int vol = objInfo.toInteger("VolumeID");

                // Pick apart the start path.  This is a string separated by dots.  
                StringTokenizer st = new StringTokenizer(startPath, "/");

                // Walk through the path.
                while (st.hasMoreTokens())
                {
                        String subFolder = st.nextToken();

                        // We want only folders that are children of the current object and which match the specified subfolder
                        String filterString;
                        if (st.hasMoreTokens())
                                filterString = "(SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE + " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE +
                                        " or SubType=" + LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE + ") and Name='" + subFolder + "'";
                        else
                                filterString = "(SubType="+ LAPI_DOCUMENTS.DOCUMENTSUBTYPE +" or SubType="+ LAPI_DOCUMENTS.FOLDERSUBTYPE +
                                        " or SubType=" + LAPI_DOCUMENTS.PROJECTSUBTYPE + " or SubType="+ LAPI_DOCUMENTS.COMPOUNDDOCUMENTSUBTYPE +
                                        ") and Name='" + subFolder + "'";

                        int status = LLDocs.ListObjects(vol, obj, null, filterString, LAPI_DOCUMENTS.PERM_SEECONTENTS, children);
                        if (status != 0)
                                throw new ACFException("Error finding start node path: "+llServer.getErrors());

                        // If there is one child, then we are okay.
                        if (children.size() == 1)
                        {
                                // New starting point is the one we found.
                                obj = children.toInteger(0, "ID");
                                int subtype = children.toInteger(0, "SubType");
                                if (subtype == LAPI_DOCUMENTS.PROJECTSUBTYPE)
                                {
                                        vol = obj;
                                        obj = -obj;
                                }
                        }
                        else
                        {
                                // Couldn't find the path.  Instead of throwing up, return null to indicate
                                // illegal node.
                                return null;
                        }
                }
                return new VolumeAndId(vol,obj);
        }



        // Protected static methods

        /** Class for returning volume id/folder id combination on path lookup.
        */
        protected static class VolumeAndId
        {
                protected int volumeID;
                protected int folderID;

                public VolumeAndId(int volumeID, int folderID)
                {
                        this.volumeID = volumeID;
                        this.folderID = folderID;
                }

                public int getVolumeID()
                {
                        return volumeID;
                }

                public int getPathId()
                {
                        return folderID;
                }
        }



        /** Class representing a root value object, plus remainder string.
        * This class peels off the workspace name prefix from a path string or
        * attribute string, and finds the right workspace root node and remainder
        * path.
        */
        protected class RootValue
        {
                protected String workspaceName;
                protected LLValue rootValue = null;
                protected String remainderPath;

                /** Constructor.
                *@param pathString is the path string.
                */
                public RootValue(String pathString)
                {
                        int colonPos = pathString.indexOf(":");
                        if (colonPos == -1)
                        {
                                remainderPath = pathString;
                                workspaceName = ENTWKSPACE_NAME;
                        }
                        else
                        {
                                workspaceName = pathString.substring(0,colonPos);
                                remainderPath = pathString.substring(colonPos+1);
                        }
                }

                /** Get the path string.
                *@return the path string (without the workspace name prefix).
                */
                public String getRemainderPath()
                {
                        return remainderPath;
                }

                /** Get the root node.
                *@return the root node.
                */
                public LLValue getRootValue()
                        throws ACFException
                {
                        if (rootValue == null)
                        {
                                if (workspaceName.equals(CATEGORY_NAME))
                                        rootValue = getObjectInfo(LLCATWK_VOL,LLCATWK_ID);
                                else if (workspaceName.equals(ENTWKSPACE_NAME))
                                        rootValue = getObjectInfo(LLENTWK_VOL,LLENTWK_ID);
                                else
                                        throw new ACFException("Bad workspace name");
                                if (rootValue == null)
                                        throw new ACFException("Could not get workspace/volume ID");
                        }

                        return rootValue;
                }
        }

}


