/* $Id: MemexSupport.java 921329 2010-03-10 12:44:20Z kwright $ */

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
package org.apache.acf.crawler.connectors.memex;

import org.apache.acf.core.interfaces.*;
import org.apache.acf.agents.interfaces.*;
import org.apache.acf.crawler.interfaces.*;
import org.apache.acf.crawler.system.Logging;

import java.io.*;
import java.util.*;
import java.net.InetAddress;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;


import java.text.SimpleDateFormat;
import com.memex.mie.*;
import com.memex.mie.pool.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** This is a placeholder for the Memex code for testing the Memex connector.
*
*/
public class MemexSupport
{
        public static final String _rcsid = "@(#)$Id: MemexSupport.java 921329 2010-03-10 12:44:20Z kwright $";

        //mieConnection is the connection to the main Configuration Server.
        //There will be further ACFMemexConnection objects for each
        //physical server accessed through the physicalServers collection.
        private ACFMemexConnection mieConnection = null;
        private MemexConnectionPool miePool = new MemexConnectionPool();

        //Collection describing the logical servers making up this system
        private Hashtable<String, LogicalServer> logicalServers = null;
        private Hashtable<String, LogicalServer> logicalServersByPrefix = null;

        //Collection describing the physical servers making up this system
        private Hashtable<String, ACFMemexConnection> physicalServers = null;

        //Two collections describing the entities in the set-up - one keyed by the entities' name, the other
        //by their label - generally speaking, we should use labels for anything being presented to the users
        //as this is what they are used to seeing within Patriarch.
        private Hashtable<String, MemexEntity> entitiesByName = null;
        private Hashtable<String, MemexEntity> entitiesByLabel = null;
        private Hashtable<String, MemexEntity> entitiesByPrefix = null;

        public Hashtable<String, String> securityLocks = null;

        private final static long CONNECTION_IDLE_INTERVAL = 900000L;
        // Connection expiration time
        private long connectionExpirationTime = -1L;

        // Internal values, probably should be arguments sometime
        String serverTimezone = "GMT";
        String characterEncoding = "windows-1252";

        /** Constructor.  Self-explanatory.
        */
        public MemexSupport(String memexUserID, String memexPassword, String memexServerName, String memexServerPort)
                throws ACFException
        {
                miePool.setUsername(memexUserID);
                miePool.setPassword(memexPassword);
                miePool.setHostname(memexServerName);
                miePool.setPort(Integer.parseInt(memexServerPort));
                miePool.setCharset(characterEncoding);
        }

