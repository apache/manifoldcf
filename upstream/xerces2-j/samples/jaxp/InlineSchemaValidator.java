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

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;

/**
 * <p>A sample demonstrating how to use the JAXP 1.3 Validation API
 * to create a validator and use the validator to validate input
 * from a DOM which contains inline schemas and multiple validation
 * roots. The output of this program shows the time spent executing 
 * the Validator.validate(Source) method.</p>
 * 
 * <p>This class is useful as a "poor-man's" performance tester to
 * compare the speed of various JAXP 1.3 validators with different
 * input sources. However, it is important to note that the first 
 * validation time of a validator will include both VM class load time 
 * and validator initialization that would not be present in subsequent
 * validations with the same document.</p>
 * 
 * <p><strong>Note:</strong> The results produced by this program
 * should never be accepted as true performance measurements.</p>
 *
 * @author Michael Glavassevich, IBM
 *
 * @version $Id$
 */
public class InlineSchemaValidator 
    implements ErrorHandler, NamespaceContext {
    
    //
    // Constants
    //

    // feature ids
    
    /** Schema full checking feature id (http://apache.org/xml/features/validation/schema-full-checking). */
    protected static final String SCHEMA_FULL_CHECKING_FEATURE_ID = "http://apache.org/xml/features/validation/schema-full-checking";
    
    /** Honour all schema locations feature id (http://apache.org/xml/features/honour-all-schemaLocations). */
    protected static final String HONOUR_ALL_SCHEMA_LOCATIONS_ID = "http://apache.org/xml/features/honour-all-schemaLocations";
    
    /** Validate schema annotations feature id (http://apache.org/xml/features/validate-annotations) */
    protected static final String VALIDATE_ANNOTATIONS_ID = "http://apache.org/xml/features/validate-annotations";
    
    /** Generate synthetic schema annotations feature id (http://apache.org/xml/features/generate-synthetic-annotations). */
    protected static final String GENERATE_SYNTHETIC_ANNOTATIONS_ID = "http://apache.org/xml/features/generate-synthetic-annotations";
    
    // default settings
    
    /** Default schema language (http://www.w3.org/2001/XMLSchema). */
    protected static final String DEFAULT_SCHEMA_LANGUAGE = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    
    /** Default repetition (1). */
    protected static final int DEFAULT_REPETITION = 1;
    
    /** Default schema full checking support (false). */
    protected static final boolean DEFAULT_SCHEMA_FULL_CHECKING = false;
    
    /** Default honour all schema locations (false). */
    protected static final boolean DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS = false;
    
    /** Default validate schema annotations (false). */
    protected static final boolean DEFAULT_VALIDATE_ANNOTATIONS = false;
    
    /** Default generate synthetic schema annotations (false). */
    protected static final boolean DEFAULT_GENERATE_SYNTHETIC_ANNOTATIONS = false;
    
    /** Default memory usage report (false). */
    protected static final boolean DEFAULT_MEMORY_USAGE = false;
    
    //
    // Data
    //
    
    /** Print writer. */
    protected PrintWriter fOut = new PrintWriter(System.out);
    
    /** Prefix to URI mappings for the NamespaceContext. */
    protected HashMap fPrefixToURIMappings;
    
    /** URI to prefix mappings for the NamespaceContext. */
    protected HashMap fURIToPrefixMappings;

    //
    // Constructors
    //

    public InlineSchemaValidator(HashMap prefixToURIMappings, HashMap uriToPrefixMappings) {
        fPrefixToURIMappings = prefixToURIMappings;
        fURIToPrefixMappings = uriToPrefixMappings;
    } // <init>(HashMap,HashMap)
    
    //
    // Public methods
    //
    
    public void validate(Validator validator, 
            Source source, String systemId,
            int repetitions, boolean memoryUsage) {
        try {
            long timeBefore = System.currentTimeMillis();
            long memoryBefore = Runtime.getRuntime().freeMemory();
            for (int j = 0; j < repetitions; ++j) {
                validator.validate(source);
            }
            long memoryAfter = Runtime.getRuntime().freeMemory();
            long timeAfter = System.currentTimeMillis();

            long time = timeAfter - timeBefore;
            long memory = memoryUsage
                        ? memoryBefore - memoryAfter : Long.MIN_VALUE;
            printResults(fOut, systemId, time, memory, repetitions);
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
    } // validate(Validator,Source,String,int,boolean)
    
    /** Prints the results. */
    public void printResults(PrintWriter out, String uri, long time,
                             long memory, int repetition) {
        
        // filename.xml: 631 ms
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
            out.print(((float)time)/repetition);
        }
        out.print(" ms");
        if (memory != Long.MIN_VALUE) {
            out.print(", ");
            out.print(memory);
            out.print(" bytes");
        }
        out.println();
        out.flush();

    } // printResults(PrintWriter,String,long,long,int)
    
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
    // NamespaceContext methods
    //
    
    public String getNamespaceURI(String prefix) {
        if (prefix == null) {
            throw new IllegalArgumentException("Prefix cannot be null.");
        }
        else if (XMLConstants.XML_NS_PREFIX.equals(prefix)) {
            return XMLConstants.XML_NS_URI;
        }
        else if (XMLConstants.XMLNS_ATTRIBUTE.equals(prefix)) {
            return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
        }
        else if (fPrefixToURIMappings != null) {
            String uri = (String) fPrefixToURIMappings.get(prefix);
            if (uri != null) {
                return uri;
            }
        }
        return XMLConstants.NULL_NS_URI;
    } // getNamespaceURI(String)

    public String getPrefix(String namespaceURI) {
        if (namespaceURI == null) {
            throw new IllegalArgumentException("Namespace URI cannot be null.");
        }
        else if (XMLConstants.XML_NS_URI.equals(namespaceURI)) {
            return XMLConstants.XML_NS_PREFIX;
        }
        else if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespaceURI)) {
            return XMLConstants.XMLNS_ATTRIBUTE;
        }
        else if (fURIToPrefixMappings != null) {
            HashSet prefixes = (HashSet) fURIToPrefixMappings.get(namespaceURI);
            if (prefixes != null && prefixes.size() > 0) {
                return (String) prefixes.iterator().next();
            }
        }
        return null;
    } // getPrefix(String)

    public Iterator getPrefixes(String namespaceURI) {
        if (namespaceURI == null) {
            throw new IllegalArgumentException("Namespace URI cannot be null.");
        }
        else if (XMLConstants.XML_NS_URI.equals(namespaceURI)) {
            return new Iterator() {
                boolean more = true;
                public boolean hasNext() {
                    return more;
                }
                public Object next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    more = false;
                    return XMLConstants.XML_NS_PREFIX;
                }
                public void remove() {
                    throw new UnsupportedOperationException();                   
                }  
            };
        }
        else if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespaceURI)) {
            return new Iterator() {
                boolean more = true;
                public boolean hasNext() {
                    return more;
                }
                public Object next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    more = false;
                    return XMLConstants.XMLNS_ATTRIBUTE;
                }
                public void remove() {
                    throw new UnsupportedOperationException();                   
                }  
            };
        }
        else if (fURIToPrefixMappings != null) {
            HashSet prefixes = (HashSet) fURIToPrefixMappings.get(namespaceURI);
            if (prefixes != null && prefixes.size() > 0) {
                return prefixes.iterator();
            }
        }
        return Collections.EMPTY_LIST.iterator();
    } // getPrefixes(String)
    
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
    
    //
    // MAIN
    //

    /** Main program entry point. */
    public static void main (String [] argv) {
        
        // is there anything to do?
        if (argv.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        // variables
        Vector schemas = null;
        Vector instances = null;
        HashMap prefixMappings = null;
        HashMap uriMappings = null;
        String docURI = argv[argv.length - 1];
        String schemaLanguage = DEFAULT_SCHEMA_LANGUAGE;
        int repetition = DEFAULT_REPETITION;
        boolean schemaFullChecking = DEFAULT_SCHEMA_FULL_CHECKING;
        boolean honourAllSchemaLocations = DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS;
        boolean validateAnnotations = DEFAULT_VALIDATE_ANNOTATIONS;
        boolean generateSyntheticAnnotations = DEFAULT_GENERATE_SYNTHETIC_ANNOTATIONS;
        boolean memoryUsage = DEFAULT_MEMORY_USAGE;
        
        // process arguments
        for (int i = 0; i < argv.length - 1; ++i) {
            String arg = argv[i];
            if (arg.startsWith("-")) {
                String option = arg.substring(1);
                if (option.equals("l")) {
                    // get schema language name
                    if (++i == argv.length) {
                        System.err.println("error: Missing argument to -l option.");
                    }
                    else {
                        schemaLanguage = argv[i];
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
                if (arg.equals("-a")) {
                    // process -a: xpath expressions for schemas
                    if (schemas == null) {
                        schemas = new Vector();
                    }
                    while (i + 1 < argv.length - 1 && !(arg = argv[i + 1]).startsWith("-")) {
                        schemas.add(arg);
                        ++i;
                    }
                    continue;
                }
                if (arg.equals("-i")) {
                    // process -i: xpath expressions for instance documents
                    if (instances == null) {
                        instances = new Vector();
                    }
                    while (i + 1 < argv.length - 1 && !(arg = argv[i + 1]).startsWith("-")) {
                        instances.add(arg);
                        ++i;
                    }
                    continue;
                }
                if (arg.equals("-nm")) {
                    String prefix;
                    String uri;
                    while (i + 2 < argv.length - 1 && !(prefix = argv[i + 1]).startsWith("-") && 
                            !(uri = argv[i + 2]).startsWith("-")) {
                        if (prefixMappings == null) {
                            prefixMappings = new HashMap();
                            uriMappings = new HashMap();
                        }
                        prefixMappings.put(prefix, uri);
                        HashSet prefixes = (HashSet) uriMappings.get(uri);
                        if (prefixes == null) {
                            prefixes = new HashSet();
                            uriMappings.put(uri, prefixes);
                        }
                        prefixes.add(prefix);
                        i += 2;
                    }
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
                if (option.equalsIgnoreCase("ga")) {
                    generateSyntheticAnnotations = option.equals("ga");
                    continue;
                }
                if (option.equalsIgnoreCase("m")) {
                    memoryUsage = option.equals("m");
                    continue;
                }
                if (option.equals("h")) {
                    printUsage();
                    continue;
                }
                System.err.println("error: unknown option ("+option+").");
                continue;
            }
        }
        
        try {
            // Create new instance of inline schema validator.
            InlineSchemaValidator inlineSchemaValidator = new InlineSchemaValidator(prefixMappings, uriMappings);
            
            // Parse document containing schemas and validation roots
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(inlineSchemaValidator);
            Document doc = db.parse(docURI);
            
            // Create XPath factory for selecting schema and validation roots
            XPathFactory xpf = XPathFactory.newInstance();
            XPath xpath = xpf.newXPath();
            xpath.setNamespaceContext(inlineSchemaValidator);
            
            // Select schema roots from the DOM
            NodeList [] schemaNodes = new NodeList[schemas != null ? schemas.size() : 0];
            for (int i = 0; i < schemaNodes.length; ++i) {
                XPathExpression xpathSchema = xpath.compile((String)schemas.elementAt(i));
                schemaNodes[i] = (NodeList) xpathSchema.evaluate(doc, XPathConstants.NODESET);
            }
            
            // Select validation roots from the DOM
            NodeList [] instanceNodes = new NodeList[instances != null ? instances.size() : 0];
            for (int i = 0; i < instanceNodes.length; ++i) {
                XPathExpression xpathInstance = xpath.compile((String)instances.elementAt(i));
                instanceNodes[i] = (NodeList) xpathInstance.evaluate(doc, XPathConstants.NODESET);
            }

            // Create SchemaFactory and configure
            SchemaFactory factory = SchemaFactory.newInstance(schemaLanguage);
            factory.setErrorHandler(inlineSchemaValidator);
            
            try {
                factory.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, schemaFullChecking);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: SchemaFactory does not recognize feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: SchemaFactory does not support feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
            }
            try {
                factory.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS_ID, honourAllSchemaLocations);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: SchemaFactory does not recognize feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: SchemaFactory does not support feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
            }
            try {
                factory.setFeature(VALIDATE_ANNOTATIONS_ID, validateAnnotations);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: SchemaFactory does not recognize feature ("+VALIDATE_ANNOTATIONS_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: SchemaFactory does not support feature ("+VALIDATE_ANNOTATIONS_ID+")");
            }
            try {
                factory.setFeature(GENERATE_SYNTHETIC_ANNOTATIONS_ID, generateSyntheticAnnotations);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: SchemaFactory does not recognize feature ("+GENERATE_SYNTHETIC_ANNOTATIONS_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: SchemaFactory does not support feature ("+GENERATE_SYNTHETIC_ANNOTATIONS_ID+")");
            }
            
            // Build Schema from sources
            Schema schema;
            {
                DOMSource [] sources;
                int size = 0;
                for (int i = 0; i < schemaNodes.length; ++i) {
                    size += schemaNodes[i].getLength();
                }
                sources = new DOMSource[size];
                if (size == 0) {
                    schema = factory.newSchema();
                }
                else {
                    int count = 0;
                    for (int i = 0; i < schemaNodes.length; ++i) {
                        NodeList nodeList = schemaNodes[i];
                        int nodeListLength = nodeList.getLength();
                        for (int j = 0; j < nodeListLength; ++j) {
                            sources[count++] = new DOMSource(nodeList.item(j));
                        }
                    }
                    schema = factory.newSchema(sources);
                }
            }
            
            // Setup validator and input source.
            Validator validator = schema.newValidator();
            validator.setErrorHandler(inlineSchemaValidator);
            
            try {
                validator.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, schemaFullChecking);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Validator does not recognize feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Validator does not support feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
            }
            try {
                validator.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS_ID, honourAllSchemaLocations);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Validator does not recognize feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Validator does not support feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
            }
            try {
                validator.setFeature(VALIDATE_ANNOTATIONS_ID, validateAnnotations);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Validator does not recognize feature ("+VALIDATE_ANNOTATIONS_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Validator does not support feature ("+VALIDATE_ANNOTATIONS_ID+")");
            }
            try {
                validator.setFeature(GENERATE_SYNTHETIC_ANNOTATIONS_ID, generateSyntheticAnnotations);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Validator does not recognize feature ("+GENERATE_SYNTHETIC_ANNOTATIONS_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Validator does not support feature ("+GENERATE_SYNTHETIC_ANNOTATIONS_ID+")");
            }

            // Validate instance documents
            for (int i = 0; i < instanceNodes.length; ++i) {
                NodeList nodeList = instanceNodes[i];
                int nodeListLength = nodeList.getLength();
                for (int j = 0; j < nodeListLength; ++j) {
                    DOMSource source = new DOMSource(nodeList.item(j));
                    source.setSystemId(docURI);
                    inlineSchemaValidator.validate(validator, source, docURI, repetition, memoryUsage);
                }
            }
        }
        catch (SAXParseException e) {
            // ignore
        }
        catch (Exception e) {
            System.err.println("error: Parse error occurred - "+e.getMessage());
            if (e instanceof SAXException) {
                Exception nested = ((SAXException)e).getException();
                if (nested != null) {
                    e = nested;
                } 
            }
            e.printStackTrace(System.err);
        }
    } // main(String[])
    
    //
    // Private static methods
    //
    
    /** Prints the usage. */
    private static void printUsage() {
        
        System.err.println("usage: java jaxp.InlineSchemaValidator (options) uri ...");
        System.err.println();
        
        System.err.println("options:");
        System.err.println("  -l name          Select schema language by name.");
        System.err.println("  -x number        Select number of repetitions.");
        System.err.println("  -a xpath    ...  Provide a list of XPath expressions for schema roots");
        System.err.println("  -i xpath    ...  Provide a list of XPath expressions for validation roots");
        System.err.println("  -nm pre uri ...  Provide a list of prefix to namespace URI mappings for the XPath expressions.");
        System.err.println("  -f  | -F         Turn on/off Schema full checking.");
        System.err.println("                   NOTE: Not supported by all schema factories and validators.");
        System.err.println("  -hs | -HS        Turn on/off honouring of all schema locations.");
        System.err.println("                   NOTE: Not supported by all schema factories and validators.");
        System.err.println("  -va | -VA        Turn on/off validation of schema annotations.");
        System.err.println("                   NOTE: Not supported by all schema factories and validators.");
        System.err.println("  -ga | -GA        Turn on/off generation of synthetic schema annotations.");
        System.err.println("                   NOTE: Not supported by all schema factories and validators.");
        System.err.println("  -m  | -M         Turn on/off memory usage report");
        System.err.println("  -h               This help screen.");
        
        System.err.println();
        System.err.println("defaults:");
        System.err.println("  Schema language:                 " + DEFAULT_SCHEMA_LANGUAGE);
        System.err.println("  Repetition:                      " + DEFAULT_REPETITION);
        System.err.print("  Schema full checking:            ");
        System.err.println(DEFAULT_SCHEMA_FULL_CHECKING ? "on" : "off");
        System.err.print("  Honour all schema locations:     ");
        System.err.println(DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS ? "on" : "off");
        System.err.print("  Validate annotations:            ");
        System.err.println(DEFAULT_VALIDATE_ANNOTATIONS ? "on" : "off");
        System.err.print("  Generate synthetic annotations:  ");
        System.err.println(DEFAULT_GENERATE_SYNTHETIC_ANNOTATIONS ? "on" : "off");
        System.err.print("  Memory:                          ");
        System.err.println(DEFAULT_MEMORY_USAGE ? "on" : "off");
        
        System.err.println();
        System.err.println("notes:");
        System.err.println("  The speed and memory results from this program should NOT be used as the");
        System.err.println("  basis of parser performance comparison! Real analytical methods should be");
        System.err.println("  used. For better results, perform multiple document validations within the");
        System.err.println("  same virtual machine to remove class loading from parse time and memory usage.");
        
    } // printUsage()

} // class InlineSchemaValidator
