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

package dom;

import java.io.PrintWriter;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * A sample DOM counter. This sample program illustrates how to
 * traverse a DOM tree in order to get information about the document.
 * The output of this program shows the time and count of elements,
 * attributes, ignorable whitespaces, and characters appearing in
 * the document. Three times are shown: the parse time, the first
 * traversal of the document, and the second traversal of the tree.
 * <p>
 * This class is useful as a "poor-man's" performance tester to
 * compare the speed and accuracy of various DOM parsers. However,
 * it is important to note that the first parse time of a parser
 * will include both VM class load time and parser initialization
 * that would not be present in subsequent parses with the same
 * file.
 * <p>
 * <strong>Note:</strong> The results produced by this program
 * should never be accepted as true performance measurements.
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class Counter {

    //
    // Constants
    //

    // feature ids

    /** Namespaces feature id (http://xml.org/sax/features/namespaces). */
    protected static final String NAMESPACES_FEATURE_ID = "http://xml.org/sax/features/namespaces";

    /** Validation feature id (http://xml.org/sax/features/validation). */
    protected static final String VALIDATION_FEATURE_ID = "http://xml.org/sax/features/validation";

    /** Schema validation feature id (http://apache.org/xml/features/validation/schema). */
    protected static final String SCHEMA_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/schema";

    /** Schema full checking feature id (http://apache.org/xml/features/validation/schema-full-checking). */
    protected static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";
    
    /** Honour all schema locations feature id (http://apache.org/xml/features/honour-all-schemaLocations). */
    protected static final String HONOUR_ALL_SCHEMA_LOCATIONS_ID = "http://apache.org/xml/features/honour-all-schemaLocations";
    
    /** Validate schema annotations feature id (http://apache.org/xml/features/validate-annotations). */
    protected static final String VALIDATE_ANNOTATIONS_ID = "http://apache.org/xml/features/validate-annotations";
    
    /** Dynamic validation feature id (http://apache.org/xml/features/validation/dynamic). */
    protected static final String DYNAMIC_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/dynamic";
    
    /** XInclude feature id (http://apache.org/xml/features/xinclude). */
    protected static final String XINCLUDE_FEATURE_ID = "http://apache.org/xml/features/xinclude";
    
    /** XInclude fixup base URIs feature id (http://apache.org/xml/features/xinclude/fixup-base-uris). */
    protected static final String XINCLUDE_FIXUP_BASE_URIS_FEATURE_ID = "http://apache.org/xml/features/xinclude/fixup-base-uris";
    
    /** XInclude fixup language feature id (http://apache.org/xml/features/xinclude/fixup-language). */
    protected static final String XINCLUDE_FIXUP_LANGUAGE_FEATURE_ID = "http://apache.org/xml/features/xinclude/fixup-language";

    // default settings

    /** Default parser name (dom.wrappers.Xerces). */
    protected static final String DEFAULT_PARSER_NAME = "dom.wrappers.Xerces";

    /** Default repetition (1). */
    protected static final int DEFAULT_REPETITION = 1;

    /** Default namespaces support (true). */
    protected static final boolean DEFAULT_NAMESPACES = true;

    /** Default validation support (false). */
    protected static final boolean DEFAULT_VALIDATION = false;

    /** Default Schema validation support (false). */
    protected static final boolean DEFAULT_SCHEMA_VALIDATION = false;

    /** Default Schema full checking support (false). */
    protected static final boolean DEFAULT_SCHEMA_FULL_CHECKING = false;
    
    /** Default honour all schema locations (false). */
    protected static final boolean DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS = false;
    
    /** Default validate schema annotations (false). */
    protected static final boolean DEFAULT_VALIDATE_ANNOTATIONS = false;

    /** Default dynamic validation support (false). */
    protected static final boolean DEFAULT_DYNAMIC_VALIDATION = false;
    
    /** Default XInclude processing support (false). */
    protected static final boolean DEFAULT_XINCLUDE = false;
    
    /** Default XInclude fixup base URIs support (true). */
    protected static final boolean DEFAULT_XINCLUDE_FIXUP_BASE_URIS = true;
    
    /** Default XInclude fixup language support (true). */
    protected static final boolean DEFAULT_XINCLUDE_FIXUP_LANGUAGE = true;

    //
    // Data
    //

    /** Number of elements. */
    protected long fElements;

    /** Number of attributes. */
    protected long fAttributes;

    /** Number of characters. */
    protected long fCharacters;

    /** Number of ignorable whitespace characters. */
    protected long fIgnorableWhitespace;

    /** Document information. */
    protected ParserWrapper.DocumentInfo fDocumentInfo;

    //
    // Public methods
    //

    /** Sets the parser wrapper. */
    public void setDocumentInfo(ParserWrapper.DocumentInfo documentInfo) {
        fDocumentInfo = documentInfo;
    } // setDocumentInfo(ParserWrapper.DocumentInfo)

    /** Traverses the specified node, recursively. */
    public void count(Node node) {

        // is there anything to do?
        if (node == null) {
            return;
        }

        int type = node.getNodeType();
        switch (type) {
            case Node.DOCUMENT_NODE: {
                fElements = 0;
                fAttributes = 0;
                fCharacters = 0;
                fIgnorableWhitespace = 0;
                Document document = (Document)node;
                count(document.getDocumentElement());
                break;
            }

            case Node.ELEMENT_NODE: {
                fElements++;
                NamedNodeMap attrs = node.getAttributes();
                if (attrs != null) {
                    fAttributes += attrs.getLength();
                }
                // drop through to entity reference
            }

            case Node.ENTITY_REFERENCE_NODE: {
                Node child = node.getFirstChild();
                while (child != null) {
                    count(child);
                    child = child.getNextSibling();
                }
                break;
            }

            case Node.CDATA_SECTION_NODE: {
                fCharacters += ((Text)node).getLength();
                break;
            }

            case Node.TEXT_NODE: {
                if (fDocumentInfo != null) {
                    Text text = (Text)node;
                    int length = text.getLength();
                    if (fDocumentInfo.isIgnorableWhitespace(text)) {
                        fIgnorableWhitespace += length;
                    }
                    else {
                        fCharacters += length;
                    }
                }
                break;
            }
        }

    } // count(Node)

    /** Prints the results. */
    public void printResults(PrintWriter out, String uri,
                             long parse, long traverse1, long traverse2,
                             int repetition) {

        // filename.xml: 631/200/100 ms (4 elems, 0 attrs, 78 spaces, 0 chars)
        out.print(uri);
        out.print(": ");
        if (repetition == 1) {
            out.print(parse);
        }
        else {
            out.print(parse);
            out.print('/');
            out.print(repetition);
            out.print('=');
            out.print(parse/repetition);
        }
        out.print(';');
        out.print(traverse1);
        out.print(';');
        out.print(traverse2);
        out.print(" ms (");
        out.print(fElements);
        out.print(" elems, ");
        out.print(fAttributes);
        out.print(" attrs, ");
        out.print(fIgnorableWhitespace);
        out.print(" spaces, ");
        out.print(fCharacters);
        out.print(" chars)");
        out.println();
        out.flush();

    } // printResults(PrintWriter,String,long,long,long)

    //
    // MAIN
    //

    /** Main program entry point. */
    public static void main(String argv[]) {

        // is there anything to do?
        if (argv.length == 0) {
            printUsage();
            System.exit(1);
        }

        // variables
        Counter counter = new Counter();
        PrintWriter out = new PrintWriter(System.out);
        ParserWrapper parser = null;
        int repetition = DEFAULT_REPETITION;
        boolean namespaces = DEFAULT_NAMESPACES;
        boolean validation = DEFAULT_VALIDATION;
        boolean schemaValidation = DEFAULT_SCHEMA_VALIDATION;
        boolean schemaFullChecking = DEFAULT_SCHEMA_FULL_CHECKING;
        boolean honourAllSchemaLocations = DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS;
        boolean validateAnnotations = DEFAULT_VALIDATE_ANNOTATIONS;
        boolean dynamicValidation = DEFAULT_DYNAMIC_VALIDATION;
        boolean xincludeProcessing = DEFAULT_XINCLUDE;
        boolean xincludeFixupBaseURIs = DEFAULT_XINCLUDE_FIXUP_BASE_URIS;
        boolean xincludeFixupLanguage = DEFAULT_XINCLUDE_FIXUP_LANGUAGE;

        // process arguments
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg.startsWith("-")) {
                String option = arg.substring(1);
                if (option.equals("p")) {
                    // get parser name
                    if (++i == argv.length) {
                        System.err.println("error: Missing argument to -p option.");
                    }
                    String parserName = argv[i];

                    // create parser
                    try {
                        parser = (ParserWrapper)Class.forName(parserName).newInstance();
                    }
                    catch (Exception e) {
                        parser = null;
                        System.err.println("error: Unable to instantiate parser ("+parserName+")");
                    }
                    continue;
                }
                if (option.equals("x")) {
                    if (++i == argv.length) {
                        System.err.println("error: Missing argument to -x option.");
                        continue;
                    }
                    String number = argv[i];
                    try {
                        int value = Integer.parseInt(number);
                        if (value < 1) {
                            System.err.println("error: Repetition must be at least 1.");
                            continue;
                        }
                        repetition = value;
                    }
                    catch (NumberFormatException e) {
                        System.err.println("error: invalid number ("+number+").");
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
                if (option.equalsIgnoreCase("hs")) {
                    honourAllSchemaLocations = option.equals("hs");
                    continue;
                }
                if (option.equalsIgnoreCase("va")) {
                    validateAnnotations = option.equals("va");
                    continue;
                }
                if (option.equalsIgnoreCase("dv")) {
                    dynamicValidation = option.equals("dv");
                    continue;
                }
                if (option.equalsIgnoreCase("xi")) {
                    xincludeProcessing = option.equals("xi");
                    continue;
                }
                if (option.equalsIgnoreCase("xb")) {
                    xincludeFixupBaseURIs = option.equals("xb");
                    continue;
                }
                if (option.equalsIgnoreCase("xl")) {
                    xincludeFixupLanguage = option.equals("xl");
                    continue;
                }
                if (option.equals("h")) {
                    printUsage();
                    continue;
                }
            }

            // use default parser?
            if (parser == null) {

                // create parser
                try {
                    parser = (ParserWrapper)Class.forName(DEFAULT_PARSER_NAME).newInstance();
                }
                catch (Exception e) {
                    System.err.println("error: Unable to instantiate parser ("+DEFAULT_PARSER_NAME+")");
                    continue;
                }
            }

            // set parser features
            try {
                parser.setFeature(NAMESPACES_FEATURE_ID, namespaces);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+NAMESPACES_FEATURE_ID+")");
            }
            try {
                parser.setFeature(VALIDATION_FEATURE_ID, validation);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+VALIDATION_FEATURE_ID+")");
            }
            try {
                parser.setFeature(SCHEMA_VALIDATION_FEATURE_ID, schemaValidation);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+SCHEMA_VALIDATION_FEATURE_ID+")");
            }
            try {
                parser.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, schemaFullChecking);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
            }
            try {
                parser.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS_ID, honourAllSchemaLocations);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
            }
            try {
                parser.setFeature(VALIDATE_ANNOTATIONS_ID, validateAnnotations);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+VALIDATE_ANNOTATIONS_ID+")");
            }
            try {
                parser.setFeature(DYNAMIC_VALIDATION_FEATURE_ID, dynamicValidation);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+DYNAMIC_VALIDATION_FEATURE_ID+")");
            }
            try {
                parser.setFeature(XINCLUDE_FEATURE_ID, xincludeProcessing);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+XINCLUDE_FEATURE_ID+")");
            }
            try {
                parser.setFeature(XINCLUDE_FIXUP_BASE_URIS_FEATURE_ID, xincludeFixupBaseURIs);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+XINCLUDE_FIXUP_BASE_URIS_FEATURE_ID+")");
            }
            try {
                parser.setFeature(XINCLUDE_FIXUP_LANGUAGE_FEATURE_ID, xincludeFixupLanguage);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+XINCLUDE_FIXUP_LANGUAGE_FEATURE_ID+")");
            }

            // parse file
            try {
                long beforeParse = System.currentTimeMillis();
                Document document = null;
                for (int j = 0; j < repetition; j++) {
                    document = parser.parse(arg);
                }
                long afterParse = System.currentTimeMillis();
                long parse = afterParse - beforeParse;

                ParserWrapper.DocumentInfo documentInfo = parser.getDocumentInfo();
                counter.setDocumentInfo(documentInfo);

                long beforeTraverse1 = System.currentTimeMillis();
                counter.count(document);
                long afterTraverse1 = System.currentTimeMillis();
                long traverse1 = afterTraverse1 - beforeTraverse1;

                long beforeTraverse2 = System.currentTimeMillis();
                counter.count(document);
                long afterTraverse2 = System.currentTimeMillis();
                long traverse2 = afterTraverse2 - beforeTraverse2;
                counter.printResults(out, arg, parse, traverse1, traverse2,
                                     repetition);
            }
            catch (SAXParseException e) {
                // ignore
            }
            catch (Exception e) {
                System.err.println("error: Parse error occurred - "+e.getMessage());
                Exception se = e;
                if (e instanceof SAXException) {
                    se = ((SAXException)e).getException();
                }
                if (se != null)
                  se.printStackTrace(System.err);
                else
                  e.printStackTrace(System.err);
            }
        }

    } // main(String[])

    //
    // Private static methods
    //

    /** Prints the usage. */
    private static void printUsage() {

        System.err.println("usage: java dom.Counter (options) uri ...");
        System.err.println();

        System.err.println("options:");
        System.err.println("  -p name     Select parser by name.");
        System.err.println("  -x number   Select number of repetitions.");
        System.err.println("  -n  | -N    Turn on/off namespace processing.");
        System.err.println("  -v  | -V    Turn on/off validation.");
        System.err.println("  -s  | -S    Turn on/off Schema validation support.");
        System.err.println("              NOTE: Not supported by all parsers.");
        System.err.println("  -f  | -F    Turn on/off Schema full checking.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        System.err.println("  -hs | -HS   Turn on/off honouring of all schema locations.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        System.err.println("  -va | -VA   Turn on/off validation of schema annotations.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        System.err.println("  -dv | -DV   Turn on/off dynamic validation.");
        System.err.println("              NOTE: Not supported by all parsers.");
        System.err.println("  -xi | -XI   Turn on/off XInclude processing.");
        System.err.println("              NOTE: Not supported by all parsers.");
        System.err.println("  -xb | -XB   Turn on/off base URI fixup during XInclude processing.");
        System.err.println("              NOTE: Requires use of -xi and not supported by all parsers.");
        System.err.println("  -xl | -XL   Turn on/off language fixup during XInclude processing.");
        System.err.println("              NOTE: Requires use of -xi and not supported by all parsers.");
        System.err.println("  -h          This help screen.");
        System.err.println();

        System.err.println("defaults:");
        System.err.println("  Parser:     "+DEFAULT_PARSER_NAME);
        System.err.println("  Repetition: "+DEFAULT_REPETITION);
        System.err.print("  Namespaces: ");
        System.err.println(DEFAULT_NAMESPACES ? "on" : "off");
        System.err.print("  Validation: ");
        System.err.println(DEFAULT_VALIDATION ? "on" : "off");
        System.err.print("  Schema:     ");
        System.err.println(DEFAULT_SCHEMA_VALIDATION ? "on" : "off");
        System.err.print("  Schema full checking:            ");
        System.err.println(DEFAULT_SCHEMA_FULL_CHECKING ? "on" : "off");
        System.err.print("  Honour all schema locations:     ");
        System.err.println(DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS ? "on" : "off");
        System.err.print("  Validate annotations:            ");
        System.err.println(DEFAULT_VALIDATE_ANNOTATIONS ? "on" : "off");
        System.err.print("  Dynamic:    ");
        System.err.println(DEFAULT_DYNAMIC_VALIDATION ? "on" : "off");
        System.err.print("  XInclude:   ");
        System.err.println(DEFAULT_XINCLUDE ? "on" : "off");
        System.err.print("  XInclude base URI fixup:  ");
        System.err.println(DEFAULT_XINCLUDE_FIXUP_BASE_URIS ? "on" : "off");
        System.err.print("  XInclude language fixup:  ");
        System.err.println(DEFAULT_XINCLUDE_FIXUP_LANGUAGE ? "on" : "off");

    } // printUsage()

} // class DOMCount
