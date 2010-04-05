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
package org.apache.lcf.crawler.connectors.memex;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.authorities.interfaces.*;
import org.apache.lcf.authorities.system.Logging;
import org.apache.lcf.authorities.system.LCF;
import com.memex.mie.*;
import com.memex.mie.pool.*;
import java.util.*;
import java.io.*;
import java.text.SimpleDateFormat;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;



/** This is the Memex implementation of the IAuthorityConnector interface.
*
* Access tokens for memex are simply user and usergroup memex identifiers.
*
*/
public class MemexAuthority extends org.apache.lcf.authorities.authorities.BaseAuthorityConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Parameters
  public static final String CONFIG_PARAM_MEMEXSERVERNAME = "Memex server name";
  public static final String CONFIG_PARAM_MEMEXSERVERPORT = "Memex server port";
  public static final String CONFIG_PARAM_USERID = "User ID";
  public static final String CONFIG_PARAM_PASSWORD = "Password";
  public static final String CONFIG_PARAM_USERNAMEMAPPING = "User name mapping";
  public static final String CONFIG_PARAM_CHARACTERENCODING = "Character encoding";

  /** How long the connection may lie idle before it is freed.  I've chosen 15 minutes. */
  private final static long CONNECTION_IDLE_INTERVAL = 900000L;


  private static final String globalDenyToken = "DEAD_AUTHORITY";
  private static final AuthorizationResponse unreachableResponse = new AuthorizationResponse(new String[]{globalDenyToken},
    AuthorizationResponse.RESPONSE_UNREACHABLE);
  private static final AuthorizationResponse userNotFoundResponse = new AuthorizationResponse(new String[]{globalDenyToken},
    AuthorizationResponse.RESPONSE_USERNOTFOUND);

  // Local variables
  protected String userName = null;
  protected String userPassword = null;
  protected String hostName = null;
  protected int hostPort = -1;
  protected String characterEncoding = null;

  //mieConnection is the connection to the main Configuration Server.
  //There will be further LCFMemexConnection objects for each
  //physical server accessed through the physicalServers collection.
  private LCFMemexConnection mieConnection = null;
  private MemexConnectionPool miePool = new MemexConnectionPool();

  //Collection describing the logical servers making up this system
  private Hashtable<String, LogicalServer> logicalServers = null;
  private Hashtable<String, LogicalServer> logicalServersByPrefix = null;

  //Collection describing the physical servers making up this system
  private Hashtable<String, LCFMemexConnection> physicalServers = null;

  //Two collections describing the entities in the set-up - one keyed by the entities' name, the other
  //by their label - generally speaking, we should use labels for anything being presented to the users
  //as this is what they are used to seeing within Patriarch.
  private Hashtable<String, MemexEntity> entitiesByName = null;
  private Hashtable<String, MemexEntity> entitiesByLabel = null;
  private Hashtable<String, MemexEntity> entitiesByPrefix = null;

  private Hashtable<String,Hashtable<String, String>> prefixList = null;
  private Hashtable<String, Hashtable<String,Hashtable<String, String>>> roleGroups = null;

  // Connection expiration time
  private long connectionExpirationTime = -1L;

  // Match map for username
  private MatchMap matchMap = null;

  /** Constructor.
  */
  public MemexAuthority()
  {
    super();
  }

  /** Return the path for the UI interface JSP elements.
  * These JSP's must be provided to allow the connector to be configured, and to
  * permit it to present document filtering specification information in the UI.
  * This method should return the name of the folder, under the <webapp>/connectors/
  * area, where the appropriate JSP's can be found.  The name should NOT have a slash in it.
  *@return the folder part
  */
  public String getJSPFolder()
  {
    return "memex";
  }

  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  */
  public void connect(ConfigParams configParams)
  {
    // Grab some values for convenience
    super.connect(configParams);
    userName = configParams.getParameter(CONFIG_PARAM_USERID);
    userPassword = configParams.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
    hostName = configParams.getParameter(CONFIG_PARAM_MEMEXSERVERNAME);
    hostPort = Integer.parseInt(configParams.getParameter(CONFIG_PARAM_MEMEXSERVERPORT));
    characterEncoding = configParams.getParameter(CONFIG_PARAM_CHARACTERENCODING);
    String userNameMapping = configParams.getParameter(CONFIG_PARAM_USERNAMEMAPPING);
    if (userNameMapping == null)
      userNameMapping = "^([^\\\\@]*).*$=$(1)";
    matchMap = new MatchMap(userNameMapping);
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  /** Check connection for sanity.
  */
  public String check()
    throws LCFException
  {
    try{
      this.setupConnection();
      return super.check();
    }
    catch (ServiceInterruption e){
      return "Transient error connecting to Memex: "+e.getMessage();
    }
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  public void poll()
    throws LCFException
  {
    // Is the connection still valid?
    if (this.physicalServers != null && !this.physicalServers.isEmpty())
    {
      // Yes: see if it is time to shut it down yet
      if (connectionExpirationTime < System.currentTimeMillis())
      {
        // Expire all the connection information
        this.cleanUpConnections();

      }
    }
  }

  /** Close the connection.  Call this before discarding the repository connector.
  */
  public void disconnect()
    throws LCFException
  {
    matchMap = null;
    this.cleanUpConnections();
    userName = null;
    userPassword = null;
    hostName = null;
    hostPort = -1;
    characterEncoding = null;
    super.disconnect();
  }

  /** Obtain the access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the response tokens (according to the current authority).
  * (Should throws an exception only when a condition cannot be properly described within the authorization response object.)
  */
  public AuthorizationResponse getAuthorizationResponse(String userName)
    throws LCFException
  {

    Hashtable<String,Hashtable<String, String>> userDBList = new Hashtable<String,Hashtable<String, String>>();
    ArrayList<String> dbPrefixes = new ArrayList<String>();
    ArrayList<String> keyCombinations = new ArrayList<String>();

    // Map the incoming user name to the Memex user name
    // Use the matchMap object to do the translation
    userName = matchMap.translate(userName);

    if (Logging.authorityConnectors.isDebugEnabled())
      Logging.authorityConnectors.debug("Memex: User name to lookup = '"+userName+"'");

    //start by making sure we have a connection
    try{
      this.setupConnection();
    }
    catch(ServiceInterruption mex){
      //something transient's gone wrong connecting.  Throw a LCFException (which will be caught, and the appropriate default behavior instigated).
      Logging.authorityConnectors.warn("Memex: Transient authority error: "+mex.getMessage(),mex.getCause());
      throw new LCFException(mex.getMessage(),mex.getCause());
    }

    //Next - search for the user's record in the mxUserGroup database

    //Start by locating the mxUserGroup database on the Config Server
    try{
      String mxUserGroupPath = "";
      if(mieConnection.localRegistry != null){
        int i;
        int dbcount = mieConnection.localRegistry.length;
        for(i=0;i<dbcount;i++){
          if(mieConnection.localRegistry[i].getName().contains("mxUserGroup")){
            mxUserGroupPath = mieConnection.localRegistry[i].getPath();
            break;
          }
        }
        if(!(mxUserGroupPath.equals(""))){

          //start by building a list of all role groups from the mxUserGroupPath db and create a connection
          //to any remote physical servers.

          int hist = 0;
          int numHits = 0;
          SearchStatus userSearch = mieConnection.mie.mxie_search(mxUserGroupPath, "(user)$type (" + userName + ")$name !(*DELETED*)$name !(null)$auditserver", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, hist);
          int searchHits = userSearch.getNumberOfHits();
          if (searchHits < 0) {
            //If the number of hits is less than zero, something went wrong
            Logging.authorityConnectors.warn("Memex: User search hits < 0! ("+Integer.toString(searchHits)+")");
            return new AuthorizationResponse(new String[] {MemexConnector.defaultAuthorityDenyToken}, AuthorizationResponse.RESPONSE_UNREACHABLE);
          }else if(searchHits == 0){
            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.debug("Memex: User '"+userName+"' not found");
            return new AuthorizationResponse(new String[] {MemexConnector.defaultAuthorityDenyToken}, AuthorizationResponse.RESPONSE_USERNOTFOUND);
          }else if(searchHits > 1){
            //This shouldn't happen - should only be one entry per user
            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.warn("Memex: Multiple entries found for '"+userName+"'!");
            throw new LCFException("Memex Error retrieving user information : multiple entries found for user " + userName);
          }else{
            //OK - we found the user - we need four fields from thier record - groups, servers, attributes and keys
            if (Logging.authorityConnectors.isDebugEnabled())
              Logging.authorityConnectors.debug("Memex: User '"+userName+"' found.");

            ArrayList<DecodedField> userFields = new ArrayList<DecodedField>();
            userFields.add(new DecodedField(userSearch.getHistory(), 1, 5, 10000)); //groups
            userFields.add(new DecodedField(userSearch.getHistory(), 1, 6, 10000)); //attributes
            userFields.add(new DecodedField(userSearch.getHistory(), 1, 8, 10000)); //servers
            userFields.add(new DecodedField(userSearch.getHistory(), 1, 17, 10000)); //keys
            //mieConnection.mie.mxie_goto_record(userSearch.getHistory(), 1);
            mieConnection.mie.mxie_decode_fields(userFields);

            String groups = userFields.get(0).getText();
            String attributes = userFields.get(1).getText();
            String servers = userFields.get(2).getText();
            String keys = userFields.get(3).getText();

            //Now we've found the user, work out what databases they have access to
            if(attributes.contains("super-user")){
              //This user has super-user permissions so
              //they have search access to all databases
              if (Logging.authorityConnectors.isDebugEnabled())
                Logging.authorityConnectors.debug("Memex: User '"+userName+"' has super-user permissions.");
              userDBList = prefixList;
            }else{
              //We need to parse their DB permissions - note in many set-ups,
              //individual users WILL NOT have any DB permissions.
              if (Logging.authorityConnectors.isDebugEnabled())
                Logging.authorityConnectors.debug("Memex: User '"+userName+"' does not have super-user permissions.");

              if((servers != null)&&(!(servers.equals("")))){
                userDBList = this.parseDBPermissions(servers, prefixList);
              }
              //Now get permissions from thier role group memberships
              String[] usergroups = groups.split(",");
              for(int x = 0; x < usergroups.length; x++){
                Hashtable<String,Hashtable<String, String>> groupDBList = roleGroups.get(usergroups[x]);
                if(groupDBList != null){
                  for(Enumeration serverkeys = groupDBList.keys(); serverkeys.hasMoreElements();){
                    String serverName = (String)serverkeys.nextElement();
                    if(!(userDBList.containsKey(serverName))){
                      //We've not previously found any permissions for this user on this server
                      userDBList.put(serverName, groupDBList.get(serverName));
                    }else{
                      //This user already has some permissions for this server so only add additional
                      //permissions.
                      for(Enumeration dbkeys = groupDBList.get(serverName).keys() ; dbkeys.hasMoreElements();){
                        String dbName = (String)dbkeys.nextElement();
                        if(!(userDBList.get(serverName).containsKey(dbName))){
                          userDBList.get(serverName).put(dbName, groupDBList.get(serverName).get(dbName));
                        }
                      }
                    }
                  }
                }
              }
            }
            if((userDBList != null)&&(!(userDBList.isEmpty()))){
              //Create an array list of all the database prefixes this user has access to.
              for(Enumeration serverkeys = userDBList.keys(); serverkeys.hasMoreElements();){
                String serverName = (String)serverkeys.nextElement();
                for(Enumeration dbkeys = userDBList.get(serverName).keys() ; dbkeys.hasMoreElements();){
                  String dbName = (String)dbkeys.nextElement();
                  dbPrefixes.add(userDBList.get(serverName).get(dbName));
                  if (Logging.authorityConnectors.isDebugEnabled())
                    Logging.authorityConnectors.debug("Memex:   User '"+userName+"' has access to database "+userDBList.get(serverName).get(dbName)+".");

                }
              }
            }

            //At this point we have all the databases the user has access to. Now we need a list of
            //keys.
            //We need each key individually and each possible two key combination (to deal with records
            //that are both protected and locked).
            String userKeys[] = keys.split(",");
            //Discard the read/write/search attributes
            for(int keynum = 0; keynum < userKeys.length; keynum++){
              userKeys[keynum] = userKeys[keynum].substring(0, userKeys[keynum].indexOf("("));
            }
            //Now add the keys to our array list - individually and then in combination with each other key
            for(int keynum = 0; keynum < userKeys.length; keynum++){
              keyCombinations.add(userKeys[keynum]);
              for(int secondkey = 0; secondkey < userKeys.length; secondkey++){
                if(keynum != secondkey){
                  keyCombinations.add(userKeys[keynum] + "-" + userKeys[secondkey]);
                }
              }
            }


            //We now have a list of all databases and all key combinations. The final
            //ACL list for this user will be the product of the two lists.
            String[] userACLList = new String[keyCombinations.size() * dbPrefixes.size() + 1];
            int aclCount = 0;
            for(int d = 0; d < dbPrefixes.size(); d++){
              for(int k = 0; k < keyCombinations.size(); k++){
                String accessToken = dbPrefixes.get(d) + "-" + keyCombinations.get(k);
                // Memex is case-insensitive, but we are not, so make sure we don't screw up for that reason.
                userACLList[aclCount] = accessToken.toUpperCase();
                if (Logging.authorityConnectors.isDebugEnabled())
                  Logging.authorityConnectors.debug("Memex: User '"+userName+"' has access token "+userACLList[aclCount]+".");
                aclCount++;
              }
            }
            // Each user also gets the default key.
            userACLList[aclCount] = "DEFAULT-GRANT";
            return new AuthorizationResponse(userACLList, AuthorizationResponse.RESPONSE_OK);
          }
        }else{
          //This is bad - we're connected, but we can't find the UserGroup database
          Logging.authorityConnectors.error("Memex: Can't locate UserGroup database.");
          throw new LCFException("Memex Error: Can't locate the mxUserGroup Database");
        }
      }else{
        //This is bad as well - we're connected but didn't find any registry entries
        Logging.authorityConnectors.error("Memex: Configuration Server's registry appears to be null.");
        throw new LCFException("Memex Error: Configuration Server's registry is null");
      }
    }catch(MemexException me){
      //something threw an error - most likely a connection issue.
      // LCFExceptions will be handled by getting the default authorization response instead.
      Logging.authorityConnectors.warn("Memex: Unknown error calculating user access tokens: "+me.getMessage(),me);
      throw new LCFException("Memex transient error: "+me.getMessage(),me);
    }
  }

  /** Obtain the default access tokens for a given user name.
  *@param userName is the user name or identifier.
  *@return the default response tokens, presuming that the connect method fails.
  */
  public AuthorizationResponse getDefaultAuthorizationResponse(String userName)
  {
    // The default response if the getConnection method fails
    return new AuthorizationResponse(new String[] {MemexConnector.defaultAuthorityDenyToken}, AuthorizationResponse.RESPONSE_UNREACHABLE);
  }

  //////////////////////////////////////////////////////////////////////
  //
  //Method that looks to see if a connection has been established and
  //if so, is it still valid. If not, creates / recreates the connection
  //
  ///////////////////////////////////////////////////////////////////////
  public void setupConnection()
    throws LCFException, ServiceInterruption
  {
    boolean connected = false;
    if((this.physicalServers != null) && !(this.physicalServers.isEmpty())){
      //If we have entries in the physical server collection, check they are all connected
      connected = true;
      for(Enumeration serverkeys = physicalServers.keys(); serverkeys.hasMoreElements();){
        String serverkey = (String)serverkeys.nextElement();
        LCFMemexConnection pserver = physicalServers.get(serverkey);
        if(!(pserver.isConnected())){
          connected = false;
        }
      }
      if (!connected)
        //Clear any existing connections
        this.cleanUpConnections();
    }

    if(!connected){
      try{

        miePool.setUsername(userName);
        miePool.setPassword(userPassword);
        miePool.setHostname(hostName);
        miePool.setPort(hostPort);
        miePool.setCharset(characterEncoding);

        //Initialise data structures
        mieConnection = new LCFMemexConnection();
        logicalServers = new Hashtable<String, LogicalServer>();
        logicalServersByPrefix = new Hashtable<String, LogicalServer>();
        physicalServers = new Hashtable<String, LCFMemexConnection>();
        entitiesByName = new Hashtable<String, MemexEntity>();
        entitiesByLabel = new Hashtable<String, MemexEntity>();
        entitiesByPrefix = new Hashtable<String, MemexEntity>();

        //Start out creating a connection to the Configuration Server.
        mieConnection.mie = miePool.getSystemConnection();
        Registry reg = mieConnection.mie.mxie_dbreg_init();
        mieConnection.localRegistry = mieConnection.mie.mxie_dbreg_list(reg);
        mieConnection.mie.mxie_dbreg_close(reg);
        reg = null;

        //Add the configuration server as the first entry in the physical servers collection.
        //There may be more physical servers - we'll discover this later with a call to getServers
        String key = miePool.getHostname() + ":" + Integer.toString(miePool.getPort());
        mieConnection.name = key;
        physicalServers.put(key, mieConnection);
        mieConnection.ConnectionMessage = "Connection to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " created";

        //Create a collection of data structures describing the entities in this set-up
        this.getEntities();

        //Create a collection of data structures describing each physical server in this set up. The
        //configuration server has laready been added.
        this.getServers();

        //Create logical server / database prefixes
        prefixList = this.initialisePrefixes();

        //Now get a list of databases each role group has access to
        roleGroups = this.initialiseRoleGroups();
      }
      catch(PoolAuthenticationException e){
        throw new LCFException("Authentication failure connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort())+": "+e.getMessage(),e);
      }
      catch(PoolException e){
        Logging.authorityConnectors.warn("Memex: Pool error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying",e);
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Pool error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying",
          e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
      catch(MemexException e){
        Logging.authorityConnectors.warn("Memex: Memex error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying",e);
        long currentTime = System.currentTimeMillis();
        throw new ServiceInterruption("Memex error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying",
          e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
    }

    connectionExpirationTime = System.currentTimeMillis() + CONNECTION_IDLE_INTERVAL;

  }

  /////////////////////////////////////////////////////
  //
  //Method disconnects any existing MIE connections
  //and sets datastructures to null
  //
  /////////////////////////////////////////////////////
  private void cleanUpConnections(){

    //Clear any existing connections
    if(physicalServers != null){
      // Don't want to remove things while enumerating, so build a list first
      String[] serverKeyArray = new String[physicalServers.size()];
      int i = 0;
      for(Enumeration serverkeys = physicalServers.keys(); serverkeys.hasMoreElements();){
        String serverkey = (String)serverkeys.nextElement();
        serverKeyArray[i++] = serverkey;
      }

      while (i < serverKeyArray.length)
      {
        String serverkey = serverKeyArray[i++];
        LCFMemexConnection currentMIE = physicalServers.get(serverkey);
        try{
          // Remove history directories belonging to this session
          physicalServers.remove(serverkey);
          String histdir = currentMIE.mie.mxie_history_current();
          currentMIE.mie.mxie_history_close();
          currentMIE.mie.mxie_svrfile_rmdir(histdir, true);
          currentMIE.mie.mxie_connection_logout();
          currentMIE.mie.mxie_connection_shutdown();
        }
        catch(MemexException e){
          Logging.authorityConnectors.warn("Memex exception logging out virtual server "+serverkey+": "+e.getMessage(),e);
        }
      }
      mieConnection = null;
      miePool.close();
      logicalServers = null;
      logicalServersByPrefix = null;
      physicalServers = null;
      entitiesByName = null;
      entitiesByLabel = null;
      entitiesByPrefix = null;
      roleGroups = null;
      prefixList = null;
      connectionExpirationTime = -1L;

      for(Enumeration serverkeys = physicalServers.keys(); serverkeys.hasMoreElements();){
        String serverkey = (String)serverkeys.nextElement();
        LCFMemexConnection currentMIE = physicalServers.get(serverkey);
        try{
          // Remove history directories belonging to this session
          String histdir = currentMIE.mie.mxie_history_current();
          currentMIE.mie.mxie_history_close();
          currentMIE.mie.mxie_svrfile_rmdir(histdir, true);
          currentMIE.mie.mxie_connection_logout();
          currentMIE.mie.mxie_connection_shutdown();
          physicalServers.remove(serverkey);
        }
        catch(MemexException e){
          Logging.authorityConnectors.warn("Memex exception logging out virtual server "+serverkey+": "+e.getMessage(),e);
        }
      }
    }
  }

  /**Creates an alphabetically ordered list of entity objects.
  */
  private void getEntities()
    throws MemexException
  {
    String mxEntityPath = null;

    //Start by locating the mxEntity database on the Config Server

    if(mieConnection.localRegistry != null){
      Map<String,RegistryEntry> registryMap = new HashMap<String,RegistryEntry>();

      int i;
      int dbcount = mieConnection.localRegistry.length;
      for(i=0;i<dbcount;i++){
        RegistryEntry re = mieConnection.localRegistry[i];
        String name = re.getName();
        if (name != null)
        {
          registryMap.put(name.substring(name.indexOf(".")+1),re);
          name = name.substring(0,name.indexOf("."));
          if(name.equals("mxEntity")){
            mxEntityPath = re.getPath();
          }
        }
      }
      if(mxEntityPath != null && !mxEntityPath.equals("")){
        //get all entries from the mxEntity db and create an mxEntity
        //object for each

        int hist = 0;
        int numHits = 0;
        SearchStatus entitySearch = mieConnection.mie.mxie_search(mxEntityPath, "e|!e", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, hist);
        if (entitySearch.getNumberOfHits() < 0) {
          throw new MemexException("Memex Error retrieving entity information : " + mieConnection.mie.mxie_error());
        }else{
          hist = entitySearch.getHistory();
          for(int x = 1; x <= entitySearch.getNumberOfHits(); x++){
            //Field 2 is the server name in the mxServer database
            ArrayList entityFields = new ArrayList();
            DecodedField entityURN = new DecodedField(hist, x, 1, 100);
            DecodedField entityfields = new DecodedField(hist, x, 2, 100000);
            DecodedField entityprefix = new DecodedField(hist, x, 8, 100);
            DecodedField entityname = new DecodedField(hist, x, 10, 100);
            DecodedField entitylabels = new DecodedField(hist, x, 33, 100000);
            DecodedField entitydisplayname = new DecodedField(hist, x, 40, 100);
            entityFields.add(entityURN);
            entityFields.add(entityfields);
            entityFields.add(entityprefix);
            entityFields.add(entityname);
            entityFields.add(entitylabels);
            entityFields.add(entitydisplayname);
            mieConnection.mie.mxie_decode_fields(entityFields);

            String entityNameString = entityname.getText();
            String entityURNString = entityURN.getText();

            if (entityNameString != null && entityNameString.length() > 0)
            {
              RegistryEntry regEntry = registryMap.get(entityURNString);
              if (regEntry != null)
              {
                MemexEntity ent = new MemexEntity(entityNameString, entityURNString, entityprefix.getText(), entitydisplayname.getText(), entityfields.getText(), entitylabels.getText());
                entitiesByName.put(ent.getName(), ent);
                entitiesByPrefix.put(ent.getPrefix(), ent);
                entitiesByLabel.put(ent.getDisplayName(), ent);
              }
            }
          }
        }
      }
    }
  }

  /**Creates a list of logical server objects.
  *Configuration Server is always the first entry in the list, all other
  *server are listed alphabetically thereafter
  */
  private void getServers()
    throws MemexException
  {

    String mxServerPath = null;

    //Start by locating the mxServer database on the Config Server
    if (mieConnection.localRegistry != null)
    {
      int i = 0;
      while (i < mieConnection.localRegistry.length)
      {
        RegistryEntry regEntry = mieConnection.localRegistry[i++];
        String entityName = regEntry.getName();
        if (entityName != null && entityName.length() > 0)
        {
          entityName = entityName.substring(0,entityName.indexOf("."));
          if (entityName.equals("mxServer"))
          {
            mxServerPath = regEntry.getPath();
            break;
          }
        }
      }
      if(mxServerPath!= null && !mxServerPath.equals("")){
        //get all entiries from the mxServer db and create a connection
        //to any remote physical servers.

        int hist = 0;
        int numHits = 0;
        SearchStatus serverSearch = mieConnection.mie.mxie_search(mxServerPath, "e|!e", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, hist);
        if (serverSearch.getNumberOfHits() < 0) {
          throw new MemexException("Memex Error retrieving Server information : " + mieConnection.mie.mxie_error());
        }else{
          for(int x = 1; x <= serverSearch.getNumberOfHits(); x++){
            //Field 2 is the server name in the mxServer database
            ArrayList serverFields = new ArrayList();
            DecodedField servername = new DecodedField(hist, x, 2, 100);
            DecodedField serverprefix = new DecodedField(hist, x, 3, 100);
            DecodedField serversource = new DecodedField(hist, x, 5, 100);
            serverFields.add(servername);
            serverFields.add(serverprefix);
            serverFields.add(serversource);
            //mieConnection.mie.mxie_goto_record(hist, x);
            mieConnection.mie.mxie_decode_fields(serverFields);
            LCFMemexConnection mie;
            if(serversource.getText().equals("configuration-server")){
              mie = mieConnection;
            }else{
              //this logical server lives on a remote physical server
              //extract the port and server strings from the source
              String[] source = serversource.getText().split("\n");
              String remoteserver = source[2].substring(4);
              String remoteport = source[3].substring(5);
              mie = getPhysicalServer(remoteserver, Integer.parseInt(remoteport));
            }
            //Now create a list of databases on this server
            LogicalServer ls = new LogicalServer(servername.getText(),serverprefix.getText(),mie,entitiesByName);
            logicalServers.put(ls.getServerName(), ls);
            logicalServersByPrefix.put(ls.getPrefix(), ls);
          }
        }
      }
    }
  }

  /**
  * Creates a list containing the SERVER/ENTITY prefix for each database
  * on each logical server that a role group has search access to
  */
  private Hashtable<String, Hashtable<String,Hashtable<String, String>>> initialiseRoleGroups()
    throws MemexException
  {

    Hashtable<String, Hashtable<String,Hashtable<String, String>>> lclRoleGroups = new Hashtable<String, Hashtable<String,Hashtable<String, String>>>();

    String mxUserGroupPath = null;

    //Start by locating the mxUserGroup database on the Config Server
    if(mieConnection.localRegistry != null){
      int i;
      int dbcount = mieConnection.localRegistry.length;
      for(i=0;i<dbcount;i++){
        if(mieConnection.localRegistry[i].getName().contains("mxUserGroup")){
          mxUserGroupPath = mieConnection.localRegistry[i].getPath();
          break;
        }
      }
      if(mxUserGroupPath != null && !mxUserGroupPath.equals("")){

        //start by building a list of all role groups from the mxUserGroupPath db and create a connection
        //to any remote physical servers.

        int hist = 0;
        int numHits = 0;
        SearchStatus serverSearch = mieConnection.mie.mxie_search(mxUserGroupPath, "(role group)$type !(*DELETED*)$name", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, hist);
        if (serverSearch.getNumberOfHits() < 0) {
          throw new MemexException("Memex Error retrieving Role Group information : " + mieConnection.mie.mxie_error());
        }else{
          for(int x = 1; x <= serverSearch.getNumberOfHits(); x++){
            //We need three fields for each group - name, servers and attributes
            ArrayList<DecodedField> serverFields = new ArrayList<DecodedField>();
            serverFields.add(new DecodedField(serverSearch.getHistory(), x, 2, 10000)); //Groupname
            serverFields.add(new DecodedField(serverSearch.getHistory(), x, 6, 10000)); //attributes
            serverFields.add(new DecodedField(serverSearch.getHistory(), x, 8, 10000)); //servers
            //mieConnection.mie.mxie_goto_record(serverSearch.getHistory(), x);
            mieConnection.mie.mxie_decode_fields(serverFields);

            String Groupname = serverFields.get(0).getText();
            String attributes = serverFields.get(1).getText();
            String servers = serverFields.get(2).getText();

            if(attributes.contains("super-user")){
              //This role group has super-user permissions so
              //they have search access to all databases
              lclRoleGroups.put(Groupname, prefixList);
            }else{
              //We need to parse their DB permissions
              Hashtable<String,Hashtable<String, String>> dbList = this.parseDBPermissions(servers, prefixList);
              if((dbList != null)&&(!(dbList.isEmpty()))){
                lclRoleGroups.put(Groupname, dbList);
              }
            }

          }
        }
      }else{
        throw new MemexException("Memex Error: Can't locat the mxUserGroup Database");
      }
    }else{
      throw new MemexException("Memex Error: Configuration Server's registry is null");
    }
    if((lclRoleGroups != null)&&(!(lclRoleGroups.isEmpty()))){
      return lclRoleGroups;
    }else{
      return null;
    }
  }


  /**
  * Takes a string representation of a user or group's database
  * permissions from the mxUserGroup databaes and parses it as XML.
  * Returns a list broken down by server and database of the URN prefixes
  * of all databases the user or group has search access to.
  */

  private Hashtable<String,Hashtable<String, String>> parseDBPermissions(String servers, Hashtable<String,Hashtable<String, String>> fullList)
    throws MemexException
  {

    Hashtable<String,Hashtable<String, String>> returnHash = new Hashtable<String,Hashtable<String, String>>();

    try{
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

      //Using factory get an instance of document builder
      DocumentBuilder db = dbf.newDocumentBuilder();

      //Convert the servers string to an inputStream
      byte[] serversBytes = servers.getBytes();
      ByteArrayInputStream is = new ByteArrayInputStream(serversBytes);
      Document xmlServers = db.parse(is);

      //First check to see if this group has WORLD=ALL access
      NodeList world_nodes = xmlServers.getElementsByTagName("WORLD");
      for(int i = 0; i < world_nodes.getLength(); i++) {
        Node node = world_nodes.item(i);
        NamedNodeMap attributes = node.getAttributes();
        if(attributes != null){
          Node all = attributes.getNamedItem("ALL");
          if((all != null)&&(all.getNodeValue().equals("TRUE"))){
            //This group has WORLD=ALL access
            return fullList;
          }
        }
      }

      //If not, look at each server in turn
      NodeList server_nodes = xmlServers.getElementsByTagName("SERVER");
      for(int x = 0; x < server_nodes.getLength(); x++) {
        //Do we have 'All' access to this server
        Node node = server_nodes.item(x);
        NamedNodeMap attributes = node.getAttributes();
        if(attributes != null){
          String serverName = attributes.getNamedItem("NAME").getNodeValue();
          Node all = attributes.getNamedItem("ALL");
          if((all != null)&&(all.getNodeValue().equals("TRUE"))){
            //This group has ALL=TRUE access to this server
            Hashtable<String, String> serverDBS = fullList.get(serverName);
            if(serverDBS != null){
              returnHash.put(serverName, serverDBS);
            }
          }else{
            //We need to inspect each database individually
            Hashtable<String, String> serverDBS = new Hashtable<String, String>();
            NodeList db_nodes = node.getChildNodes();
            for(int y = 0; y < db_nodes.getLength(); y++) {
              Node db_node = db_nodes.item(y);
              if(db_node.getNodeName().equals("DATABASE")){
                NamedNodeMap db_attributes = db_node.getAttributes();
                String db_prefix = db_attributes.getNamedItem("PREFIX").getNodeValue();
                Node db_search = db_attributes.getNamedItem("SEARCH");
                if((db_search == null)||(db_search.getNodeValue().equals("TRUE"))){
                  //We have search permission over this databases
                  MemexEntity dbEnt = entitiesByPrefix.get(db_prefix.substring(2));
                  if(dbEnt != null){
                    serverDBS.put(dbEnt.getName(), db_prefix);
                  }
                }
              }
            }
            if(!(serverDBS.isEmpty())){
              returnHash.put(serverName, serverDBS);
            }
          }
        }
      }

    }catch(SAXException e){
      throw new MemexException("Memex Error: " + e.getMessage());
    }catch(IOException e2){
      throw new MemexException("Memex Error: " + e2.getMessage());
    }catch(ParserConfigurationException e3) {
      throw new MemexException("Memex Error: " + e3.getMessage());
    }
    if(returnHash.isEmpty()){
      return null;
    }else{
      return returnHash;
    }
  }
  /**Creates a list containing the SERVER/ENTITY prefix for each database
  * on each logical server
  */
  private Hashtable<String,Hashtable<String, String>> initialisePrefixes()
    throws MemexException
  {
    Hashtable<String,Hashtable<String, String>> lclPrefixList = new Hashtable<String,Hashtable<String, String>>();

    for(Enumeration<LogicalServer> lsEnum = logicalServers.elements(); lsEnum.hasMoreElements();){
      LogicalServer ls = lsEnum.nextElement();
      String lsPrefix = ls.getPrefix();
      Hashtable<String, String> lsDBList = new Hashtable<String, String>();
      if(ls.getDatabaseCount() > 0){
        for(int i = 0; i < ls.getDatabaseCount(); i++){
          String dbname = ls.getDatabase(i).getName();
          dbname = dbname.substring(0, dbname.indexOf("."));
          //Get the entity prefix for this database
          MemexEntity dbEnt = entitiesByName.get(dbname);
          if(dbEnt != null){
            lsDBList.put(dbEnt.getName(), lsPrefix + dbEnt.getPrefix());
          }
        }
        lclPrefixList.put(ls.getServerName(), lsDBList);
      }
    }
    return lclPrefixList;
  }



  private LCFMemexConnection getPhysicalServer(String server, int port){

    String key = server + ":" + Integer.toString(port);

    if(physicalServers.containsKey(key)){
      return (LCFMemexConnection)physicalServers.get(key);
    }else{
      LCFMemexConnection newServer = new LCFMemexConnection();
      try{
        MemexConnection newMIE = miePool.getConnection(server, port);
        newServer.mie = newMIE;
        Registry reg = newServer.mie.mxie_dbreg_init();
        newServer.localRegistry = newServer.mie.mxie_dbreg_list(reg);
        newServer.mie.mxie_dbreg_close(reg);
        reg = null;
        newServer.ConnectionMessage = "Connection to Memex Server " + server + ":" + Integer.toString(port) + " created";
        newServer.name = key;
        physicalServers.put(key, newServer);
        return newServer;
      }
      catch(PoolAuthenticationException e){
        newServer.ConnectionMessage = "Authentication failure connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort());
        newServer.mie = null;
        newServer.localRegistry = null;
        physicalServers.put(key, newServer);
        return newServer;
      }
      catch(PoolException e){
        newServer.ConnectionMessage = "Error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage();
        newServer.mie = null;
        newServer.localRegistry = null;
        physicalServers.put(key, newServer);
        return newServer;
      }
      catch(MemexException e){
        newServer.ConnectionMessage = "Error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage();
        newServer.mie = null;
        newServer.localRegistry = null;
        physicalServers.put(key, newServer);
        return newServer;
      }
    }
  }

}