        /** Add a record to the repository.
        * Feel free to change these arguments as needed.
        *@param keyValuePairs is the set of fieldname/value pairs to set
        *@return a string identifier that can be used to identify the record uniquely
        */
        public String addRecord(Hashtable keyValuePairs, String server, String database)
                throws ACFException
        {
                try{
                        this.setupConnection();
                }catch(Exception e){
                        throw new ACFException(e.getMessage(),e);
                }

                //Check the logical server exists
                LogicalServer ls = logicalServers.get(server);
                if(ls == null){
                        throw new ACFException("Memex Error adding a record - logical server " + server + " cannot be found");
                }

                //Check the entity exists
                MemexEntity ent = entitiesByPrefix.get(database);
                if(ent == null){
                        throw new ACFException("Memex Error adding a record - entity " + database + " cannot be found");
                }

                //Check there is a database of the selected entity type on the selected server
                boolean found = false;
                RegistryEntry re = null;
                for(int i = 0; i < ls.getDatabaseCount(); i++){
                        if(ls.getDatabase(i).getName().contains(ent.getURN())){
                                found = true;
                                re = ls.getDatabase(i);
                                break;
                        }
                }
                if(found == false){
                        throw new ACFException("Memex Error adding a record - entity " + database + " not found on server " + server);
                }

                //Build up the minimum fields for a Memex record

                String prefix = ls.getPrefix() + ent.getPrefix();
                String sysurn = "";
                try{
                        sysurn = ls.getMIE().mie.mxie_database_uniq_id(prefix, re.getPath());
                        while(sysurn.length() < 12){
                                sysurn = prefix + "0" + sysurn.substring(4);
                        }
                }catch(MemexException e){
                        throw new ACFException("Memex Error adding record - " + e.getMessage(),e);
                }
                if(sysurn.equals("")){
                        throw new ACFException("Memex Error adding record - failed to get a urn");
                }

                long epochtime = System.currentTimeMillis();
                String sysdatecreated = this.getMemexDate(epochtime);
                String systimecreated = this.getMemexTime(epochtime);

                String syscreatedby = miePool.getUsername();
                if(keyValuePairs.containsKey("syscreatedby")){
                        syscreatedby = (String)keyValuePairs.get("syscreatedby");
                }
                String covertemail = syscreatedby + " - " + syscreatedby;
                if(keyValuePairs.containsKey("covertemail")){
                        covertemail = (String)keyValuePairs.get("covertemail");
                }
                String reviewemail = covertemail;
                if(keyValuePairs.containsKey("reviewemail")){
                        reviewemail = (String)keyValuePairs.get("reviewemail");
                }
                String supstatus = "SUBMITTED";
                if(keyValuePairs.containsKey("supstatus")){
                        supstatus = (String)keyValuePairs.get("supstatus");
                }

                String recordtext = "rrrrrrrr";
                String locks = (String)keyValuePairs.get("metainfo_clocks");
                if(locks != null){
                        recordtext += locks;
                }
                recordtext += "\nxxsysurn\n" + sysurn + "\n";
                recordtext += "xxsyswithheldmsg\n";
                if(keyValuePairs.get("syswithheldmsg") != null){
                        recordtext += (String)keyValuePairs.get("syswithheldmsg") + "\n";
                }
                recordtext += "xxsysprotectedinfo\n";
                if(keyValuePairs.get("sysprotectedinfo") != null){
                        recordtext += (String)keyValuePairs.get("sysprotectedinfo") + "\n";
                }   
                locks = (String)keyValuePairs.get("metainfo_plocks");
                if(locks != null){
                        recordtext += "ssssssss" + locks;
                }else{
                        recordtext += "ssssssss";
                }
                recordtext += "\nxxsysdatecreated\n" + sysdatecreated + "\n";
                recordtext += "xxsystimecreated\n" + systimecreated + "\n";
                recordtext += "xxsyscreatedby\n" + syscreatedby + "\n";
                recordtext += "xxsysdateupdated\n";
                recordtext += "xxsystimeupdated\n";
                recordtext += "xxsysupdatedby\n";
                recordtext += "xxcovertemail\n" + covertemail + "\n";
                recordtext += "xxreviewemail\n" + reviewemail + "\n";
                recordtext += "xxsupstatus\n" + supstatus + "\n";

                //OK - got the bare minimum;

                //Now add passed in fields
                for(Enumeration e = keyValuePairs.keys(); e.hasMoreElements();){
                        String key = (String)e.nextElement();
                        if(!(key.equals("syscreatedby"))&&!(key.equals("covertemail"))&&!(key.equals("reviewemail"))&&!(key.equals("supstatus"))&&!(key.equals("subrecords"))){
                                recordtext += "xx" + key + "\n" + (String)keyValuePairs.get(key) + "\n";
                        }
                }

                //Deal with subrecords
                Hashtable<String, ArrayList> subrecords = (Hashtable<String, ArrayList>)keyValuePairs.get("subrecords");
                if(subrecords != null){
                        for(Enumeration e = subrecords.keys(); e.hasMoreElements();){
                                String subname = (String)e.nextElement();
                                for(int x = 0; x < subrecords.get(subname).size(); x++){
                                        Hashtable<String, String> subfields = (Hashtable<String, String>)subrecords.get(subname).get(x);
                                        recordtext += "cccccccc\nxxsyscategory\n" + subname + "\n";
                                        for(Enumeration s = subfields.keys(); s.hasMoreElements();){
                                                String fieldname = (String)s.nextElement();
                                                recordtext += "xx" + fieldname + "\n" + subfields.get(fieldname) + "\n";
                                        }
                                }
                        }
                }

                //OK - got the record. Now add it to the database.
                try{
                        String appendFile = ls.getMIE().mie.mxie_svrfile_tmpname();
                        OutputStream OStream = null;
                        OStream = ls.getMIE().mie.mxie_svrfile_write(appendFile);
                        OStream.write(recordtext.getBytes());
                        OStream.close();
                        ls.getMIE().mie.mxie_append_text(appendFile, re.getPath(), 128000);
                        ls.getMIE().mie.mxie_svrfile_remove(appendFile);

                        //Now create an audit record
                        String auditRecord = "rrrrrrrr\nxxtype\nAPPEND\n";
                        auditRecord += "xxrecordurn\n" + sysurn + "\n";
                        auditRecord += "xxcreatedate\n" + sysdatecreated +"\n";
                        auditRecord += "xxcreatetime\n" + systimecreated +"\n";
                        auditRecord += "xxcreatedby\n" + miePool.getUsername() +"\n";
                        auditRecord += "xxsessionid\n";
                        auditRecord += "xxfullname\nACF Support Program\n";
                        auditRecord += "xxusername\n" + miePool.getUsername() +"\n";
                        auditRecord += "xxclienthost\n" + InetAddress.getLocalHost().getHostName() +"\n";

                        //Get the configuration server
                        LogicalServer cs = logicalServers.get("CONFIGURATION-SERVER");
                        //Find the current audit DB
                        String auditdbpath = findAuditDBPath(cs);
                        //Write the audit file to the DB
                        appendFile = ls.getMIE().mie.mxie_svrfile_tmpname();
                        OStream = cs.getMIE().mie.mxie_svrfile_write(appendFile);
                        OStream.write(auditRecord.getBytes());
                        OStream.close();
                        cs.getMIE().mie.mxie_append_text(appendFile, auditdbpath, 128000);
                        cs.getMIE().mie.mxie_svrfile_remove(appendFile);

                }catch(Exception e){
                        throw new ACFException("Memex Error adding record : " + e.getMessage(),e);
                }

                return sysurn;
        }
        
