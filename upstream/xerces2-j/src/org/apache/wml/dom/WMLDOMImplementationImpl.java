/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.wml.dom;

import org.apache.wml.WMLDOMImplementation;
import org.apache.xerces.dom.DOMImplementationImpl;
import org.apache.xerces.dom.DOMMessageFormatter;
import org.apache.xerces.dom.DocumentImpl;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;

/**
 * @xerces.internal
 * @version $Id$
 * @author <a href="mailto:david@topware.com.tw">David Li</a>
 */
public class WMLDOMImplementationImpl extends DOMImplementationImpl implements WMLDOMImplementation {
    
    static final DOMImplementationImpl singleton = new WMLDOMImplementationImpl();
    
    /** NON-DOM: Obtain and return the single shared object */
    public static DOMImplementation getDOMImplementation() {
        return singleton;
    }  
    
    /**
     * @see org.w3c.dom.DOMImplementation
     */
    public Document createDocument(String namespaceURI, 
            String qualifiedName, 
            DocumentType doctype) throws DOMException {
        if (doctype != null && doctype.getOwnerDocument() != null) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, 
                                   DOMMessageFormatter.formatMessage(
                                   DOMMessageFormatter.XML_DOMAIN, 
                                   "WRONG_DOCUMENT_ERR", null));
        }
        DocumentImpl doc = new WMLDocumentImpl(doctype);
        // If namespaceURI and qualifiedName are null return a Document with no document element.
        if (qualifiedName != null || namespaceURI != null) {
            Element e = doc.createElementNS(namespaceURI, qualifiedName);
            doc.appendChild(e);
        }
        return doc;
    }
}

