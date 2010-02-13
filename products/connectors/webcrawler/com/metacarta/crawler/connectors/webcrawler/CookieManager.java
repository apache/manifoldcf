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
package com.metacarta.crawler.connectors.webcrawler;

import java.util.*;
import java.io.*;
import com.metacarta.core.interfaces.*;
import com.metacarta.crawler.interfaces.*;
import com.metacarta.authorities.interfaces.*;
import com.metacarta.crawler.interfaces.CacheKeyFactory;
import com.metacarta.crawler.system.Metacarta;
import com.metacarta.crawler.system.Logging;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.cookie.Cookie2;

/** This class manages the database table into which we write cookies.  The data resides in the database,
* as well as in cache (up to a certain point).  The result is that there is a memory limited, database-backed repository
* of cookies that we can draw on.
*/
public class CookieManager extends com.metacarta.core.database.BaseTable
{
        public static final String _rcsid = "@(#)$Id$";

        // Robots cache class.  Only one needed.
        protected static CookiesCacheClass cookiesCacheClass = new CookiesCacheClass();

        // Database fields
        protected final static String keyField = "sequencekey";
        protected final static String ordinalField = "ordinal";
        // The rest of these individual fields are here only because the &^*% httpclient Cookie class doesn't have a constructor that
        // accepts the string form, so we're forced to keep all the cookie construction arguments individually.
        protected final static String domainSpecifiedField = "domainspecified";
        protected final static String domainField = "domain";
        protected final static String nameField = "name";
        protected final static String valueField = "value";
        protected final static String pathSpecifiedField = "pathspecified";
        protected final static String pathField = "path";
        protected final static String versionSpecifiedField = "versionspecified";
        protected final static String versionField = "version";
        protected final static String commentField = "comment";
        protected final static String secureField = "secure";
        protected final static String expirationDateField = "expirationdate";
        protected final static String discardField = "discard";
        protected final static String commentURLField = "commenturl";
        protected final static String portBlankField = "portblank";
        protected final static String portSpecifiedField = "portspecified";
        protected final static String portField = "ports";
        

        // Cache manager.  This handle is set up during the constructor.
        ICacheManager cacheManager;

        /** Constructor.  Note that one cookiemanager handle is only useful within a specific thread context,
        * so the calling connector object logic must recreate the handle whenever the thread context changes.
        *@param tc is the thread context.
        *@param database is the database handle.
        */
        public CookieManager(IThreadContext tc, IDBInterface database)
                throws MetacartaException
        {
                super(database,"cookiedata");
                cacheManager = CacheManagerFactory.make(tc);
        }