        /** Modify an existing record in the repository.
        * Feel free to change these arguments as needed.
        *@param id is a string identifier which uniquely identifies the record to change
        *@param keyValuePairs is the set of fieldname/value pairs to change
        */
        public void modifyRecord(String id, Hashtable keyValuePairs)
                throws ACFException
        {
                try{
                        this.setupConnection();
                }catch(Exception e){
                        throw new ACFException(e.getMessage(),e);
                }

                //Check the logical server exists
                LogicalServer ls = logicalServersByPrefix.get(id.substring(0, 2));
                if(ls == null){
                    throw new ACFException("Memex Error modifying a record - logical server cannot be found");
                }

                //Check the entity exists
                MemexEntity ent = entitiesByPrefix.get(id.substring(2, 4));
                if(ent == null){
                    throw new ACFException("Memex Error modifying a record - entity cannot be found");
                }

                //Check there is a database of the selected entity type on the selected server
                boolean found = false;
                RegistryEntry re = null;
                for(int i = 0; i < ls.getDatabaseCount(); i++){
                    if(ls.getDatabase(i).getName().contains(ent.getURN())){
                                found = true;
                                re = ls.getDatabase(i);
                                break;
                    }
                }
                if(found == false){
                    throw new ACFException("Memex Error modifying a record - entity not found on server ");
                }

                //OK - do the search
                Hashtable mxRecord = null;
                try{
                    SearchStatus serverSearch = ls.getMIE().mie.mxie_search(re.getPath(), "(" + id + ")$sysurn & (!y)$sysarchived", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, 0);
                    if (serverSearch.getNumberOfHits() == 1) {
                                        mxRecord = this.getmxRecordObj(ls, serverSearch.getHistory(), 1);

                                //OK - swap the fields in the retreived record for the passed in fields
                                for(Enumeration e = keyValuePairs.keys(); e.hasMoreElements();){
                                        String key = (String)e.nextElement();
                                        if(!(key.equals("subrecords"))){
                                        mxRecord.put(key, keyValuePairs.get(key));
                                        }
                                }
                                //Now deal with subrecords.
                                Hashtable<String, ArrayList> newsubs = (Hashtable<String, ArrayList>)keyValuePairs.get("subrecords");
                                if(newsubs != null){
                                        Hashtable<String, ArrayList> oldsubs = (Hashtable<String, ArrayList>)mxRecord.get("subrecords");
                                        if(oldsubs == null){
                                                //There's no existing subrecords
                                                mxRecord.put("subrecords", newsubs);
                                                }else{
                                                //Do the specific subrecords we're looking for exist?
                                                for(Enumeration sn = newsubs.keys(); sn.hasMoreElements();){
                                                        String subname = (String)sn.nextElement();
                                                        oldsubs.put(subname, newsubs.get(subname));
                                                }
                                        }
                                }

                                //At this point, the hash table has been updated with the new values - all we need to do now
                                //is write it out

                                long epochtime = System.currentTimeMillis();
                                String sysdateupdated = this.getMemexDate(epochtime) + "\n";
                                String systimeupdated = this.getMemexTime(epochtime) + "\n";


                                //Grab the meta data that goes before the protected record seperator - we need
                                //to make sure these appear in the correct place (field order after the seperator
                                //is not important).


                                String sysurn = (String)mxRecord.get("sysurn");
                                if((sysurn == null)||(!(sysurn.equals(id)))){
                                        //something's gone way wrong
                                        throw new ACFException("Memex Error : returned record does not match provided ID when updating record");
                                }else{
                                        mxRecord.remove("sysurn");
                                        sysurn += "\n";
                                }

                                String syswithheldmsg = (String)mxRecord.get("syswithheldmsg");
                                if((syswithheldmsg == null)||(syswithheldmsg.equals(""))){
                                        syswithheldmsg = "";
                                }else{
                                        mxRecord.remove("syswithheldmsg");
                                        syswithheldmsg += "\n";
                                }

                                String sysprotectedinfo = (String)mxRecord.get("sysprotectedinfo");
                                if((sysprotectedinfo == null)||(sysprotectedinfo.equals(""))){
                                        sysprotectedinfo = "";
                                }else{
                                        mxRecord.remove("sysprotectedinfo");
                                        sysprotectedinfo += "\n";
                                }

                                String syscases = (String)mxRecord.get("syscases");
                                if((syscases == null)||(syscases.equals(""))){
                                        syscases = "";
                                }else{
                                        mxRecord.remove("syscases");
                                        syscases += "\n";
                                }

                                String sysarea = (String)mxRecord.get("sysarea");
                                if((sysarea == null)||(sysarea.equals(""))){
                                        sysarea = "";
                                }else{
                                        mxRecord.remove("sysarea");
                                        sysarea += "\n";
                                }

                                String syssubstantiatedby = (String)mxRecord.get("syssubstantiatedby");
                                if((syssubstantiatedby == null)||(syssubstantiatedby.equals(""))){
                                        syssubstantiatedby = "";
                                }else{
                                        mxRecord.remove("syssubstantiatedby");
                                        syssubstantiatedby += "\n";
                                }

                                String syssubstantiates = (String)mxRecord.get("syssubstantiates");
                                if((syssubstantiates == null)||(syssubstantiates.equals(""))){
                                        syssubstantiates = "";
                                }else{
                                        mxRecord.remove("syssubstantiates");
                                        syssubstantiates += "\n";
                                }

                                String sysarchived = (String)mxRecord.get("sysarchived");
                                if((sysarchived == null)||(sysarchived.equals(""))){
                                        sysarchived = "";
                                }else{
                                        mxRecord.remove("sysarchived");
                                        sysarchived += "\n";
                                }

                                String recordtext = "rrrrrrrr";
                                String locks = (String)keyValuePairs.get("meta_clocks");
                                if(locks != null){
                                        recordtext += locks;
                                }
                                recordtext += "\nxxsysurn\n" + sysurn;
                                recordtext += "xxsyswithheldmsg\n" + syswithheldmsg;
                                recordtext += "xxsysprotectedinfo\n" + sysprotectedinfo;
                                recordtext += "xxsyscases\n" + syscases;
                                recordtext += "xxsysarea\n" + sysarea;
                                recordtext += "xxsyssubstantiatedby\n" + syssubstantiatedby;
                                recordtext += "xxsyssubstantiates\n" + syssubstantiates;
                                recordtext += "xxsysarchived\n" + sysarchived;

                                locks = (String)keyValuePairs.get("meta_plocks");
                                if(locks != null){
                                        recordtext += "ssssssss" + locks + "\n";
                                }else{
                                        recordtext += "ssssssss\n";
                                }

                                //Need to add the modified-by info
                                recordtext += "xxsysdateupdated\n" + sysdateupdated;
                                recordtext += "xxsystimeupdated\n" + systimeupdated;
                                recordtext += "xxsysupdatedby\n" + miePool.getUsername() + "\n";

                                //remove previous modified-by info
                                mxRecord.remove("sysdateupdated");
                                mxRecord.remove("systimeupdated");
                                mxRecord.remove("sysupdatedby");

                                //OK - got the bare minimum - everything should just be copied from mxRecord;

                                //Now add passed in fields
                                for(Enumeration e = mxRecord.keys(); e.hasMoreElements();){
                                        String key = (String)e.nextElement();
                                        if(!(key.equals("subrecords"))&&(!(key.startsWith("meta_")))){
                                        recordtext += "xx" + key + "\n" + (String)mxRecord.get(key) + "\n";
                                        }
                                }

                                //Deal with subrecords
                                Hashtable<String, ArrayList> subrecords = (Hashtable<String, ArrayList>)mxRecord.get("subrecords");
                                if(subrecords != null){
                                        for(Enumeration e = subrecords.keys(); e.hasMoreElements();){
                                                String subname = (String)e.nextElement();
                                                for(int x = 0; x < subrecords.get(subname).size(); x++){
                                                        Hashtable<String, String> subfields = (Hashtable<String, String>)subrecords.get(subname).get(x);
                                                        recordtext += "cccccccc\nxxsyscategory\n" + subname + "\n";
                                                        for(Enumeration s = subfields.keys(); s.hasMoreElements();){
                                                        String fieldname = (String)s.nextElement();
                                                        recordtext += "xx" + fieldname + "\n" + subfields.get(fieldname) + "\n";
                                                        }
                                                }
                                        }
                                }

                                //OK - got the record. Now add it to the database.
                                String appendFile = ls.getMIE().mie.mxie_svrfile_tmpname();
                                OutputStream OStream = null;
                                OStream = ls.getMIE().mie.mxie_svrfile_write(appendFile);
                                OStream.write(recordtext.getBytes());
                                OStream.close();
                                int[] update = {ls.getMIE().mie.mxie_edit_begin(serverSearch.getHistory(), 1, appendFile)};
                                ls.getMIE().mie.mxie_edit_commit(update, ls.getMIE().mie.R_DELETE);
                                ls.getMIE().mie.mxie_svrfile_remove(appendFile);

                                //Now create an audit record
                                String auditRecord = "rrrrrrrr\nxxtype\nEDIT\n";
                                auditRecord += "xxrecordurn\n" + sysurn;
                                auditRecord += "xxresultsid\n\n";
                                auditRecord += "xxcreatedate\n" + sysdateupdated;
                                auditRecord += "xxcreatetime\n" + systimeupdated;
                                auditRecord += "xxreason\n\n";
                                auditRecord += "xxreviewdate\n\n";
                                auditRecord += "xxcreatedby\n" + miePool.getUsername() +"\n";
                                auditRecord += "xxsessionid\n";
                                auditRecord += "xxfullname\nACF Support Program\n";
                                auditRecord += "xxusername\n" + miePool.getUsername() +"\n";
                                auditRecord += "xxclienthost\n" + InetAddress.getLocalHost().getHostName() +"\n";

                                //Get the configuration server
                                LogicalServer cs = logicalServers.get("CONFIGURATION-SERVER");
                                //Find the current audit DB
                                String auditdbpath = findAuditDBPath(cs);
                                //Write the audit file to the DB
                                appendFile = ls.getMIE().mie.mxie_svrfile_tmpname();
                                OStream = cs.getMIE().mie.mxie_svrfile_write(appendFile);
                                OStream.write(auditRecord.getBytes());
                                OStream.close();
                                cs.getMIE().mie.mxie_append_text(appendFile, auditdbpath, 128000);
                                cs.getMIE().mie.mxie_svrfile_remove(appendFile);

                    }
                }catch(Exception e){
                    throw new ACFException("Memex Error searching for record to modify : " + e.getMessage(),e);
                }



        }
        
