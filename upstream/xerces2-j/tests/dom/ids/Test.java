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

package dom.ids;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import dom.ParserWrapper;
import dom.util.Assertion;

/**
 * A simple program to test Document.getElementById() and the management
 * of ID attributes. Originally based on dom.Counter.
 * This test takes as input input.xml file
 *
 * @author Andy Clark, IBM
 * @author Arnaud  Le Hors, IBM
 *
 * @version $Id$
 */
public class Test {

    //
    // Constants
    //

    // feature ids

    protected static final String NAMESPACES_FEATURE_ID =
        "http://xml.org/sax/features/namespaces";

    protected static final String VALIDATION_FEATURE_ID =
        "http://xml.org/sax/features/validation";

    protected static final String SCHEMA_VALIDATION_FEATURE_ID =
        "http://apache.org/xml/features/validation/schema";

    protected static final String SCHEMA_FULL_CHECKING_FEATURE_ID =
        "http://apache.org/xml/features/validation/schema-full-checking";

    protected static final String DEFERRED_DOM_FEATURE_ID =
        "http://apache.org/xml/features/dom/defer-node-expansion";

    // default settings

    protected static final String DEFAULT_PARSER_NAME = "dom.wrappers.Xerces";

    protected static final boolean DEFAULT_NAMESPACES = true;

    protected static final boolean DEFAULT_VALIDATION = false;

    protected static final boolean DEFAULT_SCHEMA_VALIDATION = false;

    protected static final boolean DEFAULT_SCHEMA_FULL_CHECKING = false;

    // Xerces specific feature
    protected static final boolean DEFAULT_DEFERRED_DOM = true;

    //
    // Public methods
    //

    /** Performs the actual test. */
    public void test(Document doc) {

        System.out.println("DOM IDs Test...");
        Element el = doc.getElementById("one.worker");
        Assertion.verify(el != null);
        Element el2 = doc.getElementById("one.worker there");
        Assertion.verify(el2 == null);

        if (el != null) {
            Assertion.equals(el.getAttribute("id"), "one.worker");
            el.setAttribute("id", "my.worker");
            el2 = doc.getElementById("my.worker");
            Assertion.verify(el2 == el);
            
            el2 = doc.getElementById("one.worker");
            Assertion.verify(el2 == null);
            el.removeAttribute("id");
            el2 = doc.getElementById("my.worker");
            Assertion.verify(el2 == null);
        }

        // find default id attribute and check its value
        NodeList elementList = doc.getElementsByTagName("person");
        Element testEmployee = (Element)elementList.item(1);
        Attr id = testEmployee.getAttributeNode("id2");
        Assertion.verify(id.getNodeValue().equals("id02"), "value == 'id02'");


        Element elem = doc.getElementById("id02");
        Assertion.verify(elem.getNodeName().equals("person"), "return by id 'id02'");
        
        // 
        // remove default attribute and check on retrieval what its value
        Attr removedAttr = testEmployee.removeAttributeNode(id);
        String value = testEmployee.getAttribute("id2");
        Assertion.verify(value.equals("default.id"), "value='default.id'");


        elem = doc.getElementById("default.id");
        Assertion.verify(elem !=null, "elem by id 'default.id'");


        elem = doc.getElementById("id02");
        Assertion.verify(elem ==null, "elem by id '02'");
        
        Element person = (Element)doc.getElementsByTagNameNS(null, "person").item(0);
        person.removeAttribute("id");
        person.removeAttribute("id2");
        person.setAttributeNS(null, "idAttr", "eb0009");
        person.setIdAttribute("idAttr", true);
        
        elem = doc.getElementById("eb0009");
        Assertion.verify(elem !=null, "elem by id 'eb0009'");
       
        doc.getDocumentElement().removeChild(person);
        elem = doc.getElementById("eb0009");
        Assertion.verify(elem ==null, "element with id 'eb0009 removed'");

        doc.getDocumentElement().appendChild(person);
        elem = doc.getElementById("eb0009");
        Assertion.verify(elem !=null, "elem by id 'eb0009'");
        Attr attr = (Attr)person.getAttributeNode("idAttr");
        Assertion.verify(attr.isId(), "attribute is id");

        person.setIdAttribute("idAttr", false);
        elem = doc.getElementById("eb0009");
        Assertion.verify(elem ==null, "element with id 'eb0009 removed'");
        
        Assertion.verify(!attr.isId(), "attribute is not id");        

        System.out.println("done.");

    } // test(Document)

    //
    // MAIN
    //

