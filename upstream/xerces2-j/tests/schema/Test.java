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

package schema;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import org.apache.xerces.parsers.SAXParser;

/**
 * Test Schema processing
 *
 * @author Khaled Noaman, IBM
 */
public class Test extends DefaultHandler {


    // feature ids

    /** Namespaces feature id (http://xml.org/sax/features/namespaces). */
    protected static final String NAMESPACES_FEATURE_ID = "http://xml.org/sax/features/namespaces";

    /** Namespace prefixes feature id (http://xml.org/sax/features/namespace-prefixes). */
    protected static final String NAMESPACE_PREFIXES_FEATURE_ID = "http://xml.org/sax/features/namespace-prefixes";

    /** Validation feature id (http://xml.org/sax/features/validation). */
    protected static final String VALIDATION_FEATURE_ID = "http://xml.org/sax/features/validation";

    /** Schema validation feature id (http://apache.org/xml/features/validation/schema). */
    protected static final String SCHEMA_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/schema";

    /** Schema full checking feature id (http://apache.org/xml/features/validation/schema-full-checking). */
    protected static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";

    /** Dynamic validation feature id (http://apache.org/xml/features/validation/dynamic). */
    protected static final String DYNAMIC_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/dynamic";


    // property ids

    /** schema noNamespaceSchemaLocation property id (http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation). */
    protected static final String SCHEMA_NONS_LOCATION_ID = "http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation";


    // Default data source location
    protected static final String SOURCE_LOC_ID = "./tests/schema/";


    // Default constructor
    public Test() {
    }


    // ErrorHandler methods

    public void warning(SAXParseException ex) throws SAXException {
        printError("Warning", ex);
    }

    public void error(SAXParseException ex) throws SAXException {
        printError("Error", ex);
    }

    public void fatalError(SAXParseException ex) throws SAXException {
        printError("Fatal Error", ex);
    }


    // Protected methods
    protected void printError(String type, SAXParseException ex) {

        System.err.print("[");
        System.err.print(type);
        System.err.print("] ");
        if (ex== null) {
            System.out.println("!!!");
        }
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
    }

    public void testSettingNoNamespaceSchemaLocation() throws Exception {

        System.err.println("#");
        System.err.println("# Testing Setting noNamespaceSchemaLocation property");
        System.err.println("#");

        try {
            SAXParser parser = new org.apache.xerces.parsers.SAXParser();

            // Set features
            parser.setFeature(NAMESPACES_FEATURE_ID, true);
            parser.setFeature(NAMESPACE_PREFIXES_FEATURE_ID, false);
            parser.setFeature(VALIDATION_FEATURE_ID, true);
            parser.setFeature(SCHEMA_VALIDATION_FEATURE_ID, true);
            parser.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, false);
            parser.setFeature(DYNAMIC_VALIDATION_FEATURE_ID, false);

            // Set properties
            parser.setProperty(SCHEMA_NONS_LOCATION_ID, "personal.xsd");

            // Set handlers
            parser.setContentHandler(this);
            parser.setErrorHandler(this);

            // parse document
            parser.parse(SOURCE_LOC_ID + "personal-schema.xml");
            System.err.println("Pass: " + SOURCE_LOC_ID + "personal-schema.xml");
            System.err.println();
        }
        catch (SAXException e) {
            System.err.println("Fail:" + e.getMessage());
        }
    }


    /** Main program entry point. */
    public static void main(String argv[]) throws Exception {

        Test test = new Test();
        test.testSettingNoNamespaceSchemaLocation();
    }
}
