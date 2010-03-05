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

package dom.wrappers;

import dom.ParserWrapper;

import org.apache.xerces.dom.TextImpl;
import org.apache.xerces.parsers.DOMParser;

import org.w3c.dom.Document;
import org.w3c.dom.Text;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * Wraps the Xerces DOM parser.
 *
 * @version  $Id$
 */
public class Xerces 
    implements ParserWrapper, ParserWrapper.DocumentInfo, ErrorHandler {

    //
    // Data
    //

    /** Parser. */
    protected DOMParser parser = new DOMParser();

    //
    // Constructors
    //

    /** Default constructor. */
    public Xerces() {
        parser.setErrorHandler(this);
    } // <init>()

    //
    // ParserWrapper methods
    //

    /** Parses the specified URI and returns the document. */
    public Document parse(String uri) throws Exception {
        parser.parse(uri);
        return parser.getDocument();
    } // parse(String):Document

    /** Sets a feature. */
    public void setFeature(String featureId, boolean state)
        throws SAXNotRecognizedException, SAXNotSupportedException {
        parser.setFeature(featureId, state);
    } // setFeature(String,boolean)

    /** Returns the document information. */
    public ParserWrapper.DocumentInfo getDocumentInfo() {
        return this;
    } // getDocumentInfo():DocumentInfo

    //
    // DocumentInfo methods
    //

    /** 
     * Returns true if the specified text node is ignorable whitespace. 
     */
    public boolean isIgnorableWhitespace(Text text) {
        return ((TextImpl)text).isIgnorableWhitespace();
    }

    //
    // ErrorHandler methods
    //

    /** Warning. */
    public void warning(SAXParseException ex) throws SAXException {
        printError("Warning", ex);
    } // warning(SAXParseException)

    /** Error. */
    public void error(SAXParseException ex) throws SAXException {
        printError("Error", ex);
    } // error(SAXParseException)

    /** Fatal error. */
    public void fatalError(SAXParseException ex) throws SAXException {
        printError("Fatal Error", ex);
        throw ex;
    } // fatalError(SAXParseException)

    //
    // Protected methods
    //

    /** Prints the error message. */
    protected void printError(String type, SAXParseException ex) {

        System.err.print("[");
        System.err.print(type);
        System.err.print("] ");
        String systemId = ex.getSystemId();
        if (systemId != null) {
            int index = systemId.lastIndexOf('/');
            if (index != -1)
                systemId = systemId.substring(index + 1);
            System.err.print(systemId);
        }
        System.err.print(':');
        System.err.print(ex.getLineNumber());
        System.err.print(':');
        System.err.print(ex.getColumnNumber());
        System.err.print(": ");
        System.err.print(ex.getMessage());
        System.err.println();
        System.err.flush();

    } // printError(String,SAXParseException)

} // class Xerces