    /** Main program entry point. */
    public static void main(String argv[]) {

        // is there anything to do?
        /*if (argv.length == 0) {
            printUsage();
            System.exit(1);
        } */

        
        // variables
        Test test = new Test();
        ParserWrapper parser = null;
        boolean namespaces = DEFAULT_NAMESPACES;
        boolean validation = DEFAULT_VALIDATION;
        boolean schemaValidation = DEFAULT_SCHEMA_VALIDATION;
        boolean schemaFullChecking = DEFAULT_SCHEMA_FULL_CHECKING;
        boolean deferredDom = DEFAULT_DEFERRED_DOM;
        
        String inputfile="tests/dom/ids/input.xml";
        
        // process arguments
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg.startsWith("-")) {
                String option = arg.substring(1);
                if (option.equals("p")) {
                    // get parser name
                    if (++i == argv.length) {
                        System.err.println("error: Missing argument to -p"
                                           + " option.");
                    }
                    String parserName = argv[i];

                    // create parser
                    try {
                        parser = (ParserWrapper)
                            Class.forName(parserName).newInstance();
                    }
                    catch (Exception e) {
                        parser = null;
                        System.err.println("error: Unable to instantiate "
                                           + "parser (" + parserName + ")");
                    }
                    continue;
                }
                if (option.equalsIgnoreCase("n")) {
                    namespaces = option.equals("n");
                    continue;
                }
                if (option.equalsIgnoreCase("v")) {
                    validation = option.equals("v");
                    continue;
                }
                if (option.equalsIgnoreCase("s")) {
                    schemaValidation = option.equals("s");
                    continue;
                }
                if (option.equalsIgnoreCase("f")) {
                    schemaFullChecking = option.equals("f");
                    continue;
                }
                if (option.equalsIgnoreCase("d")) {
                    deferredDom = option.equals("d");
                    continue;
                }
                if (option.equals("h")) {
                    printUsage();
                    continue;
                }
            }
        }

            // use default parser?
            if (parser == null) {

                // create parser
                try {
                    parser = (ParserWrapper)
                        Class.forName(DEFAULT_PARSER_NAME).newInstance();
                }
                catch (Exception e) {
                    System.err.println("error: Unable to instantiate parser ("
                                       + DEFAULT_PARSER_NAME + ")");
                    System.exit(1);
                }
            }

            // set parser features
            try {
                parser.setFeature(NAMESPACES_FEATURE_ID, namespaces);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("
                                   + NAMESPACES_FEATURE_ID + ")");
            }
            try {
                parser.setFeature(VALIDATION_FEATURE_ID, validation);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("
                                   + VALIDATION_FEATURE_ID + ")");
            }
            try {
                parser.setFeature(SCHEMA_VALIDATION_FEATURE_ID,
                                  schemaValidation);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("
                                   + SCHEMA_VALIDATION_FEATURE_ID + ")");
            }
            try {
                parser.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID,
                                  schemaFullChecking);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("
                                   + SCHEMA_FULL_CHECKING_FEATURE_ID + ")");
            }

            if (parser instanceof dom.wrappers.Xerces) {
                try {
                    parser.setFeature(DEFERRED_DOM_FEATURE_ID,
                                      deferredDom);
                }
                catch (SAXException e) {
                    System.err.println("warning: Parser does not support " +
                                       "feature (" +
                                       DEFERRED_DOM_FEATURE_ID + ")");
                }
            }

            // parse file
            try {
                Document document = null;
                document = parser.parse(inputfile);
                test.test(document);
            }
            catch (SAXParseException e) {
                // ignore
            }
            catch (Exception e) {
                System.err.println("error: Parse error occurred - " +
                                   e.getMessage());
                Exception se = e;
                if (e instanceof SAXException) {
                    se = ((SAXException)e).getException();
                }
                if (se != null)
                  se.printStackTrace(System.err);
                else
                  e.printStackTrace(System.err);
            }
        

    } // main(String[])

    //
    // Private static methods
    //

    /** Prints the usage. */
    private static void printUsage() {

        System.err.println("usage: java dom.ids.Test (options) " +
                           "...data/personal.xml");
        System.err.println();

        System.err.println("options:");
        System.err.println("  -p name    Select parser by name.");
        System.err.println("  -d  | -D   Turn on/off (Xerces) deferred DOM.");
        System.err.println("  -n  | -N   Turn on/off namespace processing.");
        System.err.println("  -v  | -V   Turn on/off validation.");
        System.err.println("  -s  | -S   Turn on/off Schema validation " +
                           "support.");
        System.err.println("             NOTE: Not supported by all parsers.");
        System.err.println("  -f  | -F   Turn on/off Schema full checking.");
        System.err.println("             NOTE: Requires use of -s and not " +
                           "supported by all parsers.");
        System.err.println("  -h         This help screen.");
        System.err.println();

        System.err.println("defaults:");
        System.err.println("  Parser:     " + DEFAULT_PARSER_NAME);
        System.err.println("  Xerces Deferred DOM: " +
                           (DEFAULT_DEFERRED_DOM ? "on" : "off"));
        System.err.println("  Namespaces: " +
                           (DEFAULT_NAMESPACES ? "on" : "off"));
        System.err.println("  Validation: " +
                           (DEFAULT_VALIDATION ? "on" : "off"));
        System.err.println("  Schema:     " +
                           (DEFAULT_SCHEMA_VALIDATION ? "on" : "off"));
        System.err.println("  Schema full checking:     " +
                           (DEFAULT_SCHEMA_FULL_CHECKING ? "on" : "off"));

    } // printUsage()

} // class Test
