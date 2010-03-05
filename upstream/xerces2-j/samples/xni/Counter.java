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

import java.io.PrintWriter;

import org.apache.xerces.parsers.XMLDocumentParser;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.apache.xerces.xni.parser.XMLParserConfiguration;

/**
 * A sample XNI counter. The output of this program shows the time
 * and count of elements, attributes, ignorable whitespaces, and
 * characters appearing in the document.
 * <p>
 * This class is useful as a "poor-man's" performance tester to
 * compare the speed and accuracy of various parser configurations.
 * However, it is important to note that the first parse time of a
 * parser will include both VM class load time and parser
 * initialization that would not be present in subsequent parses
 * with the same file.
 * <p>
 * <strong>Note:</strong> The results produced by this program
 * should never be accepted as true performance measurements.
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class Counter
    extends XMLDocumentParser
    implements XMLErrorHandler {

    //
    // Constants
    //

    // feature ids

    /** Namespaces feature id (http://xml.org/sax/features/namespaces). */
    protected static final String NAMESPACES_FEATURE_ID =
        "http://xml.org/sax/features/namespaces";

    /** Namespace prefixes feature id (http://xml.org/sax/features/namespace-prefixes). */
    protected static final String NAMESPACE_PREFIXES_FEATURE_ID =
        "http://xml.org/sax/features/namespace-prefixes";

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

    /** Default repetition (1). */
    protected static final int DEFAULT_REPETITION = 1;

    /** Default namespaces support (true). */
    protected static final boolean DEFAULT_NAMESPACES = true;

    /** Default namespace prefixes (false). */
    protected static final boolean DEFAULT_NAMESPACE_PREFIXES = false;

    /** Default validation support (false). */
    protected static final boolean DEFAULT_VALIDATION = false;

    /** Default Schema validation support (false). */
    protected static final boolean DEFAULT_SCHEMA_VALIDATION = false;

    /** Default Schema full checking support (false). */
    protected static final boolean DEFAULT_SCHEMA_FULL_CHECKING = false;
    
    /** Default honour all schema locations (false). */
    protected static final boolean DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS = false;

    /** Default memory usage report (false). */
    protected static final boolean DEFAULT_MEMORY_USAGE = false;

    /** Default "tagginess" report (false). */
    protected static final boolean DEFAULT_TAGGINESS = false;

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

    /** Number of characters of tags. */
    protected long fTagCharacters;

    /** Number of other content characters for the "tagginess" calculation. */
    protected long fOtherCharacters;

    //
    // Constructors
    //

    /** Default constructor. */
    public Counter(XMLParserConfiguration configuration) {
        super(configuration);
        fConfiguration.setErrorHandler(this);
    } // <init>(XMLParserConfiguration)

    //
    // Public methods
    //

    /** Prints the results. */
    public void printResults(PrintWriter out, String uri, long time,
                             long memory, boolean tagginess,
                             int repetition) {

        // filename.xml: 631 ms (4 elems, 0 attrs, 78 spaces, 0 chars)
        out.print(uri);
        out.print(": ");
        if (repetition == 1) {
            out.print(time);
        }
        else {
            out.print(time);
            out.print('/');
            out.print(repetition);
            out.print('=');
            out.print(time/repetition);
        }
        out.print(" ms");
        if (memory != Long.MIN_VALUE) {
            out.print(", ");
            out.print(memory);
            out.print(" bytes");
        }
        out.print(" (");
        out.print(fElements);
        out.print(" elems, ");
        out.print(fAttributes);
        out.print(" attrs, ");
        out.print(fIgnorableWhitespace);
        out.print(" spaces, ");
        out.print(fCharacters);
        out.print(" chars)");
        if (tagginess) {
            out.print(' ');
            long totalCharacters = fTagCharacters + fOtherCharacters
                                 + fCharacters + fIgnorableWhitespace;
            long tagValue = fTagCharacters * 100 / totalCharacters;
            out.print(tagValue);
            out.print("% tagginess");
        }
        out.println();
        out.flush();

    } // printResults(PrintWriter,String,long)

    //
    // ContentHandler methods
    //

    /** Start document. */
    public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext, Augmentations augs)
        throws XNIException {

        fElements            = 0;
        fAttributes          = 0;
        fCharacters          = 0;
        fIgnorableWhitespace = 0;
        fTagCharacters       = 0;
        fOtherCharacters     = 0;

    } // startDocument(XMLLocator,String, NamespaceContext, Augmentations)

    /** Start element. */
    public void startElement(QName element, XMLAttributes attrs, Augmentations augs)
        throws XNIException {

        fElements++;
        fTagCharacters++; // open angle bracket
        fTagCharacters += element.rawname.length();
        if (attrs != null) {
            int attrCount = attrs.getLength();
            fAttributes += attrCount;
            for (int i = 0; i < attrCount; i++) {
                fTagCharacters++; // space
                fTagCharacters += attrs.getQName(i).length();
                fTagCharacters++; // '='
                fTagCharacters++; // open quote
                fOtherCharacters += attrs.getValue(i).length();
                fTagCharacters++; // close quote
            }
        }
        fTagCharacters++; // close angle bracket

    } // startElement(QName,XMLAttributes)

    /** Empty element. */
    public void emptyElement(QName element, XMLAttributes attrs, Augmentations augs)
        throws XNIException {

        fElements++;
        fTagCharacters++; // open angle bracket
        fTagCharacters += element.rawname.length();
        if (attrs != null) {
            int attrCount = attrs.getLength();
            fAttributes += attrCount;
            for (int i = 0; i < attrCount; i++) {
                fTagCharacters++; // space
                fTagCharacters += attrs.getQName(i).length();
                fTagCharacters++; // '='
                fTagCharacters++; // open quote
                fOtherCharacters += attrs.getValue(i).length();
                fTagCharacters++; // close quote
            }
        }
        fTagCharacters++; // forward slash
        fTagCharacters++; // close angle bracket

    } // startElement(QName,XMLAttributes)

    /** Characters. */
    public void characters(XMLString text, Augmentations augs) throws XNIException {

        fCharacters += text.length;

    } // characters(XMLString);

    /** Ignorable whitespace. */
    public void ignorableWhitespace(XMLString text, Augmentations augs) throws XNIException {

        fIgnorableWhitespace += text.length;

    } // ignorableWhitespace(XMLString);

    /** Processing instruction. */
    public void processingInstruction(String target, XMLString data, Augmentations augs)
        throws XNIException {
        fTagCharacters += 2; // "<?"
        fTagCharacters += target.length();
        if (data.length > 0) {
            fTagCharacters++; // space
            fOtherCharacters += data.length;
        }
        fTagCharacters += 2; // "?>"
    } // processingInstruction(String,XMLString)

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
        PrintWriter out = new PrintWriter(System.out);
        XMLDocumentParser parser = null;
        XMLParserConfiguration parserConfig = null;
        int repetition = DEFAULT_REPETITION;
        boolean namespaces = DEFAULT_NAMESPACES;
        boolean namespacePrefixes = DEFAULT_NAMESPACE_PREFIXES;
        boolean validation = DEFAULT_VALIDATION;
        boolean schemaValidation = DEFAULT_SCHEMA_VALIDATION;
        boolean schemaFullChecking = DEFAULT_SCHEMA_FULL_CHECKING;
        boolean honourAllSchemaLocations = DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS;
        boolean memoryUsage = DEFAULT_MEMORY_USAGE;
        boolean tagginess = DEFAULT_TAGGINESS;

        // process arguments
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg.startsWith("-")) {
                String option = arg.substring(1);
                if (option.equals("p")) {
                    // get parser name
                    if (++i == argv.length) {
                        System.err.println("error: Missing argument to -p option.");
                        continue;
                    }
                    String parserName = argv[i];

                    // create parser
                    try {
                        parserConfig = (XMLParserConfiguration)ObjectFactory.newInstance(parserName,
                            ObjectFactory.findClassLoader(), true);
                        parserConfig.addRecognizedFeatures(new String[] {
                            NAMESPACE_PREFIXES_FEATURE_ID,
                        });
                        parser = null;
                    }
                    catch (Exception e) {
                        parserConfig = null;
                        System.err.println("error: Unable to instantiate parser configuration ("+parserName+")");
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
                if (option.equalsIgnoreCase("np")) {
                    namespacePrefixes = option.equals("np");
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
                if (option.equalsIgnoreCase("m")) {
                    memoryUsage = option.equals("m");
                    continue;
                }
                if (option.equalsIgnoreCase("t")) {
                    tagginess = option.equals("t");
                    continue;
                }
                if (option.equals("-rem")) {
                    if (++i == argv.length) {
                        System.err.println("error: Missing argument to -# option.");
                        continue;
                    }
                    System.out.print("# ");
                    System.out.println(argv[i]);
                    continue;
                }
                if (option.equals("h")) {
                    printUsage();
                    continue;
                }
                System.err.println("error: unknown option ("+option+").");
                continue;
            }

            // use default parser?
            if (parserConfig == null) {

                // create parser
                try {
                    parserConfig = (XMLParserConfiguration)ObjectFactory.newInstance(DEFAULT_PARSER_CONFIG,
                        ObjectFactory.findClassLoader(), true);
                    parserConfig.addRecognizedFeatures(new String[] {
                        NAMESPACE_PREFIXES_FEATURE_ID,
                    });
                }
                catch (Exception e) {
                    System.err.println("error: Unable to instantiate parser configuration ("+DEFAULT_PARSER_CONFIG+")");
                    continue;
                }
            }

            // set parser features
            if (parser == null) {
                parser = new Counter(parserConfig);
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
                long timeBefore = System.currentTimeMillis();
                long memoryBefore = 0;
                if(memoryUsage) {
                    System.gc();
                    memoryBefore = Runtime.getRuntime().freeMemory();
                }
                for (int j = 0; j < repetition; j++) {
                    parser.parse(new XMLInputSource(null, arg, null));
                }
                long memory = Long.MIN_VALUE;
                if(memoryUsage) {
                    long memoryAfter = Runtime.getRuntime().freeMemory();
                    memory = memoryBefore - memoryAfter;
                }

                long timeAfter = System.currentTimeMillis();
                long time = timeAfter - timeBefore;
                ((Counter)parser).printResults(out, arg, time,
                                               memory, tagginess,
                                               repetition);
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

        System.err.println("usage: java xni.Counter (options) uri ...");
        System.err.println();

        System.err.println("options:");
        System.err.println("  -p name     Select parser configuration by name.");
        System.err.println("  -x number   Select number of repetitions.");
        System.err.println("  -n  | -N    Turn on/off namespace processing.");
        System.err.println("  -np | -NP   Turn on/off namespace prefixes.");
        System.err.println("              NOTE: Requires use of -n.");
        System.err.println("  -v  | -V    Turn on/off validation.");
        System.err.println("  -s  | -S    Turn on/off Schema validation support.");
        System.err.println("              NOTE: Not supported by all parser configurations.");
        System.err.println("  -f  | -F    Turn on/off Schema full checking.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        System.err.println("  -hs | -HS   Turn on/off honouring of all schema locations.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        System.err.println("  -m  | -M    Turn on/off memory usage report.");
        System.err.println("  -t  | -T    Turn on/off \"tagginess\" report.");
        System.err.println("  --rem text  Output user defined comment before next parse.");
        System.err.println("  -h          This help screen.");

        System.err.println();
        System.err.println("defaults:");
        System.err.println("  Config:     "+DEFAULT_PARSER_CONFIG);
        System.err.println("  Repetition: "+DEFAULT_REPETITION);
        System.err.print("  Namespaces: ");
        System.err.println(DEFAULT_NAMESPACES ? "on" : "off");
        System.err.print("  Prefixes:   ");
        System.err.println(DEFAULT_NAMESPACE_PREFIXES ? "on" : "off");
        System.err.print("  Validation: ");
        System.err.println(DEFAULT_VALIDATION ? "on" : "off");
        System.err.print("  Schema:     ");
        System.err.println(DEFAULT_SCHEMA_VALIDATION ? "on" : "off");
        System.err.print("  Schema full checking:     ");
        System.err.println(DEFAULT_SCHEMA_FULL_CHECKING ? "on" : "off");
        System.err.print("  Honour all schema locations:     ");
        System.err.println(DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS ? "on" : "off");
        System.err.print("  Memory:     ");
        System.err.println(DEFAULT_MEMORY_USAGE ? "on" : "off");
        System.err.print("  Tagginess:  ");
        System.err.println(DEFAULT_TAGGINESS ? "on" : "off");

        System.err.println();
        System.err.println("notes:");
        System.err.println("  The speed and memory results from this program should NOT be used as the");
        System.err.println("  basis of parser performance comparison! Real analytical methods should be");
        System.err.println("  used. For better results, perform multiple document parses within the same");
        System.err.println("  virtual machine to remove class loading from parse time and memory usage.");
        System.err.println();
        System.err.println("  The \"tagginess\" measurement gives a rough estimate of the percentage of");
        System.err.println("  markup versus content in the XML document. The percent tagginess of a ");
        System.err.println("  document is equal to the minimum amount of tag characters required for ");
        System.err.println("  elements, attributes, and processing instructions divided by the total");
        System.err.println("  amount of characters (characters, ignorable whitespace, and tag characters)");
        System.err.println("  in the document.");
        System.err.println();
        System.err.println("  Not all features are supported by different parser configurations.");

    } // printUsage()

} // class Counter