        /** Remove an existing record from the repository.
        * Feel free to change these arguments as needed.
        *@param id is a string identifier which uniquely identifies the record to remove
        */
        public void removeRecord(String id)
                throws ACFException
        {
                try{
                        this.setupConnection();

                       //Check the logical server exists
                        LogicalServer ls = logicalServersByPrefix.get(id.substring(0, 2));
                        if(ls == null){
                                    throw new ACFException("Memex Error deleting a record - logical server cannot be found");
                        }

                        //Check the entity exists
                        MemexEntity ent = entitiesByPrefix.get(id.substring(2, 4));
                        if(ent == null){
                                    throw new ACFException("Memex Error deleting a record - entity cannot be found");
                        }

                        //Check there is a database of the selected entity type on the selected server
                        boolean found = false;
                        RegistryEntry re = null;
                        for(int i = 0; i < ls.getDatabaseCount(); i++){
                                    if(ls.getDatabase(i).getName().contains(ent.getURN())){
                                            found = true;
                                            re = ls.getDatabase(i);
                                            break;
                                    }
                        }
                        if(found == false){
                                    throw new ACFException("Memex Error deleting a record - entity not found on server ");
                        }


                        SearchStatus serverSearch = ls.getMIE().mie.mxie_search(re.getPath(), "(" + id + ")$sysurn", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, 0);
                        if (serverSearch.getNumberOfHits() == 1) {
                                    //OK - found the record. Now delete it by replacing it with null.
                                    int[] update = {ls.getMIE().mie.mxie_edit_begin(serverSearch.getHistory(), 1, null)};
                                    ls.getMIE().mie.mxie_edit_commit(update, 0);
                        }


                        String datedeleted = this.getMemexDate(System.currentTimeMillis());
                        String timedeleted = this.getMemexTime(System.currentTimeMillis());
                        //Now create an audit record
                        String auditRecord = "rrrrrrrr\nxxtype\nDELETE\n";
                        auditRecord += "xxrecordurn\n" + id + "\n";
                        auditRecord += "xxcreatedate\n" + datedeleted +"\n";
                        auditRecord += "xxcreatetime\n" + timedeleted +"\n";
                        auditRecord += "xxcreatedby\n" + miePool.getUsername() +"\n";
                        auditRecord += "xxsessionid\n";
                        auditRecord += "xxfullname\nACF Support Program\n";
                        auditRecord += "xxusername\n" + miePool.getUsername() +"\n";
                        auditRecord += "xxclienthost\n" + InetAddress.getLocalHost().getHostName() +"\n";

                        //Get the configuration server
                        LogicalServer cs = logicalServers.get("CONFIGURATION-SERVER");
                        //Find the current audit DB
                        String auditdbpath = findAuditDBPath(cs);
                        //Write the audit file to the DB
                        String appendFile = ls.getMIE().mie.mxie_svrfile_tmpname();
                        OutputStream OStream = cs.getMIE().mie.mxie_svrfile_write(appendFile);
                        OStream.write(auditRecord.getBytes());
                        OStream.close();
                        cs.getMIE().mie.mxie_append_text(appendFile, auditdbpath, 128000);
                        cs.getMIE().mie.mxie_svrfile_remove(appendFile);


                }catch(Exception e){
                    throw new ACFException(e.getMessage(),e);
                }
        }
        
