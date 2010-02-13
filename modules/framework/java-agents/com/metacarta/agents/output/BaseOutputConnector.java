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
package com.metacarta.agents.output;

import com.metacarta.core.interfaces.*;
import com.metacarta.agents.interfaces.*;

import java.io.*;
import java.util.*;

/** This base class describes an instance of a connection between an output pipeline and the Connector Framework.
*
* Each instance of this interface is used in only one thread at a time.  Connection Pooling
* on these kinds of objects is performed by the factory which instantiates repository connectors
* from symbolic names and config parameters, and is pooled by these parameters.  That is, a pooled connector
* handle is used only if all the connection parameters for the handle match.
*
* Implementers of this interface should provide a default constructor which has this signature:
*
* xxx();
*
* Connectors are either configured or not.  If configured, they will persist in a pool, and be
* reused multiple times.  Certain methods of a connector may be called before the connector is
* configured.  This includes basically all methods that permit inspection of the connector's
* capabilities.
*
*/
public abstract class BaseOutputConnector implements IOutputConnector
{
        public static final String _rcsid = "@(#)$Id$";

        // Config params
        protected ConfigParams params = null;

        // Current thread context
        protected IThreadContext currentContext = null;

        /** Install the connector.
        * This method is called to initialize persistent storage for the connector, such as database tables etc.
        * It is called when the connector is registered.
        *@param threadContext is the current thread context.
        */
        public void install(IThreadContext threadContext)
                throws MetacartaException
        {
                // Base install does nothing
        }

        /** Uninstall the connector.
        * This method is called to remove persistent storage for the connector, such as database tables etc.
        * It is called when the connector is deregistered.
        *@param threadContext is the current thread context.
        */
        public void deinstall(IThreadContext threadContext)
                throws MetacartaException
        {
                // Base uninstall does nothing
        }

        /** Return the list of activities that this connector supports (i.e. writes into the log).
        *@return the list.
        */
        public String[] getActivitiesList()
        {
                return new String[0];
        }

        /** Connect.  The configuration parameters are included.
        *@param configParams are the configuration parameters for this connection.
        */
        public void connect(ConfigParams configParams)
        {
                params = configParams;
        }

        // All methods below this line will ONLY be called if a connect() call succeeded
        // on this instance!

        /** Test the connection.  Returns a string describing the connection integrity.
        *@return the connection's status as a displayable string.
        */
        public String check()
                throws MetacartaException
        {
                // Base version returns "OK" status.
                return "Connection working";
        }

        /** This method is periodically called for all connectors that are connected but not
        * in active use.
        */
        public void poll()
                throws MetacartaException
        {
                // Base version does nothing
        }

        /** Close the connection.  Call this before discarding the repository connector.
        */
        public void disconnect()
                throws MetacartaException
        {
                params = null;
        }

        /** Clear out any state information specific to a given thread.
        * This method is called when this object is returned to the connection pool.
        */
        public void clearThreadContext()
        {
                currentContext = null;
        }

        /** Attach to a new thread.
        *@param threadContext is the new thread context.
        */
        public void setThreadContext(IThreadContext threadContext)
        {
                currentContext = threadContext;
        }

        /** Get configuration information.
        *@return the configuration information for this class.
        */
        public ConfigParams getConfiguration()
        {
                return params;
        }

}


