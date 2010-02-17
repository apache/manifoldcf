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
package org.apache.lcf.crawler.connectors.DCTM;

import com.trinitytechnologies.tools.dfc.*;
import org.apache.log4j.*;
import com.trinitytechnologies.tools.dfc.DFCSessionManagerEx;
import com.documentum.fc.client.*;
import java.util.*;
import java.io.*;

import com.documentum.fc.common.*;

/** Documentum support class, which establishes a session with parameters passed in (to support testing).
*/
public class DCTMAddRemove
        extends DFCSessionManagerEx
{
	public static final String _rcsid = "@(#)$Id$";

	String strLocation = null;
        String strObjectType = null;
        DfClient objDfClient = null;


        public DCTMAddRemove(String strDocbase, String strDomain, String strUsername, String strPassword, String strLocation)
            throws Exception
        {
        	super();

		// Initialize the logger.  Hopefully nothing else will reset it.
		PropertyConfigurator.configure("/etc/documentum/log4j.properties");

                try
                {
                    //--Do work...
		    this.setDocbase(strDocbase);
		    this.setUser(strUsername);
		    this.setPassword(strPassword);
		    this.setDomain(strDomain);

                    System.err.println("Docbase = '" + strDocbase + "'");
                    System.err.println("Username = '" + strUsername + "'");
                    System.err.println("Password = '" + strPassword + "'");
                    System.err.println("Domain = '" + strDomain + "'");

		    this.strLocation = strLocation;
                    if (this.strLocation == null || this.strLocation.length() < 1)
                    {
                        this.strLocation = "";
                    }
                    else
                    {
                        if (!this.strLocation.startsWith("/"))
                        {
                            this.strLocation = "/" + this.strLocation;
                        }
                    }
                    System.err.println("Location = '" + strLocation + "'");

                    strObjectType = "dm_document";
                    if (strObjectType == null || strObjectType.length() < 1)
                    {
                        strObjectType = "dm_document";
                    }

                    System.err.println("ObjectType = '" + strObjectType + "'");

                    //--At this point, we have set up our default identity and have the default docbase.
                    //--So, initialize...
                    this.setIdentity(strDocbase);

                    System.err.println("Identity has been set" + this.hasIdentity(strDocbase));
                    objDfClient = new DfClient();
                    System.err.println("got a dfclient connection");
                    System.err.println("DFC Version" + objDfClient.getDFCVersion());

                    System.err.println("DMCL Version" + objDfClient.getLocalClient().getClientConfig().getString("r_dmcl_version"));

                }
                catch (Exception ex)
                {
			throw new Exception("Initialization error",ex);
                }
        }

	/** Lookup a document in the docbase.
	*@return returns the object ID, or null if object not found.
	*/
	public String FindDoc(String strDocumentumName)
	    throws Exception
	{
            try
            {
                IDfSession objIDfSession = null;
                objIDfSession = this.getIDfSession(this.getDocbase());
                IDfSysObject objIDfSysObject = (IDfSysObject) objIDfSession.getObjectByQualification(strObjectType + "  where object_name='" + strDocumentumName + "' AND Folder('"+strLocation+"')");
		if (objIDfSysObject == null)
			return null;
		return objIDfSysObject.getObjectId().toString();
            }
            catch (Exception ex)
            {
		throw new Exception("Exception in FindDoc",ex);
            }

	}

        /** Add a document to the docbase.  Will update an existing document if already present.
	* Returns the object ID.
        */
        public String AddDoc(String strDocumentumName, String strLocalPath)
            throws Exception
        {
	    int lastIndex = strLocalPath.lastIndexOf(".");
	    String documentumType = "crtxt";
	    if (lastIndex != -1)
	    {
		String ext = strLocalPath.substring(lastIndex+1).toLowerCase();
		if (ext.equals("htm") || ext.equals("html") || ext.equals("xml") || ext.equals("txt"))
			documentumType = "crtext";
		else if (ext.equals("pdf"))
			documentumType = "pdf";
		else if (ext.equals("doc"))
			documentumType = "msw8";
		else if (ext.equals("xls"))
			documentumType = "excel8Book";
		else
			throw new Exception("Unknown document extension: "+ext);
	    }

            try
            {
                IDfSession objIDfSession = null;


                objIDfSession = this.getIDfSession(this.getDocbase());



                IDfSysObject objIDfSysObject = (IDfSysObject) objIDfSession.getObjectByQualification(strObjectType + "  where object_name='" + strDocumentumName + "' AND Folder('"+strLocation+"')");

                if (objIDfSysObject == null)
                {
                    objIDfSysObject = (IDfSysObject) objIDfSession.newObject(strObjectType);
                    objIDfSysObject.setObjectName(strDocumentumName);
                    System.err.println("Created new object of object type: " + strObjectType + " with object ID " + objIDfSysObject.getObjectId().toString());
                    objIDfSysObject.link(strLocation);
                    objIDfSysObject.useACL("FOLDER");
                }
                else
		{
                    System.err.println("Located existing object with object ID=" + objIDfSysObject.getObjectId().toString());
                }

                System.err.println("Loading document: " + strLocalPath);
                objIDfSysObject.setContentType(documentumType);
                objIDfSysObject.setFile(strLocalPath);
                System.err.println("About to save document");
                objIDfSysObject.save();
                if (!objIDfSysObject.isDirty())
		{
                    System.err.println("Object has been saved");
                }
                else
		{
                    System.err.println("Object not saved");
                }

		String objectID = objIDfSysObject.getObjectId().toString();

                objIDfSysObject = null;
                objIDfSession = null;
		return objectID;
            }
            catch (Exception ex)
            {
		throw new Exception("Exception in AddDoc",ex);
            }
        }



        /** Delete a specified document.  Q: Why no path specification?
         */
        public void DeleteDoc(String strDocumentumName)
            throws Exception
        {
            try
            {
                IDfSession objIDfSession = null;

                objIDfSession = this.getIDfSession(this.getDocbase());
                IDfSysObject objIDfSysObject = (IDfSysObject) objIDfSession.getObjectByQualification(strObjectType + "  where object_name='" + strDocumentumName + "' AND Folder('"+strLocation+"')");

                if (objIDfSysObject == null)
                {
                    System.err.println("Can not find document to delete:" + strDocumentumName);
                }
                else {
                    System.err.println("Located existing object with object ID=" + objIDfSysObject.getObjectId().toString());
                }

                objIDfSysObject.destroyAllVersions();
                System.err.println("All version of document:" + strDocumentumName + " has been deleted");

                objIDfSysObject = null;
                objIDfSession = null;
            }
            catch (Exception ex)
            {
                System.err.println("Exception thrown: " + ex.getMessage());
                throw new Exception("Exception in DeleteDoc");

            }
        }

        /** Reversion the specified document.  Return the object ID.
         */
        public String VersionDoc(String strDocumentumName)
            throws Exception
        {
            try
            {
                IDfSession objIDfSession = null;

                objIDfSession = this.getIDfSession(this.getDocbase());
                IDfSysObject objIDfSysObject = (IDfSysObject) objIDfSession.getObjectByQualification(strObjectType + "  where object_name='" + strDocumentumName + "' AND Folder('"+strLocation+"')");

                if (objIDfSysObject == null)
                {
                    System.err.println("Can not find document to version :" + strDocumentumName);
                }
                else
		{
                    System.err.println("Located existing object with object ID=" + objIDfSysObject.getObjectId().toString());
                }

                objIDfSysObject.checkout();
                IDfSysObject objNewSysObject = (IDfSysObject) objIDfSession.getObject(objIDfSysObject.checkin(false, ""));
                System.out.println("Versioned document:" + strDocumentumName + " to version:" + objNewSysObject.getVersionPolicy().getSameLabel().toString());

		String objectID = objIDfSysObject.getObjectId().toString();
                objIDfSysObject = null;
                objNewSysObject = null;
                objIDfSession = null;
		return objectID;

            }
            catch (Exception ex)
            {
                throw new Exception("Exception in VersionDoc",ex);
            }
        }



}