        /** Lookup a record given identifying unique information, which is not a system-assigned id, but is based on a user data value.
        * (This is used to clean up data that might be left lying around from an old test run.)
        * Feel free to change these arguments as needed.
        *@param virtualServer is the virtual server name
        *@param entityName is the name of the entity type to search
        *@param fieldName is the field name to search on
        *@param fieldValue is the field value to search for
        *@return the id of the first record that matches, or null if none found
        */
        public String lookupRecord(String virtualServer, String entityName, String fieldName, String fieldValue)
                throws ACFException
        {

                try{
                    this.setupConnection();

                   //Check the logical server exists
                    LogicalServer ls = logicalServers.get(virtualServer);
                    if(ls == null){
                                throw new ACFException("Memex Error looking up a record - logical server cannot be found");
                    }

                    //Check the entity exists
                    MemexEntity ent = entitiesByPrefix.get(entityName);
                    if(ent == null){
                                throw new ACFException("Memex Error looking up a record - entity cannot be found");
                    }

                    //Check the entity contains the field we're looking for
                    boolean found = false;
                    String[] entFields = ent.getFields();
                    for(int i = 0; i < entFields.length; i++){
                                if(entFields[i].equals(fieldName)){
                                        found = true;
                                }
                    }
                    if(!found){
                                throw new ACFException("Memex Error looking up a record - entity does not contain field");
                    }

                    //Check there is a database of the selected entity type on the selected server
                    found = false;
                    RegistryEntry re = null;
                    for(int i = 0; i < ls.getDatabaseCount(); i++){
                                if(ls.getDatabase(i).getName().contains(ent.getURN())){
                                        found = true;
                                        re = ls.getDatabase(i);
                                        break;
                                }
                    }
                    if(found == false){
                                throw new ACFException("Memex Error looking up a record - entity not found on server ");
                    }

                    //strip off the subrecord name
                    if(fieldName.contains(".")){
                                fieldName = fieldName.substring(fieldName.indexOf(".")+1);
                    }
                    String query = "(" + fieldValue + "):%%$" + fieldName + " & (!y)$sysarchived";
                    SearchStatus serverSearch = ls.getMIE().mie.mxie_search(re.getPath(), query, 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, 0);
                    if (serverSearch.getNumberOfHits() > 0) {
                                //OK - found matches. Return sysurn from the first record
                                DecodedField sysurn = new DecodedField(serverSearch.getHistory(), 1, 1, 100);
                                mieConnection.mie.mxie_goto_record(serverSearch.getHistory(), 1);
                                mieConnection.mie.mxie_decode_field(sysurn);
                                return sysurn.getText();
                    }
                }catch(Exception e){
                    throw new ACFException(e.getMessage(),e);
                }
                return null;
        }

        /** Set a record's ACL.  This will be used by the test to check on document visibility within the appliance, so it
        * must be able to handle the complete breadth of security situations that might be encountered in the wild.
        * Feel free to change these arguments as needed - especially since I don't understand the model well enough to
        * know whether this is how you'd structure it or not.
        *@param id is the record identifier
        *@param userGroupSet is the set of security group identifiers that have read access (?)
        */
        public void setRecordSecurity(String id, String[] userGroupSet)
                throws ACFException
        {
                
                try{
                    this.setupConnection();

                        //turn the string into a comma seperated list
                        String clocks = "";
                        for(int i = 0; i < userGroupSet.length; i++){
                                String lock = getSecurityLock(userGroupSet[i]);
                                if (lock == null)
                                        throw new ACFException("Invalid user/group name: '"+userGroupSet[i]+"'");
                                if(i > 0){
                                        clocks += ", " + lock;
                                }else{
                                        clocks = lock;
                                }
                        }
                        if(clocks.length() > 0){
                                clocks = "(" + clocks + ")";
                                Hashtable newValues = new Hashtable();
                                newValues.put("meta_clocks", clocks);
                                this.modifyRecord(id, newValues);
                        }
                }
                catch (ACFException e)
                {
                        throw e;
                }catch(Exception e){
                        throw new ACFException(e.getMessage(),e);
                }

        }

        /** Set a record's ACL.  This will be used by the test to check on document visibility within the appliance, so it
        * must be able to handle the complete breadth of security situations that might be encountered in the wild.
        * Feel free to change these arguments as needed - especially since I don't understand the model well enough to
        * know whether this is how you'd structure it or not.
        * This variant of the method sets protected security (plock) on the record.
        *@param id is the record identifier
        *@param userGroupSet is the set of security group identifiers that have read access (?)
        *@param protectedmessage is the nessage isplayed to users who do not have access to the record.
        */
        public void setRecordSecurity(String id, String[] userGroupSet, String protectedmessage)
                throws ACFException
        {
                try{
                        this.setupConnection();

                        //turn the string into a comma seperated list
                        String plocks = "";
                        for(int i = 0; i < userGroupSet.length; i++){
                                String lock = getSecurityLock(userGroupSet[i]);
                                if (lock == null)
                                        throw new ACFException("Invalid user/group name: '"+userGroupSet[i]+"'");
                                if(i > 0){
                                        plocks += ", " + lock;
                                }else{
                                        plocks = lock;
                                }
                        }
                        if((plocks.length() > 0)&&(protectedmessage != null)&&(!protectedmessage.equals(""))){
                                plocks = "(" + plocks + ")";
                                Hashtable newValues = new Hashtable();
                                newValues.put("meta_plocks", plocks);
                                newValues.put("syswithheldmsg", protectedmessage);
                                String mxdate = this.getMemexDate(System.currentTimeMillis());
                                String user = miePool.getUsername();
                                newValues.put("sysprotectedinfo", mxdate + "," + user);
                                this.modifyRecord(id, newValues);
                        }
                }
                catch (ACFException e)
                {
                        throw e;
                }
                catch(Exception e){
                        throw new ACFException(e.getMessage(),e);
                }

        }
        
