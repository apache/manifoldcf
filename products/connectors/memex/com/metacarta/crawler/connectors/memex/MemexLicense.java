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
package com.metacarta.crawler.connectors.memex;

import com.metacarta.license.LicenseFileService;
import com.metacarta.license.LicenseFile;
import com.metacarta.license.MetaCartaLicenseFileConstants;
import java.lang.reflect.*;


/* singleton class to manage the MetaCarta license file.  Call
 * verify() to figure out if the documentum connector is enabled.
 * TBD: replace this with a more generic interface POST FREEZE.
 */
public class MemexLicense {
    public static final String _rcsid = "@(#)$Id$";
    
    // make this a singleton object
    private static MemexLicense _instance = null;

    private static synchronized void createInstance() {
	if (_instance == null) {
	    _instance = new MemexLicense();
	}
    }   

    public static MemexLicense getInstance() {
	if (_instance == null) {
	    createInstance();
	}
	return _instance;
    }

    private static ClassLoader findClassLoader()
    {
	ClassLoader rval = ClassLoader.getSystemClassLoader();
	return rval;
	/*
	while (true)
	    {
		ClassLoader parent = rval.getParent();
		if (parent == null)
		    return rval;
		rval = parent;
	    }
	*/
    }

    private LicenseFileService lfs = null;
   
    // constructor
    private MemexLicense() {
	ClassLoader cl = findClassLoader();
	try
	    {
		Class licenseFileServiceClass = cl.loadClass("com.metacarta.license.LicenseFileService");
		Class constantsClass = cl.loadClass("com.metacarta.license.MetaCartaLicenseFileConstants");
		Constructor c = licenseFileServiceClass.getConstructor(new Class[]{String.class,String.class});
		lfs = (LicenseFileService)c.newInstance(new Object[]{"/var/lib/metacarta/license",
			(String)constantsClass.getDeclaredField("MEMEX_CONNECTOR_PREFIX").get(null)});
	    }
	catch (Exception e)
	    {
		throw new Error("Can't invoke license manager",e);
	    }

	// lfs = new LicenseFileService("/var/lib/metacarta/license", 
	//			     MetaCartaLicenseFileConstants.FILENET_CONNECTOR_PREFIX);
    }

    
    public LicenseFile.Error verify() {
	return lfs.verify();
    }
}