        /** Install the manager.
        */
        public void install()
                throws MetacartaException
        {
                beginTransaction();
                try
                {
                        Map existing = getTableSchema(null,null);
                        if (existing == null)
                        {
                                // Install the table.
                                HashMap map = new HashMap();
                                map.put(keyField,new ColumnDescription("VARCHAR(255)",false,false,null,null,false));
                                map.put(ordinalField,new ColumnDescription("BIGINT",false,false,null,null,false));
                                // The rest of the fields allow us to recreate Cookie objects from the database so we can hand them
                                // to httpclient.  (It would be better if we just kept the cookie data around, but that's not how httpclient works.)
                                map.put(domainSpecifiedField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
                                map.put(domainField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
                                map.put(nameField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
                                map.put(valueField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
                                map.put(pathSpecifiedField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
                                map.put(pathField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
                                map.put(versionSpecifiedField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
                                map.put(versionField,new ColumnDescription("BIGINT",false,true,null,null,false));
                                map.put(commentField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
                                map.put(secureField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
                                map.put(expirationDateField,new ColumnDescription("BIGINT",false,true,null,null,false));
                                map.put(discardField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
                                map.put(commentURLField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
                                map.put(portBlankField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
                                map.put(portSpecifiedField,new ColumnDescription("CHAR(1)",false,false,null,null,false));
                                map.put(portField,new ColumnDescription("LONGTEXT",false,true,null,null,false));
                                performCreate(map,null);
                            
                                // Create the appropriate indices
                                ArrayList list = new ArrayList();
                                list.add(keyField);
                                addTableIndex(false,list);
                        }
                }
                catch (MetacartaException e)
                {
                        signalRollback();
                        throw e;
                }
                catch (Error e)
                {
                        signalRollback();
                        throw e;
                }
                finally
                {
                        endTransaction();
                }
        }

        /** Uninstall the manager.
        */
        public void deinstall()
                throws MetacartaException
        {
                performDrop(null);
        }

        /** Read cookies currently in effect for a given session key.
        *@param sessionKey is the session key.
        *@return the login cookies object.
        */
        public LoginCookies readCookies(String sessionKey)
                throws MetacartaException
        {
                // Build description objects
                CookiesDescription[] objectDescriptions = new CookiesDescription[1];
                StringSetBuffer ssb = new StringSetBuffer();
                ssb.add(getCookiesCacheKey(sessionKey));
                objectDescriptions[0] = new CookiesDescription(sessionKey,new StringSet(ssb));

                CookiesExecutor exec = new CookiesExecutor(this,objectDescriptions[0]);
                cacheManager.findObjectsAndExecute(objectDescriptions,null,exec,getTransactionID());
            
                // Expiration is in fact done by the web site; the cookies will be updated if necessary.
                return exec.getResults();
        }
        
        /** Update cookes that are in effect for a given session key.
        *@param sessionKey is the session key.
        *@param cookies are the cookies to write into the database.
        */
        public void updateCookies(String sessionKey, LoginCookies cookies)
                throws MetacartaException
        {
                StringSetBuffer ssb = new StringSetBuffer();
                ssb.add(getCookiesCacheKey(sessionKey));
                StringSet cacheKeys = new StringSet(ssb);
                ICacheHandle ch = cacheManager.enterCache(null,cacheKeys,getTransactionID());
                try
                {
                        beginTransaction();
                        try
                        {
                                // Delete any old cookies, and create new ones
                                ArrayList list = new ArrayList();
                                list.add(sessionKey);
                                performDelete("WHERE "+keyField+"=?",list,null);
                            
                                // Now, insert the new cookies
                                int i = 0;
                                while (i < cookies.getCookieCount())
                                {
                                        Cookie c = cookies.getCookie(i);
                                        Cookie2 c2;
                                        if (c instanceof Cookie2)
                                                c2 = (Cookie2)c;
                                        else
                                                c2 = null;
                                        HashMap map = new HashMap();
                                        map.put(keyField,sessionKey);
                                        map.put(ordinalField,new Long(i));
                                        String domain = c.getDomain();
                                        if (domain != null && domain.length() > 0)
                                                map.put(domainField,domain);
                                        map.put(domainSpecifiedField,booleanToString(c.isDomainAttributeSpecified()));
                                        String name = c.getName();
                                        if (name != null && name.length() > 0)
                                                map.put(nameField,name);
                                        String value = c.getValue();
                                        if (value != null && value.length() > 0)
                                                map.put(valueField,value);
                                        String path = c.getPath();
                                        if (path != null && path.length() > 0)
                                                map.put(pathField,path);
                                        map.put(pathSpecifiedField,booleanToString(c.isPathAttributeSpecified()));
                                        map.put(versionField,new Long(c.getVersion()));
                                        if (c2 != null)
                                                map.put(versionSpecifiedField,booleanToString(c2.isVersionAttributeSpecified()));
                                        else
                                                // Make something up.  It may not be correct, but there's really no choice.
                                                map.put(versionSpecifiedField,booleanToString(true));
                                        String comment = c.getComment();
                                        if (comment != null && comment.length() > 0)
                                                map.put(commentField,comment);
                                        map.put(secureField,booleanToString(c.getSecure()));
                                        Date expirationDate = c.getExpiryDate();
                                        if (expirationDate != null)
                                                map.put(expirationDateField,new Long(expirationDate.getTime()));
                                        if (c2 != null)
                                                map.put(discardField,booleanToString(!c2.isPersistent()));
                                        else
                                                // Once again, make something up.
                                                map.put(discardField,booleanToString(true));
                                        if (c2 != null)
                                        {
                                                String commentURL = c2.getCommentURL();
                                                if (commentURL != null && commentURL.length() > 0)
                                                        map.put(commentURLField,commentURL);
                                        }
                                        if (c2 != null)
                                        {
                                                int[] ports = c2.getPorts();
                                                if (ports != null && ports.length > 0)
                                                        map.put(portField,portsToString(ports));
                                        }
                                        if (c2 != null)
                                                map.put(portBlankField,booleanToString(c2.isPortAttributeBlank()));
                                        else
                                                map.put(portBlankField,booleanToString(true));
                                        if (c2 != null)
                                                map.put(portSpecifiedField,booleanToString(c2.isPortAttributeSpecified()));
                                        else
                                                map.put(portSpecifiedField,booleanToString(false));
                                        performInsert(map,null);
                                        i++;
                                }
                            
                                cacheManager.invalidateKeys(ch);
                        }
                        catch (MetacartaException e)
                        {
                                signalRollback();
                                throw e;
                        }
                        catch (Error e)
                        {
                                signalRollback();
                                throw e;
                        }
                        finally
                        {
                                endTransaction();
                        }
                }
                finally
                {
                        cacheManager.leaveCache(ch);
                }
        }
        
        // Protected methods and classes

        /** Construct a global key which represents an individual session.
        *@param sessionKey is the session key.
        *@return the cache key.
        */
        protected static String getCookiesCacheKey(String sessionKey)
        {
                return "COOKIES_"+sessionKey;
        }

        /** Read cookies from database, uncached.
        *@param sessionKey is the session key.
        *@return the login cookies object.
        */
        protected LoginCookies readCookiesUncached(String sessionKey)
                throws MetacartaException
        {
                ArrayList list = new ArrayList();
                list.add(sessionKey);
                IResultSet result = performQuery("SELECT * FROM "+getTableName()+" WHERE "+keyField+"=? ORDER BY "+ordinalField+" ASC",list,null,null);
                DynamicCookieSet dcs = new DynamicCookieSet();
                int i = 0;
                while (i < result.getRowCount())
                {
                        IResultRow row = result.getRow(i++);
                        Cookie2 c = new Cookie2();
                        String domain = (String)row.getValue(domainField);
                        if (domain != null && domain.length() > 0)
                                c.setDomain(domain);
                        c.setDomainAttributeSpecified(stringToBoolean((String)row.getValue(domainSpecifiedField)));
                        String name = (String)row.getValue(nameField);
                        if (name != null && name.length() > 0)
                                c.setName(name);
                        String value = (String)row.getValue(valueField);
                        if (value != null && value.length() > 0)
                                c.setValue(value);
                        String path = (String)row.getValue(pathField);
                        if (path != null && path.length() > 0)
                                c.setPath(path);
                        c.setPathAttributeSpecified(stringToBoolean((String)row.getValue(pathSpecifiedField)));
                        Long version = (Long)row.getValue(versionField);
                        if (version != null)
                                c.setVersion((int)version.longValue());
                        c.setVersionAttributeSpecified(stringToBoolean((String)row.getValue(versionSpecifiedField)));
                        String comment = (String)row.getValue(commentField);
                        if (comment != null)
                                c.setComment(comment);
                        c.setSecure(stringToBoolean((String)row.getValue(secureField)));
                        Long expirationDate = (Long)row.getValue(expirationDateField);
                        if (expirationDate != null)
                                c.setExpiryDate(new Date(expirationDate.longValue()));
                        c.setDiscard(stringToBoolean((String)row.getValue(discardField)));
                        String commentURL = (String)row.getValue(commentURLField);
                        if (commentURL != null && commentURL.length() > 0)
                                c.setCommentURL(commentURL);
                        String ports = (String)row.getValue(portField);
                        // Ports are comma-separated
                        if (ports != null && ports.length() > 0)
                                c.setPorts(stringToPorts(ports));
                        c.setPortAttributeBlank(stringToBoolean((String)row.getValue(portBlankField)));
                        c.setPortAttributeSpecified(stringToBoolean((String)row.getValue(portSpecifiedField)));

                        dcs.addCookie(c);
                }
                return dcs;
        }

        /** Convert a boolean string to a boolean.
        */
        protected static boolean stringToBoolean(String value)
                throws MetacartaException
        {
                if (value.equals("T"))
                        return true;
                else if (value.equals("F"))
                        return false;
                else
                        throw new MetacartaException("Expected T or F but saw "+value);
        }
        
        /** Convert a boolean to a boolean string.
        */
        protected static String booleanToString(boolean value)
        {
                if (value)
                        return "T";
                else
                        return "F";
        }
        
        /** Convert a string to a port array.
        */
        protected static int[] stringToPorts(String value)
                throws MetacartaException
        {
                String[] ports = value.split(",");
                int[] rval = new int[ports.length];
                int i = 0;
                while (i < rval.length)
                {
                        try
                        {
                                rval[i] = Integer.parseInt(ports[i]);
                        }
                        catch (NumberFormatException e)
                        {
                                throw new MetacartaException(e.getMessage(),e);
                        }
                        i++;
                }
                return rval;
        }
        
        /** Convert a port array to a string.
        */
        protected static String portsToString(int[] ports)
        {
                StringBuffer sb = new StringBuffer();
                int i = 0;
                while (i < ports.length)
                {
                        if (i > 0)
                                sb.append(",");
                        sb.append(Integer.toString(ports[i]));
                        i++;
                }
                return sb.toString();
        }
        
        /** This is a set of cookies, built dynamically.
        */
        protected static class DynamicCookieSet implements LoginCookies
        {
                protected ArrayList cookies = new ArrayList();
            
                public DynamicCookieSet()
                {
                }
                
                public void addCookie(Cookie c)
                {
                        cookies.add(c);
                }
                
                public int getCookieCount()
                {
                        return cookies.size();
                }
                
                public Cookie getCookie(int index)
                {
                        return (Cookie)cookies.get(index);
                }
        }
        
        /** This is the object description for a session key object.
        * This is the key that is used to look up cached data.
        */
        protected static class CookiesDescription extends com.metacarta.core.cachemanager.BaseDescription
        {
                protected String sessionKey;
                protected String criticalSectionName;
                protected StringSet cacheKeys;

                public CookiesDescription(String sessionKey, StringSet invKeys)
                {
                        super("cookiescache");
                        this.sessionKey = sessionKey;
                        criticalSectionName = getClass().getName()+"-"+sessionKey;
                        cacheKeys = invKeys;
                }

                public String getSessionKey()
                {
                        return sessionKey;
                }

                public int hashCode()
                {
                        return sessionKey.hashCode();
                }

                public boolean equals(Object o)
                {
                        if (!(o instanceof CookiesDescription))
                                return false;
                        CookiesDescription d = (CookiesDescription)o;
                        return d.sessionKey.equals(sessionKey);
                }

                public String getCriticalSectionName()
                {
                        return criticalSectionName;
                }

                /** Get the cache keys for an object (which may or may not exist yet in
                * the cache).  This method is called in order for cache manager to throw the correct locks.
                * @return the object's cache keys, or null if the object should not
                * be cached.
                */
                public StringSet getObjectKeys()
                {
                        return cacheKeys;
                }

                /** Get the object class for an object.  The object class is used to determine
                * the group of objects treated in the same LRU manner.
                * @return the newly created object's object class, or null if there is no
                * such class, and LRU behavior is not desired.
                */
                public ICacheClass getObjectClass()
                {
                        return cookiesCacheClass;
                }
        }

        /** Cache class for robots.
        * An instance of this class describes the cache class for cookie caching.  There's
        * only ever a need for one, so that will be created statically.
        */
        protected static class CookiesCacheClass implements ICacheClass
        {
                /** Get the name of the object class.
                * This determines the set of objects that are treated in the same
                * LRU pool.
                *@return the class name.
                */
                public String getClassName()
                {
                        // We count all the cookies, so this is a constant string.
                        return "COOKIESCLASS";
                }

                /** Get the maximum LRU count of the object class.
                *@return the maximum number of the objects of the particular class
                * allowed.
                */
                public int getMaxLRUCount()
                {
                        // Hardwired for the moment; 2000 cookies records will be cached,
                        // and no more.
                        return 2000;
                }

        }

        /** This is the executor object for locating cookies session objects.
        * This object furnishes the operations the cache manager needs to rebuild objects that it needs that are
        * not in the cache at the moment.
        */
        protected static class CookiesExecutor extends com.metacarta.core.cachemanager.ExecutorBase
        {
                // Member variables
                protected CookieManager thisManager;
                protected LoginCookies returnValue;
                protected CookiesDescription thisDescription;

                /** Constructor.
                *@param manager is the RobotsManager class instance.
                *@param objectDescription is the desired object description.
                */
                public CookiesExecutor(CookieManager manager, CookiesDescription objectDescription)
                {
                        super();
                        thisManager = manager;
                        thisDescription = objectDescription;
                        returnValue = null;
                }

                /** Get the result.
                *@return the looked-up or read cached instance.
                */
                public LoginCookies getResults()
                {
                        return returnValue;
                }

                /** Create a set of new objects to operate on and cache.  This method is called only
                * if the specified object(s) are NOT available in the cache.  The specified objects
                * should be created and returned; if they are not created, it means that the
                * execution cannot proceed, and the execute() method will not be called.
                * @param objectDescriptions is the set of unique identifier of the object.
                * @return the newly created objects to cache, or null, if any object cannot be created.
                *  The order of the returned objects must correspond to the order of the object descriptinos.
                */
                public Object[] create(ICacheDescription[] objectDescriptions) throws MetacartaException
                {
                        // I'm not expecting multiple values to be requested, so it's OK to walk through the objects
                        // and do a request at a time.
                        LoginCookies[] rval = new LoginCookies[objectDescriptions.length];
                        int i = 0;
                        while (i < rval.length)
                        {
                                CookiesDescription desc = (CookiesDescription)objectDescriptions[i];
                                rval[i] = thisManager.readCookiesUncached(desc.getSessionKey());
                                i++;
                        }

                        return rval;
                }


                /** Notify the implementing class of the existence of a cached version of the
                * object.  The object is passed to this method so that the execute() method below
                * will have it available to operate on.  This method is also called for all objects
                * that are freshly created as well.
                * @param objectDescription is the unique identifier of the object.
                * @param cachedObject is the cached object.
                */
                public void exists(ICacheDescription objectDescription, Object cachedObject) throws MetacartaException
                {
                        // Cast what came in as what it really is
                        CookiesDescription objectDesc = (CookiesDescription)objectDescription;
                        LoginCookies cookiesData = (LoginCookies)cachedObject;
                        if (objectDesc.equals(thisDescription))
                                returnValue = cookiesData;
                }

                /** Perform the desired operation.  This method is called after either createGetObject()
                * or exists() is called for every requested object.
                */
                public void execute() throws MetacartaException
                {
                        // Does nothing; we only want to fetch objects in this cacher.
                }
        }


}
