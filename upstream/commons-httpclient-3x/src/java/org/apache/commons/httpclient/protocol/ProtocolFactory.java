/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//httpclient/src/java/org/apache/commons/httpclient/protocol/Protocol.java,v 1.10 2004/04/18 23:51:38 jsdever Exp $
 * $Revision: 157457 $
 * $Date: 2005-03-14 15:23:16 -0500 (Mon, 14 Mar 2005) $
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.commons.httpclient.protocol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.util.LangUtils;

/**
 * A class to encapsulate the specifics of a protocol.  This class class also
 * provides the ability to customize the set and characteristics of the
 * protocols used.
 * 
 * <p>One use case for modifying the default set of protocols would be to set a
 * custom SSL socket factory.  This would look something like the following:
 * <pre> 
 * Protocol myHTTPS = new Protocol( "https", new MySSLSocketFactory(), 443 );
 * 
 * Protocol.registerProtocol( "https", myHTTPS );
 * </pre>
 *
 * @author Michael Becke 
 * @author Jeff Dever
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 *  
 * @since 2.0 
 */
public class ProtocolFactory {

    /** The available protocols */
    private Map PROTOCOLS = Collections.synchronizedMap(new HashMap());

    /**
     * Registers a new protocol with the given identifier.  If a protocol with
     * the given ID already exists it will be overridden.  This ID is the same
     * one used to retrieve the protocol from getProtocol(String).
     * 
     * @param id the identifier for this protocol
     * @param protocol the protocol to register
     * 
     * @see #getProtocol(String)
     */
    public void registerProtocol(String id, Protocol protocol) {

        if (id == null) {
            throw new IllegalArgumentException("id is null");
        }
        if (protocol == null) {
            throw new IllegalArgumentException("protocol is null");
        }

        PROTOCOLS.put(id, protocol);
    }

    /**
     * Unregisters the protocol with the given ID.
     * 
     * @param id the ID of the protocol to remove
     */
    public void unregisterProtocol(String id) {

        if (id == null) {
            throw new IllegalArgumentException("id is null");
        }

        PROTOCOLS.remove(id);
    }

    /**
     * Gets the protocol with the given ID.
     * 
     * @param id the protocol ID
     * 
     * @return Protocol a protocol
     * 
     * @throws IllegalStateException if a protocol with the ID cannot be found
     */
    public Protocol getProtocol(String id) 
        throws IllegalStateException {

        if (id == null) {
            throw new IllegalArgumentException("id is null");
        }

        Protocol protocol = (Protocol) PROTOCOLS.get(id);

        if (protocol == null) {
            protocol = lazyRegisterProtocol(id);
        }

        return protocol;
    } 

    /**
     * Lazily registers the protocol with the given id.
     * 
     * @param id the protocol ID
     * 
     * @return the lazily registered protocol
     * 
     * @throws IllegalStateException if the protocol with id is not recognized
     */
    private Protocol lazyRegisterProtocol(String id) 
        throws IllegalStateException {

        if ("http".equals(id)) {
            final Protocol http 
                = new Protocol("http", DefaultProtocolSocketFactory.getSocketFactory(), 80);
            Protocol.registerProtocol("http", http);
            return http;
        }

        if ("https".equals(id)) {
            final Protocol https 
                = new Protocol("https", SSLProtocolSocketFactory.getSocketFactory(), 443);
            Protocol.registerProtocol("https", https);
            return https;
        }

        throw new IllegalStateException("unsupported protocol: '" + id + "'");
    }
}
