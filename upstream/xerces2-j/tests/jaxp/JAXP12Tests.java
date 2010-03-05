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

package jaxp;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * Test JAXP 1.2 specific features
 *
 * @author Edwin Goei
 * 
 * @version $Id$
 */
public class JAXP12Tests extends TestCase implements JAXPConstants {
    protected DocumentBuilderFactory dbf;
    protected DocumentBuilder db;
    protected DocumentBuilder dbn;
    protected DocumentBuilder dbnv;

    SAXParserFactory spf;
    SAXParser spn;
    SAXParser spnv;

    public JAXP12Tests(String name) {
        super(name);
    }

    private static class MyErrorHandler implements ErrorHandler {
        public void fatalError(SAXParseException x) throws SAXException {
            x.printStackTrace();
            fail("ErrorHandler#fatalError() should not have been" +
                 " called: " +
                 x.getMessage() +
                 " [line = " + x.getLineNumber() + ", systemId = " +
                 x.getSystemId() + "]");
        }
        public void error(SAXParseException x) throws SAXException {
            x.printStackTrace();
            fail("ErrorHandler#error() should not have been called: " +
                 x.getMessage() +
                 " [line = " + x.getLineNumber() + ", systemId = " +
                 x.getSystemId() + "]");
        }
        public void warning(SAXParseException x) throws SAXException {
            x.printStackTrace();
            fail("ErrorHandler#warning() should not have been called: "
                 + x.getMessage() +
                 " [line = " + x.getLineNumber() + ", systemId = " +
                 x.getSystemId() + "]");
        }
    }

    /**
     * Overrides method error() to see if it was ever called
     */
    private static class ErrorHandlerCheck extends MyErrorHandler {
        Boolean gotError = Boolean.FALSE;
        public void error(SAXParseException x) throws SAXException {
            gotError = Boolean.TRUE;
            throw x;
        }
        public Object getStatus() {
            return gotError;
        }
    };

    protected void setUp() throws Exception {
        dbf = DocumentBuilderFactory.newInstance();
        db = dbf.newDocumentBuilder();  // non-namespaceAware version
        dbf.setNamespaceAware(true);
        dbn = dbf.newDocumentBuilder(); // namespaceAware version
        dbn.setErrorHandler(new MyErrorHandler());
        dbf.setValidating(true);
        dbnv = dbf.newDocumentBuilder(); // validating version
        dbnv.setErrorHandler(new MyErrorHandler());

        spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spn = spf.newSAXParser();
        spf.setValidating(true);
        spnv = spf.newSAXParser();
    }

    /**
     * Should not cause a validation error.  Problem is that you get same
     * result if no validation is occurring at all.  See other tests that
     * checks that validation actually occurs.
     */
    public void testSaxParseXSD() throws Exception {
        spnv.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
        XMLReader xr = spnv.getXMLReader();
        xr.setErrorHandler(new MyErrorHandler());
        xr.parse(new InputData("personal-schema.xml"));
    }

    /**
     * Should cause a validation error.  Checks that validation is indeed
     * occurring.  Warning: does not actually check for particular
     * validation error, but assumes any exception thrown is a validation
     * error of the type we expect.
     */
    public void testSaxParseXSD2() throws Exception {
        spnv.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
        XMLReader xr = spnv.getXMLReader();

        ErrorHandlerCheck meh = new ErrorHandlerCheck();
        xr.setErrorHandler(meh);
        try {
            xr.parse(new InputData("personal-schema-err.xml"));
            fail("ErrorHandler.error() should have thrown a SAXParseException");
        } catch (SAXException x) {
            assertEquals("Should have caused validation error.",
                         Boolean.TRUE, meh.getStatus());
        }
    }

    /**
     * Check that setting schemaSource overrides xsi: hint in instance doc
     */
    public void testSaxParseSchemaSource() throws Exception {
        spnv.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
        spnv.setProperty(JAXP_SCHEMA_SOURCE, new InputData("personal.xsd"));
        XMLReader xr = spnv.getXMLReader();
        xr.setErrorHandler(new MyErrorHandler());
        xr.parse(new InputData("personal-schema-badhint.xml"));
        xr.parse(new InputData("personal-schema-nohint.xml"));
    }

    /**
     * Turn on DTD validation and expect an error b/c instance doc has no
     * doctypedecl
     */
    public void testSaxParseNoXSD() throws Exception {
        XMLReader xr = spnv.getXMLReader();

        ErrorHandlerCheck meh = new ErrorHandlerCheck();
        xr.setErrorHandler(meh);
        try {
            xr.parse(new InputData("personal-schema.xml"));
            fail("ErrorHandler.error() should have thrown a SAXParseException");
        } catch (SAXException x) {
            assertEquals("Should have caused validation error.",
                         Boolean.TRUE, meh.getStatus());
        }
    }

    /**
     * Should not cause a validation error.  Problem is that you get same
     * result if no validation is occurring at all.  See other tests that
     * checks that validation actually occurs.
     */
    public void testDomParseXSD() throws Exception {
        dbf.setNamespaceAware(true);
        dbf.setValidating(true);
        dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
        DocumentBuilder mydb = dbf.newDocumentBuilder();
        mydb.setErrorHandler(new MyErrorHandler());
        mydb.parse(new InputData("personal-schema.xml"));
    }

    /**
     * Should cause a validation error.  Checks that validation is indeed
     * occurring.  Warning: does not actually check for particular
     * validation error, but assumes any exception thrown is a validation
     * error of the type we expect.
     */
    public void testDomParseXSD2() throws Exception {
        dbf.setNamespaceAware(true);
        dbf.setValidating(true);
        dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
        DocumentBuilder mydb = dbf.newDocumentBuilder();

        ErrorHandlerCheck meh = new ErrorHandlerCheck();
        mydb.setErrorHandler(meh);
        try {
            mydb.parse(new InputData("personal-schema-err.xml"));
            fail("ErrorHandler.error() should have thrown a SAXParseException");
        } catch (SAXException x) {
            assertEquals("Should have caused validation error.",
                         Boolean.TRUE, meh.getStatus());
        }
    }

    /**
     * Check that setting schemaSource overrides xsi: hint in instance doc
     */
    public void testDomParseSchemaSource() throws Exception {
        dbf.setNamespaceAware(true);
        dbf.setValidating(true);
        dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
        dbf.setAttribute(JAXP_SCHEMA_SOURCE, new InputData("personal.xsd"));
        DocumentBuilder mydb = dbf.newDocumentBuilder();
        mydb.setErrorHandler(new MyErrorHandler());
        mydb.parse(new InputData("personal-schema-badhint.xml"));
        mydb.parse(new InputData("personal-schema-nohint.xml"));
    }

    /**
     * Turn on DTD validation and expect an error b/c instance doc has no
     * doctypedecl
     */
    public void testDomParseNoXSD() throws Exception {
        dbf.setNamespaceAware(true);
        dbf.setValidating(true);
        DocumentBuilder mydb = dbf.newDocumentBuilder();

        ErrorHandlerCheck meh = new ErrorHandlerCheck();
        mydb.setErrorHandler(meh);
        try {
            mydb.parse(new InputData("personal-schema.xml"));
            fail("ErrorHandler.error() should have thrown a SAXParseException");
        } catch (SAXException x) {
            assertEquals("Should have caused validation error.",
                         Boolean.TRUE, meh.getStatus());
        }
    }


}
