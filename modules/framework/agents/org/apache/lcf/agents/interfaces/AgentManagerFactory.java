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
package org.apache.lcf.agents.interfaces;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.system.*;

/** Agent manager factory class.
*/
public class AgentManagerFactory
{
        public static final String _rcsid = "@(#)$Id$";

        protected final static String agentManager = "_AgentManager_";

        private AgentManagerFactory()
        {
        }

        /** Make an agent manager instance.
        *@param threadContext is the thread context.
        *@return the manager.
        */
        public static IAgentManager make(IThreadContext threadContext)
                throws LCFException
        {
                Object o = threadContext.get(agentManager);
                if (o == null || !(o instanceof IAgentManager))
                {
                        IDBInterface database = DBInterfaceFactory.make(threadContext,
                                LCF.getMasterDatabaseName(),
                                LCF.getMasterDatabaseUsername(),
                                LCF.getMasterDatabasePassword());

                        o = new org.apache.lcf.agents.agentmanager.AgentManager(threadContext,database);
                        threadContext.save(agentManager,o);
                }
                return (IAgentManager)o;
        }

        /** Request permission from all registered agents to delete an output connection.
        *@param threadContext is the thread context.
        *@param connName is the name of the output connection.
        *@return true if the connection is in use, false otherwise.
        */
        public static boolean isOutputConnectionInUse(IThreadContext threadContext, String connName)
                throws LCFException
        {
                // Instantiate the list of IAgent objects
                IAgent[] theAgents = instantiateAllAgents(threadContext);
                int i = 0;
                while (i < theAgents.length)
                {
                        if (theAgents[i++].isOutputConnectionInUse(connName))
                                return true;
                }
                return false;
        }
        
        /**  Note to all registered agents the deregistration of an output connector used by the specified connections.
        * This method will be called when the connector is deregistered.
        *@param threadContext is the thread context.
        *@param connectionNames is the set of connection names.
        */
        public static void noteOutputConnectorDeregistration(IThreadContext threadContext, String[] connectionNames)
                throws LCFException
        {
                // Instantiate the list of IAgent objects
                IAgent[] theAgents = instantiateAllAgents(threadContext);
                int i = 0;
                while (i < theAgents.length)
                {
                        theAgents[i++].noteOutputConnectorDeregistration(connectionNames);
                }
        }
        
        /** Note to all registered agents the registration of an output connector used by the specified connections.
        * This method will be called when a connector is registered, on which the specified
        * connections depend.
        *@param threadContext is the thread context.
        *@param connectionNames is the set of connection names.
        */
        public static void noteOutputConnectorRegistration(IThreadContext threadContext, String[] connectionNames)
                throws LCFException
        {
                // Instantiate the list of IAgent objects
                IAgent[] theAgents = instantiateAllAgents(threadContext);
                int i = 0;
                while (i < theAgents.length)
                {
                        theAgents[i++].noteOutputConnectorRegistration(connectionNames);
                }
        }

        /** Instantiate the complete set of IAgent objects.
        *@param threadContext is the thread context.
        *@return the array of such objects.
        */
        public static IAgent[] instantiateAllAgents(IThreadContext threadContext)
                throws LCFException
        {
                IAgentManager manager = make(threadContext);
                String[] allAgents = manager.getAllAgents();
                IAgent[] rval = new IAgent[allAgents.length];
                int i = 0;
                while (i < rval.length)
                {
                        rval[i] = AgentFactory.make(threadContext,allAgents[i]);
                        i++;
                }
                return rval;
        }
        
}