        /** Close the connection.  Call this before discarding the repository connector.
        */
        public void close()
                throws ACFException
        {
                this.cleanUpConnections();
        }




        /*********************************************************************************/

        private void setupConnection()
                throws ACFException, ServiceInterruption
        {
                boolean connected = false;
                if((this.physicalServers != null) && !(this.physicalServers.isEmpty())){
                    //If we have entries in the physical server collection, check they are all connected
                    connected = true;
                    for(Enumeration serverkeys = physicalServers.keys(); serverkeys.hasMoreElements();){
                                String serverkey = (String)serverkeys.nextElement();
                                ACFMemexConnection pserver = physicalServers.get(serverkey);
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
                                //Initialise data structures
                                mieConnection = new ACFMemexConnection();
                                logicalServers = new Hashtable<String, LogicalServer>();
                                logicalServersByPrefix = new Hashtable<String, LogicalServer>();
                                physicalServers = new Hashtable<String, ACFMemexConnection>();
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

                                //Initialise the Query History
                                //mieConnection.mie.mxie_history_open("ACFSupport", 1000);


                                //Create a collection of data structures describing the entities in this set-up
                                this.getEntities();

                                //Create a collection of data structures describing each physical server in this set up. The
                                //configuration server has laready been added.
                                this.getServers();

                    }
                    catch(PoolAuthenticationException e){
                                throw new ACFException("Authentication failure connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()),e);
                    }
                    catch(PoolException e){
                        //Logging.connectors.warn("Memex: Pool error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying: " + e.getMessage(),e);
                        long currentTime = System.currentTimeMillis();
                        throw new ServiceInterruption("Pool error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying: " + e.getMessage(),
                                e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
                    }
                    catch(MemexException e){
                        //Logging.connectors.warn("Memex: Memex error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying: " + e.getMessage(),e);
                        long currentTime = System.currentTimeMillis();
                        throw new ServiceInterruption("Memex error connecting to Memex Server " + miePool.getHostname() + ":" + Integer.toString(miePool.getPort()) + " - " + e.getMessage() + " - retrying: " + e.getMessage(),
                                e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
                    }
                }

                connectionExpirationTime = System.currentTimeMillis() + CONNECTION_IDLE_INTERVAL;
        }

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
                        ACFMemexConnection currentMIE = physicalServers.get(serverkey);
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
                            System.err.println("Memex exception logging out virtual server "+serverkey+": "+e.getMessage());
                            e.printStackTrace(System.err);
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
                    securityLocks = null;
                    connectionExpirationTime = -1L;
                }
        }

        /**Creates an alphabetically ordered list of entity objects.
        */
        private void getEntities()
                throws MemexException, ACFException
        {
                String mxEntityPath = null;
                String[] entityReturn = new String[1];

                //Start by locating the mxEntity database on the Config Server

                if(mieConnection.localRegistry != null){
                    Map<String,RegistryEntry> registryMap = new HashMap<String,RegistryEntry>();
                    int i;
                    int dbcount = mieConnection.localRegistry.length;
                    for(i=0;i<dbcount;i++)
                    {
                        RegistryEntry re = mieConnection.localRegistry[i];
                        String name = re.getName();
                        if (name != null)
                        {
                            name = name.substring(0,name.indexOf("."));
                            registryMap.put(name,re);
                            if(name.equals("mxEntity")){
                                mxEntityPath = re.getPath();
                            }
                        }
                    }
                    if(mxEntityPath != null && !(mxEntityPath.equals("")))
                    {
                        String configServerPath = mxEntityPath.substring(0, mxEntityPath.indexOf("mxEntity"));

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

                                //Get the form file for the entity
                                String entityNameString = entityname.getText();
                                                
                                if (entityNameString != null && entityNameString.length() > 0)
                                {
                                    Document entityForm = null;
                                    try{
                                        InputStream formStream = mieConnection.mie.mxie_svrfile_read(configServerPath + "files/forms/" + entityNameString + ".form.xml");
                                        try
                                        {
                                            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                                            //Using factory get an instance of document builder
                                            DocumentBuilder db = dbf.newDocumentBuilder();
                                            // Parse it!
                                            entityForm = db.parse(formStream);
                                        }catch(ParserConfigurationException e){
                                            throw new ACFException("Can't find a valid parser: "+e.getMessage(),e);
                                        }catch(SAXException e){
                                            throw new ACFException("XML had parse errors: "+e.getMessage(),e);
                                        }catch(IOException e){
                                            // I/O problem
                                            throw new ACFException(e.getMessage(),e);
                                        }
                                        finally
                                        {
                                            try
                                            {
                                                formStream.close();
                                            }
                                            catch (IOException e)
                                            {
                                                throw new ACFException("Error reading memex form data: "+e.getMessage(),e);
                                            }
                                        }
                                    }catch(MemexException e){
                                        // This means file doesn't exist, which might be OK for some kinds of entities we may encounter
                                    }
                                                    
                                    RegistryEntry regEntry = registryMap.get(entityNameString);
                                    if (regEntry != null)
                                    {

                                        MemexEntity ent = new MemexEntity(entityNameString, entityURN.getText(), entityprefix.getText(), entitydisplayname.getText(), entityfields.getText(), entitylabels.getText(), entityForm);
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


        /**Creates an alphabetically ordered list of entity objects in memory, and looks up a lock based on user or group name.
        */
        private String getSecurityLock(String userGroupName)
                throws MemexException
        {
                if (securityLocks == null)
                {
                    securityLocks = new Hashtable<String, String>();

                    String mxUserGroupPath = "";

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
                        if(!(mxUserGroupPath.equals(""))){
                                    //get all users and security groups from the mxUserGroup db and create an mxEntity
                                    //object for each

                                    int hist = 0;
                                    int numHits = 0;
                                    SearchStatus secgroupSearch = mieConnection.mie.mxie_search(mxUserGroupPath, "((user | (security group):%2) & !deleted)$type", 3, 3, MemexConnection.SAVE_HITS, MemexConnection.R_DONTCARE, MemexConnection.MXIE_WAIT, hist);
                                    if (secgroupSearch.getNumberOfHits() < 0) {
                                            throw new MemexException("Memex Error retrieving security group information : " + mieConnection.mie.mxie_error());
                                    }else{
                                            securityLocks.clear();
                                            hist = secgroupSearch.getHistory();
                                            for(int x = 1; x <= secgroupSearch.getNumberOfHits(); x++){
                                                    //Field 2 is the server name in the mxServer database
                                                    ArrayList secgroupFields = new ArrayList();
                                                    DecodedField secgroupURN = new DecodedField(hist, x, 12, 100);
                                                    DecodedField secgroupType = new DecodedField(hist, x, 1, 100000);
                                                    DecodedField secgroupName = new DecodedField(hist, x, 2, 100);

                                                    secgroupFields.add(secgroupURN);
                                                    secgroupFields.add(secgroupType);
                                                    secgroupFields.add(secgroupName);

                                                    mieConnection.mie.mxie_goto_record(hist, x);
                                                    mieConnection.mie.mxie_decode_fields(secgroupFields);

                                                    if(secgroupType.getText().equals("user")){
                                                            String implicitLock = secgroupURN.getText() + "_imp";
                                                            securityLocks.put(secgroupName.getText(), implicitLock);
                                                    }else{
                                                            securityLocks.put(secgroupName.getText(), secgroupName.getText());
                                                    }
                                            }
                                    }
                        }
                    }
                }
                return securityLocks.get(userGroupName);
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
                    if(mxServerPath != null && !(mxServerPath.equals(""))){
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
                                                ACFMemexConnection mie;
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
                                                //Initialise the Query History
                                                //ls.getMIE().mie.mxie_history_open("ACFSupport", 1000);
                                        }
                                }
                    }
                }
        }

        private ACFMemexConnection getPhysicalServer(String server, int port){

                String key = server + ":" + Integer.toString(port);

                if(physicalServers.containsKey(key)){
                    return (ACFMemexConnection)physicalServers.get(key);
                }else{
                    ACFMemexConnection newServer = new ACFMemexConnection();
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

        private String getMemexDate(long epochmilis){

                TimeZone tz = TimeZone.getTimeZone(serverTimezone);
                Calendar calendar = new GregorianCalendar(tz);
                Date theDate = new Date(epochmilis);
                calendar.setTime(theDate);

                int month = calendar.get(Calendar.MONTH) + 1;
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                int year = calendar.get(Calendar.YEAR);

                return "date" + String.format("%04d",year) + String.format("%02d",month) + String.format("%02d",day) + " month" + String.format("%02d",month) + String.format("%04d",year);
        }

        private String getMemexTime(long epochmilis){

                TimeZone tz = TimeZone.getTimeZone(serverTimezone);
                Calendar calendar = new GregorianCalendar(tz);
                Date theDate = new Date(epochmilis);
                calendar.setTime(theDate);

                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                int minute = calendar.get(Calendar.MINUTE);
                int second = calendar.get(Calendar.SECOND);

                return String.format("%02d",hour) + String.format("%02d",minute) + String.format("%02d",second);
        }

        /** Given the configuration server logical server, find the correct audit db's path */
        private String findAuditDBPath(LogicalServer cs)
        {
                //Find the current audit DB - audit DB's paths are suffixed with three digits, eg 'mxAudit001'
                RegistryEntry[] fullDatabases = cs.getMIE().localRegistry;
                int dbnum = -1;
                String auditdbpath = null;
                for(int i = 0; i < fullDatabases.length; i++){
                        String dbName = fullDatabases[i].getName();
                        if (dbName != null && dbName.length() > 0)
                        {
                                dbName = dbName.substring(0,dbName.indexOf("."));
                                if (dbName.startsWith("mxAudit"))
                                {
                                        if (dbName.equals("mxAudit"))
                                        {
                                                if (dbnum == -1)
                                                {
                                                        auditdbpath = fullDatabases[i].getPath();
                                                        dbnum = 0;
                                                }
                                        }
                                        else
                                        {
                                                int currnum = Integer.parseInt(dbName.substring(dbName.length() - 3));
                                                if(currnum > dbnum){
                                                        auditdbpath = fullDatabases[i].getPath();
                                                        dbnum = currnum;
                                                }
                                        }
                                }
                        }
                }
                return auditdbpath;
        }
        
        /*
        * Return an HashTable represtation of a Memex Patriarch Record
        * Normal fields are stored as strings. Subrecords are stored as
        * an array list of hashtables;
        * @param urn - the urn of the record being sought
        * @return - the hash table representation of the record. Null if its not found
        */
        private Hashtable getmxRecordObj(LogicalServer ls, int histno, int recnum)
                throws ACFException, ServiceInterruption
        {
                Hashtable mxRecord = null;
                try{
                    mxRecord = new Hashtable();
                    //Get the record header and look for any covert locks
                    String sepinfo = ls.getMIE().mie.mxie_decode_sepinfo(histno, recnum);
                    if(sepinfo == null){
                        //Do something if the record's disappeared between the search completing
                        //and us decoding the record.
                    }
                    //generation number
                    int start = sepinfo.indexOf(",");
                    int end = sepinfo.indexOf(",", start+1);
                    mxRecord.put("meta_gennum", sepinfo.substring(start+2, end));

                    //datestamp
                    start = sepinfo.indexOf(",", end+1);
                    end = sepinfo.indexOf("]");
                    mxRecord.put("meta_datestamp", sepinfo.substring(start+2, end));

                    //Covert locks
                    if(sepinfo.contains("(")){
                        sepinfo = sepinfo.substring(sepinfo.indexOf("("), sepinfo.length());
                        mxRecord.put("meta_clocks", sepinfo);
                    }

                    //Decode the record
                    ls.getMIE().mie.mxie_goto_record(histno, recnum);
                    InputStream isDecode = ls.getMIE().mie.mxie_decode_record("", "", 3, MemexConnection.MXIE_FORWARD);
                    if(isDecode == null){
                        //Do something if the record's disappeared between the search completing
                        //and us decoding the record.
                    }
                    BufferedReader reader = new BufferedReader(new InputStreamReader(isDecode));
                    String line = null;
                    try {
                        String fieldname = null;
                        String value = "";
                        String subname = null;
                        Hashtable subvalues = null;
                        while((line = reader.readLine()) != null){
                            //Loop through the record - each field should be added as an entry in the hash table
                            if(line.startsWith("xx")){
                                //we've found a field definition
                                //Add the previous field to the hash table
                                if(fieldname != null){
                                    if(subname != null){
                                        //We're currently processing a subrecord
                                        subvalues.put(fieldname, value);
                                    }else{
                                        mxRecord.put(fieldname, value);
                                    }
                                    fieldname = null;
                                    value = "";
                                }
                                fieldname = line.substring(2);
                            }
                            else if(line.startsWith("ssssssss")){
                                //we've found the level seperator
                                //Add any previous field to the hash table
                                if(fieldname != null){
                                    if(subname != null){
                                        //We're currently processing a subrecord
                                        subvalues.put(fieldname, value);
                                    }else{
                                        mxRecord.put(fieldname, value);
                                    }
                                    fieldname = null;
                                    value = "";
                                }
                                //Now look to see if there are any protected keys
                                if(line.contains("(")){
                                    line = line.substring(line.indexOf("("), line.length());
                                    mxRecord.put("meta_plocks", line);
                                }
                            }
                            else if(line.startsWith("cccccccc")){
                                //we've found the sub-record seperator
                                //Add any previous field to the hash table
                                if(fieldname != null){
                                    if(subname != null){
                                        //We're currently processing a subrecord
                                        subvalues.put(fieldname, value);
                                    }else{
                                        mxRecord.put(fieldname, value);
                                    }
                                    fieldname = null;
                                    value = "";
                                }
                                //This is the start of a new subrecord, so write
                                //any subrecord we were previosuly processing to
                                //the main hash
                                if(subname != null){
                                    ArrayList subarray = null;
                                    if(!(mxRecord.containsKey("subrecords"))){
                                        mxRecord.put("subrecords", new Hashtable<String, ArrayList>());
                                    }
                                    if(!(((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).containsKey(subname))){
                                        subarray = new ArrayList();
                                    }else{
                                        subarray = ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).get(subname);
                                        ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).remove(subname);
                                    }
                                    subarray.add(subvalues);
                                    ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).put(subname, subarray);
                                    subname = null;
                                    subvalues = null;
                                }
                                //Now read in the name of the new subrecord
                                line = reader.readLine(); //Should be xxsyscategory
                                line = reader.readLine();
                                subname = line;
                                subvalues = new Hashtable();
                            }
                            else if(!(line.startsWith("rrrrrrrr"))){
                                //This is just a normal field value line
                                if(!(value.equals(""))){
                                    value += "\n" + line;
                                }else{
                                    value = line;
                                }
                            }
                        }
                        //We've reached the end of the stream - check to see if there are any final
                        //fields or subrecords to add
                        if(fieldname != null){
                            if(subname != null){
                                //We're currently processing a subrecord
                                subvalues.put(fieldname, value);
                            }else{
                                mxRecord.put(fieldname, value);
                            }
                            fieldname = null;
                            value = "";
                        }

                        //Was this a subrecord?
                        if(subname != null){
                            ArrayList subarray = null;
                            if(!(mxRecord.containsKey("subrecords"))){
                                mxRecord.put("subrecords", new Hashtable<String, ArrayList>());
                            }
                            if(!(((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).containsKey(subname))){
                                subarray = new ArrayList();
                            }else{
                                subarray = ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).get(subname);
                                ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).remove(subname);
                            }
                            subarray.add(subvalues);
                            ((Hashtable<String, ArrayList>)mxRecord.get("subrecords")).put(subname, subarray);
                            subname = null;
                            subvalues = null;
                        }

                        return mxRecord;

                    }catch (IOException eek) {
                        // Treat this as a service interruption
                        //Logging.connectors.warn("Memex: Couldn't read record from "+ls.getServerName()+"; record id="+Integer.toString(recnum)+": "+eek.getMessage()+" - retrying",eek);
                        long currentTime = System.currentTimeMillis();
                        throw new ServiceInterruption("Couldn't read record from "+ls.getServerName()+"; record id="+Integer.toString(recnum)+": "+eek.getMessage(),eek,currentTime + 300000L,
                            currentTime + 12 * 60 * 60000L,-1,true);
                    }
                }catch(MemexException mex){
                        // Throw a transient error??  It would be good to know if this could happen (a) as a result of record deletion, or
                        // (b) as a result of security issues, and treat that separately
                        Logging.connectors.warn("Memex: Couldn't read record?  Record id ? Message: "+mex.getMessage()+" - retrying",mex);
                        long currentTime = System.currentTimeMillis();
                        throw new ServiceInterruption("Problem reading record from Memex, retrying: " + mex.getMessage(),mex,currentTime + 300000L,
                                currentTime + 12 * 60 * 60000L,-1,true);
                }
        }

}


