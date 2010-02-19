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
package org.apache.lcf.crawler.connectors.meridio;

import org.apache.commons.httpclient.protocol.*;
import org.apache.commons.httpclient.params.*;
import org.apache.commons.httpclient.*;

import java.io.*;
import java.net.*;


/** HTTPClient secure socket factory, which implements SecureProtocolSocketFactory
*/
public class MeridioSecureSocketFactory implements SecureProtocolSocketFactory
{
        public static final String _rcsid = "@(#)$Id$";

        /** This is the javax.net socket factory.
        */
        protected javax.net.ssl.SSLSocketFactory socketFactory;

        /** Constructor */
        public MeridioSecureSocketFactory(javax.net.ssl.SSLSocketFactory socketFactory)
        {
                this.socketFactory = socketFactory;
        }

        public Socket createSocket(
                String host,
                int port,
                InetAddress clientHost,
                int clientPort)
                throws IOException, UnknownHostException
        {
                return socketFactory.createSocket(
                    host,
                    port,
                    clientHost,
                    clientPort
                );
        }

        public Socket createSocket(
                final String host,
                final int port,
                final InetAddress localAddress,
                final int localPort,
                final HttpConnectionParams params
            ) throws IOException, UnknownHostException, ConnectTimeoutException
        {
                if (params == null)
                {
                    throw new IllegalArgumentException("Parameters may not be null");
                }
                int timeout = params.getConnectionTimeout();
                if (timeout == 0)
                {
                    return createSocket(host, port, localAddress, localPort);
                }
                else
                    throw new IllegalArgumentException("This implementation does not handle non-zero connection timeouts");
        }

        public Socket createSocket(String host, int port)
                throws IOException, UnknownHostException
        {
                return socketFactory.createSocket(
                        host,
                        port
                        );
        }

        public Socket createSocket(
                Socket socket,
                String host,
                int port,
                boolean autoClose)
                throws IOException, UnknownHostException
        {
                return socketFactory.createSocket(
                        socket,
                        host,
                        port,
                        autoClose
                        );
        }

        public boolean equals(Object obj)
        {
                if (obj == null || !(obj instanceof MeridioSecureSocketFactory))
                        return false;
                // Each object is unique
                return super.equals(obj);
        }

        public int hashCode()
        {
                return super.hashCode();
        }    
}
