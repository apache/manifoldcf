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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.apache.xerces.parsers.XMLDocumentParser;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.apache.xerces.xni.parser.XMLParserConfiguration;
import org.apache.xerces.xni.parser.XMLPullParserConfiguration;

/**
 * A sample XNI writer. This sample program illustrates how to
 * use the XNI XMLDocumentHandler callbacks in order to print a
 * document that is parsed.
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class Writer
    extends XMLDocumentParser
    implements XMLErrorHandler {

    //
    // Constants
    //

    // feature ids

    /** Namespaces feature id (http://xml.org/sax/features/namespaces). */
    protected static final String NAMESPACES_FEATURE_ID =
        "http://xml.org/sax/features/namespaces";

    /** Validation feature id (http://xml.org/sax/features/validation). */
    protected static final String VALIDATION_FEATURE_ID =
        "http://xml.org/sax/features/validation";

    /** Schema validation feature id (http://apache.org/xml/features/validation/schema). */
    protected static final String SCHEMA_VALIDATION_FEATURE_ID =
        "http://apache.org/xml/features/validation/schema";

    /** Schema full checking feature id (http://apache.org/xml/features/validation/schema-full-checking). */
    protected static final String SCHEMA_FULL_CHECKING_FEATURE_ID =
        "http://apache.org/xml/features/validation/schema-full-checking";
    
    /** Honour all schema locations feature id (http://apache.org/xml/features/honour-all-schemaLocations). */
    protected static final String HONOUR_ALL_SCHEMA_LOCATIONS_ID = 
        "http://apache.org/xml/features/honour-all-schemaLocations";

    // default settings

    /** Default parser configuration (org.apache.xerces.parsers.XIncludeAwareParserConfiguration). */
    protected static final String DEFAULT_PARSER_CONFIG =
        "org.apache.xerces.parsers.XIncludeAwareParserConfiguration";

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

    /** Default canonical output (false). */
    protected static final boolean DEFAULT_CANONICAL = false;

    /** Default incremental mode (false). */
    protected static final boolean DEFAULT_INCREMENTAL = false;

    //
    // Data
    //

    /** Print writer. */
    protected PrintWriter fOut;

    /** Canonical output. */
    protected boolean fCanonical;

    /** Element depth. */
    protected int fElementDepth;

    /** Seen root element. */
    protected boolean fSeenRootElement;

    //
    // Constructors
    //

    /** Default constructor. */
    public Writer(XMLParserConfiguration configuration) {
        super(configuration);
        fConfiguration.setErrorHandler(this);
    } // <init>(XMLParserConfiguration)

    //
    // Public methods
    //

    /** Sets whether output is canonical. */
    public void setCanonical(boolean canonical) {
        fCanonical = canonical;
    } // setCanonical(boolean)

    /** Sets the output stream for printing. */
    public void setOutput(OutputStream stream, String encoding)
        throws UnsupportedEncodingException {

        if (encoding == null) {
            encoding = "UTF8";
        }

        java.io.Writer writer = new OutputStreamWriter(stream, encoding);
        fOut = new PrintWriter(writer);

    } // setOutput(OutputStream,String)

    /** Sets the output writer. */
    public void setOutput(java.io.Writer writer) {

        fOut = writer instanceof PrintWriter
             ? (PrintWriter)writer : new PrintWriter(writer);

    } // setOutput(java.io.Writer)

    //
    // XMLDocumentHandler methods
    //

    /** Start document. */
    public void startDocument(XMLLocator locator, String encoding,
            NamespaceContext namespaceContext, Augmentations augs)
        throws XNIException {

        fSeenRootElement = false;
        fElementDepth = 0;

        if (!fCanonical) {
            fOut.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            fOut.flush();
        }

    } // startDocument(XMLLocator,String,NamespaceContext, Augmentations)

    /** Start element. */
    public void startElement(QName element, XMLAttributes attrs, Augmentations augs)
        throws XNIException {

        fSeenRootElement = true;
        fElementDepth++;
        fOut.print('<');
        fOut.print(element.rawname);
        if (attrs != null) {
            /***
            attrs = sortAttributes(attrs);
            /***/
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
                fOut.print(' ');
                fOut.print(attrs.getQName(i));
                fOut.print("=\"");
                normalizeAndPrint(attrs.getValue(i));
                fOut.print('"');
            }
        }
        fOut.print('>');
        fOut.flush();

    } // startElement(QName,XMLAttributes,Augmentations)

    /** Empty element. */
    public void emptyElement(QName element, XMLAttributes attrs, Augmentations augs)
        throws XNIException {

        fSeenRootElement = true;
        fElementDepth++;
        fOut.print('<');
        fOut.print(element.rawname);
        if (attrs != null) {
            /***
            attrs = sortAttributes(attrs);
            /***/
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
                fOut.print(' ');
                fOut.print(attrs.getQName(i));
                fOut.print("=\"");
                normalizeAndPrint(attrs.getValue(i));
                fOut.print('"');
            }
        }
        fOut.print("/>");
        fOut.flush();

    } // emptyElement(QName,XMLAttributes,Augmentations)

    /** Processing instruction. */
    public void processingInstruction(String target, XMLString data, Augmentations augs)
        throws XNIException {

        if (fSeenRootElement) {
            fOut.print('\n');
        }
        fOut.print("<?");
        fOut.print(target);
        if (data != null && data.length > 0) {
            fOut.print(' ');
            fOut.print(data.toString());
        }
        fOut.print("?>");
        if (!fSeenRootElement) {
            fOut.print('\n');
        }
        fOut.flush();

    } // processingInstruction(String,XMLString,Augmentations)

    /** Comment. */
    public void comment(XMLString text, Augmentations augs) throws XNIException {
        if (!fCanonical) {
            if (fSeenRootElement && fElementDepth == 0) {
                fOut.print('\n');
            }
            fOut.print("<!--");
            fOut.print(text.toString());
            fOut.print("-->");
            if (!fSeenRootElement) {
                fOut.print('\n');
            }
            fOut.flush();
        }
    } // comment(XMLString,Augmentations)

    /** Characters. */
    public void characters(XMLString text, Augmentations augs) throws XNIException {

        normalizeAndPrint(text);
        //fOut.println("one call...");
        fOut.flush();

    } // characters(XMLString,Augmentations)

    /** Ignorable whitespace. */
    public void ignorableWhitespace(XMLString text, Augmentations augs) throws XNIException {

        characters(text, augs);
        fOut.flush();

    } // ignorableWhitespace(XMLString,Augmentations)

    /** End element. */
    public void endElement(QName element, Augmentations augs) throws XNIException {

        fElementDepth--;
        fOut.print("</");
        fOut.print(element.rawname);
        fOut.print('>');
        fOut.flush();

    } // endElement(QName,Augmentations)

    /** Start CDATA section. */
    public void startCDATA(Augmentations augs) throws XNIException {
    } // startCDATA(Augmentations)

    /** End CDATA section. */
    public void endCDATA(Augmentations augs) throws XNIException {
    } // endCDATA(Augmentations)

    //
    // XMLDTDHandler methods
    //

    //
    // XMLErrorHandler methods
    //

    /** Warning. */
    public void warning(String domain, String key, XMLParseException ex)
        throws XNIException {
        printError("Warning", ex);
    } // warning(String,String,XMLParseException)

    /** Error. */
    public void error(String domain, String key, XMLParseException ex)
        throws XNIException {
        printError("Error", ex);
    } // error(String,String,XMLParseException)

    /** Fatal error. */
    public void fatalError(String domain, String key, XMLParseException ex)
        throws XNIException {
        printError("Fatal Error", ex);
        throw ex;
    } // fatalError(String,String,XMLParseException)

    //
    // Protected methods
    //

    /** Returns a sorted list of attributes. */
    /***
    protected XMLAttributes sortAttributes(XMLAttributes attrs) {

        XMLAttributesImpl attributes = new XMLAttributesImpl();

        int len = (attrs != null) ? attrs.getLength() : 0;
        for (int i = 0; i < len; i++) {
            String name = attrs.getQName(i);
            int count = attributes.getLength();
            int j = 0;
            while (j < count) {
                if (name.compareTo(attributes.getQName(j)) < 0) {
                    break;
                }
                j++;
            }
            attributes.insertAttributeAt(j, name, attrs.getType(i),
                                         attrs.getValue(i));
        }

        return attributes;

    } // sortAttributes(XMLAttributeList):XMLAttributeList
    /***/

    /** Normalizes and prints the given string. */
    protected void normalizeAndPrint(String s) {

        int len = (s != null) ? s.length() : 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            normalizeAndPrint(c);
        }

    } // normalizeAndPrint(String)

    /** Normalizes and prints the given array of characters. */
    protected void normalizeAndPrint(XMLString text) {
        for (int i = 0; i < text.length; i++) {
            normalizeAndPrint(text.ch[text.offset + i]);
        }
    } // normalizeAndPrint(XMLString)

    /** Normalizes and print the given character. */
    protected void normalizeAndPrint(char c) {

        switch (c) {
            case '<': {
                fOut.print("&lt;");
                break;
            }
            case '>': {
                fOut.print("&gt;");
                break;
            }
            case '&': {
                fOut.print("&amp;");
                break;
            }
            case '"': {
                fOut.print("&quot;");
                break;
            }
            case '\r':
            case '\n': {
                if (fCanonical) {
                    fOut.print("&#");
                    fOut.print(Integer.toString(c));
                    fOut.print(';');
                    break;
                }
                // else, default print char
            }
            default: {
                fOut.print(c);
            }
        }

    } // normalizeAndPrint(char)

    /** Prints the error message. */
    protected void printError(String type, XMLParseException ex) {

        System.err.print("[");
        System.err.print(type);
        System.err.print("] ");
        String systemId = ex.getExpandedSystemId();
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

    } // printError(String,XMLParseException)

    //
    // Main
    //

    /** Main program entry point. */
    public static void main(String argv[]) {

        // is there anything to do?
        if (argv.length == 0) {
            printUsage();
            System.exit(1);
        }

        // variables
        Writer writer = null;
        XMLParserConfiguration parserConfig = null;
        boolean namespaces = DEFAULT_NAMESPACES;
        boolean validation = DEFAULT_VALIDATION;
        boolean schemaValidation = DEFAULT_SCHEMA_VALIDATION;
        boolean schemaFullChecking = DEFAULT_SCHEMA_FULL_CHECKING;
        boolean honourAllSchemaLocations = DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS;
        boolean canonical = DEFAULT_CANONICAL;
        boolean incremental = DEFAULT_INCREMENTAL;

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
                        parserConfig = (XMLParserConfiguration)ObjectFactory.newInstance(parserName,
                            ObjectFactory.findClassLoader(), true);
                        /***
                        parserConfig.addRecognizedFeatures(new String[] {
                            NAMESPACE_PREFIXES_FEATURE_ID,
                        });
                        /***/
                        writer = null;
                    }
                    catch (Exception e) {
                        parserConfig = null;
                        System.err.println("error: Unable to instantiate parser configuration ("+parserName+")");
                        e.printStackTrace(System.err);
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
                if (option.equalsIgnoreCase("c")) {
                    canonical = option.equals("c");
                    continue;
                }
                if (option.equalsIgnoreCase("i")) {
                    incremental = option.equals("i");
                    continue;
                }
                if (option.equals("h")) {
                    printUsage();
                    continue;
                }
            }

            // use default parser?
            if (parserConfig == null) {

                // create parser
                try {
                    parserConfig = (XMLParserConfiguration)ObjectFactory.newInstance(DEFAULT_PARSER_CONFIG,
                        ObjectFactory.findClassLoader(), true);
                    /***
                    parserConfig.addRecognizedFeatures(new String[] {
                        NAMESPACE_PREFIXES_FEATURE_ID,
                    });
                    /***/
                }
                catch (Exception e) {
                    System.err.println("error: Unable to instantiate parser configuration ("+DEFAULT_PARSER_CONFIG+")");
                    e.printStackTrace(System.err);
                    continue;
                }
            }

            // set parser features
            if (writer == null) {
                writer = new Writer(parserConfig);
            }
            try {
                parserConfig.setFeature(NAMESPACES_FEATURE_ID, namespaces);
            }
            catch (XMLConfigurationException e) {
                System.err.println("warning: Parser does not support feature ("+NAMESPACES_FEATURE_ID+")");
            }
            try {
                parserConfig.setFeature(VALIDATION_FEATURE_ID, validation);
            }
            catch (XMLConfigurationException e) {
                System.err.println("warning: Parser does not support feature ("+VALIDATION_FEATURE_ID+")");
            }
            try {
                parserConfig.setFeature(SCHEMA_VALIDATION_FEATURE_ID, schemaValidation);
            }
            catch (XMLConfigurationException e) {
                if (e.getType() == XMLConfigurationException.NOT_SUPPORTED) {
                    System.err.println("warning: Parser does not support feature ("+SCHEMA_VALIDATION_FEATURE_ID+")");
                }
            }
            try {
                parserConfig.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, schemaFullChecking);
            }
            catch (XMLConfigurationException e) {
                if (e.getType() == XMLConfigurationException.NOT_SUPPORTED) {
                    System.err.println("warning: Parser does not support feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
                }
            }
            try {
                parserConfig.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS_ID, honourAllSchemaLocations);
            }
            catch (XMLConfigurationException e) {
                if (e.getType() == XMLConfigurationException.NOT_SUPPORTED) {
                    System.err.println("warning: Parser does not support feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
                }
            }

            // parse file
            try {
                writer.setOutput(System.out, "UTF8");
            }
            catch (UnsupportedEncodingException e) {
                System.err.println("error: Unable to set output. Exiting.");
                System.exit(1);
            }
            writer.setCanonical(canonical);
            try {
                if (incremental && parserConfig instanceof XMLPullParserConfiguration) {
                    XMLPullParserConfiguration pullParserConfig = (XMLPullParserConfiguration)parserConfig;
                    pullParserConfig.setInputSource(new XMLInputSource(null, arg, null));
                    int step = 1;
                    do {
                        //System.err.println("# step "+step++);
                    } while (pullParserConfig.parse(false));
                }
                else {
                    writer.parse(new XMLInputSource(null, arg, null));
                }
            }
            catch (XMLParseException e) {
                // ignore
            }
            catch (Exception e) {
                System.err.println("error: Parse error occurred - "+e.getMessage());
                if (e instanceof XNIException) {
                    e = ((XNIException)e).getException();
                }
                e.printStackTrace(System.err);
            }
        }

    } // main(String[])

    //
    // Private static methods
    //

    /** Prints the usage. */
    private static void printUsage() {

        System.err.println("usage: java sax.Writer (options) uri ...");
        System.err.println();

        System.err.println("options:");
        System.err.println("  -p name     Select parser configuration by name.");
        System.err.println("  -n | -N     Turn on/off namespace processing.");
        System.err.println("  -v | -V     Turn on/off validation.");
        System.err.println("  -s | -S     Turn on/off Schema validation support.");
        System.err.println("              NOTE: Not supported by all parsers.");
        System.err.println("  -f  | -F    Turn on/off Schema full checking.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        System.err.println("  -hs | -HS   Turn on/off honouring of all schema locations.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        /***
        System.err.println("  -c | -C     Turn on/off Canonical XML output.");
        System.err.println("              NOTE: This is not W3C canonical output.");
        /***/
        System.err.println("  -i | -I     Incremental mode.");
        System.err.println("              NOTE: This feature only works if the configuration used");
        System.err.println("                    implements XMLPullParserConfiguration.");
        System.err.println("  -h          This help screen.");
        System.err.println();

        System.err.println("defaults:");
        System.err.println("  Config:     "+DEFAULT_PARSER_CONFIG);
        System.err.print("  Namespaces: ");
        System.err.println(DEFAULT_NAMESPACES ? "on" : "off");
        System.err.print("  Validation: ");
        System.err.println(DEFAULT_VALIDATION ? "on" : "off");
        System.err.print("  Schema:     ");
        System.err.println(DEFAULT_SCHEMA_VALIDATION ? "on" : "off");
        System.err.print("  Schema full checking:     ");
        System.err.println(DEFAULT_SCHEMA_FULL_CHECKING ? "on" : "off");
        System.err.print("  Honour all schema locations:     ");
        System.err.println(DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS ? "on" : "off");
        /***
        System.err.print("  Canonical:  ");
        System.err.println(DEFAULT_CANONICAL ? "on" : "off");
        /***/
        System.err.print("  Incremental:  ");
        System.err.println(DEFAULT_INCREMENTAL ? "on" : "off");

    } // printUsage()

} // class Writer
