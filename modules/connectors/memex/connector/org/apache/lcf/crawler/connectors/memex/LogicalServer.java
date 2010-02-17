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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.lcf.crawler.connectors.memex;

import java.util.ArrayList;
import com.memex.mie.*;
import java.util.*;

/**
 *
 * @author mxadmin
 */
class LogicalServer{

    private String servername;
    private String prefix;
    private LCFMemexConnection mie;
    private RegistryEntry[] databases;
    private boolean active = false;
    private Map<String,RegistryEntry> databasesByName = new HashMap<String,RegistryEntry>();
    private Map<String,Map<String,DatabaseField>> fieldsByDatabaseName = new HashMap<String,Map<String,DatabaseField>>();

    public LogicalServer(String name, String urnPrefix, LCFMemexConnection serverMIE, Map<String,MemexEntity> entitiesByName)
        throws MemexException
    {
        servername = name;
        prefix = urnPrefix;
        mie = serverMIE;
        RegistryEntry[] fullRegistry = mie.localRegistry;
        
        List<String> entitynames = new ArrayList<String>();

        for(int i = 0; i < fullRegistry.length; i++){
            String tagname = fullRegistry[i].getTag();
            if (tagname != null && tagname.startsWith(prefix)){
                String dbname = fullRegistry[i].getName();
                if (dbname != null && !dbname.equals(""))
                {
                    dbname = dbname.substring(0, dbname.indexOf("."));
                    if(!dbname.startsWith("mxAudit")){
                        MemexEntity ent = entitiesByName.get(dbname);
                        if(ent != null)
                        {
                            String displayName = ent.getDisplayName();
                            databasesByName.put(displayName, fullRegistry[i]);
                            entitynames.add(displayName);
                            
                            if (!dbname.equals("mxDisseminate"))
                                active = true;

                        }
                    }
                }
            }
        }
        databases = new RegistryEntry[entitynames.size()];
        if(!(entitynames.isEmpty())){
            Collections.sort(entitynames);
            for(int i = 0; i < entitynames.size(); i++){
                databases[i] = databasesByName.get(entitynames.get(i));
            }
        }

    }
    
    public String getServerName()
    {
        return servername;
    }

    public String getPrefix()
    {
        return prefix;
    }
    
    public LCFMemexConnection getMIE()
    {
        return mie;
    }
    
    public boolean isActive()
    {
        return active;
    }
    
    public int getDatabaseCount()
    {
        return databases.length;
    }
    
    public RegistryEntry getDatabase(int j)
    {
        return databases[j];
    }
    
    public Map<String,DatabaseField> getFieldsByDatabaseName(String databaseName)
        throws MemexException
    {
        Map<String,DatabaseField> rval = fieldsByDatabaseName.get(databaseName);
        if (rval == null)
        {
            // Find the desired information, and cache it for the life of this object
            RegistryEntry db = databasesByName.get(databaseName);
            if (db == null)
                return null;
            DatabaseConfig dbConfig = mie.mie.mxie_database_config_read(db.getPath());
            // Process the fields so we can get the index numbers
            rval = new HashMap<String,DatabaseField>();
            // Build field lookup map
            List<DatabaseField> fieldList = dbConfig.getFields();
            int h = 0;
            while (h < fieldList.size())
            {
                DatabaseField f = fieldList.get(h++);
                rval.put(f.getName(),f);
            }
            fieldsByDatabaseName.put(databaseName, rval);
        }
        return rval;
    }

}
