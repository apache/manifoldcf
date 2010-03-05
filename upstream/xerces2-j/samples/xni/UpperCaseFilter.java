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

package xni;

import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XNIException;
 
/**
 * This sample demonstrates how to create a filter for the document
 * "streaming" information set that turns element names into upper
 * case.
 * <p>
 * <strong>Note:</strong> This sample does not contain a 
 * <code>main</code> method and cannot be run. It is only for
 * demonstration purposes.
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class UpperCaseFilter
    extends PassThroughFilter {
    
    //
    // Data
    //
    
    /** 
     * Temporary QName structure used by the <code>toUpperCase</code>
     * method. It should not be used anywhere else.
     *
     * @see #toUpperCase
     */
    private final QName fQName = new QName();

    //
    // XMLDocumentHandler methods
    //
    
    /**
     * The start of an element.
     * 
     * @param element    The name of the element.
     * @param attributes The element attributes.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startElement(QName element, XMLAttributes attributes, Augmentations augs)
        throws XNIException {
        super.startElement(toUpperCase(element), attributes, augs);
    } // startElement(QName,XMLAttributes)
    
    /**
     * An empty element.
     * 
     * @param element    The name of the element.
     * @param attributes The element attributes.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs)
        throws XNIException {
        super.emptyElement(toUpperCase(element), attributes, augs);
    } // emptyElement(QName,XMLAttributes)
    
    /**
     * The end of an element.
     * 
     * @param element The name of the element.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endElement(QName element, Augmentations augs)
        throws XNIException {
        super.endElement(toUpperCase(element), augs);
    } // endElement(QName)
    
    //
    // Protected methods
    //
    
    /**
     * This method upper-cases the prefix, localpart, and rawname
     * fields in the specified QName and returns a different
     * QName object containing the upper-cased string values.
     *
     * @param qname The QName to upper-case.
     */
    protected QName toUpperCase(QName qname) {
        String prefix = qname.prefix != null
                      ? qname.prefix.toUpperCase() : null;
        String localpart = qname.localpart != null
                         ? qname.localpart.toUpperCase() : null;
        String rawname = qname.rawname != null
                       ? qname.rawname.toUpperCase() : null;
        String uri = qname.uri;
        fQName.setValues(prefix, localpart, rawname, uri);
        return fQName;
    } // toUpperCase(QName):QName

} // class UpperCaseFilter
