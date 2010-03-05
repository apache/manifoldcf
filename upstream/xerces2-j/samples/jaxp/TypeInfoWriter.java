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

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Vector;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.TypeInfoProvider;
import javax.xml.validation.ValidatorHandler;

import org.w3c.dom.TypeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.DTDHandler;
import org.xml.sax.Locator;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.ParserAdapter;
import org.xml.sax.helpers.ParserFactory;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * <p>Provides a trace of the schema type information for elements and
 * attributes in an XML document. This demonstrates usage of the
 * JAXP 1.3 Validation API, particuarly how to read type information
 * from a TypeInfoProvider.</p>
 *
 * @author Michael Glavassevich, IBM
 *
 * @version $Id$
 */
public class TypeInfoWriter 
    extends DefaultHandler {

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
    
    /** Default parser name (org.apache.xerces.parsers.SAXParser). */
    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";
    
    /** Default schema full checking support (false). */
    protected static final boolean DEFAULT_SCHEMA_FULL_CHECKING = false;
    
    /** Default honour all schema locations (false). */
    protected static final boolean DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS = false;
    
    /** Default validate schema annotations (false). */
    protected static final boolean DEFAULT_VALIDATE_ANNOTATIONS = false;
    
    /** Default generate synthetic schema annotations (false). */
    protected static final boolean DEFAULT_GENERATE_SYNTHETIC_ANNOTATIONS = false;
    
    //
    // Data
    //

    /** TypeInfo provider. */
    protected TypeInfoProvider fTypeInfoProvider;
    
    /** Print writer. */
    protected PrintWriter fOut;
    
    /** Indent level. */
    protected int fIndent;
    
    //
    // Constructors
    //

    /** Default constructor. */
    public TypeInfoWriter() {}
    
    //
    // ContentHandler and DocumentHandler methods
    //

    /** Set document locator. */
    public void setDocumentLocator(Locator locator) {
        
        fIndent = 0;
        printIndent();
        fOut.print("setDocumentLocator(");
        fOut.print("systemId=");
        printQuotedString(locator.getSystemId());
        fOut.print(", publicId=");
        printQuotedString(locator.getPublicId());
        fOut.println(')');
        fOut.flush();

    } // setDocumentLocator(Locator)

    /** Start document. */
    public void startDocument() throws SAXException {

        fIndent = 0;
        printIndent();
        fOut.println("startDocument()");
        fOut.flush();
        fIndent++;

    } // startDocument()
    
    /** Start element. */
    public void startElement(String uri, String localName, String qname,
                             Attributes attributes) throws SAXException {

        TypeInfo type;
        printIndent();
        fOut.print("startElement(");
        fOut.print("name=");
        printQName(uri, localName);
        fOut.print(',');
        fOut.print("type=");
        if (fTypeInfoProvider != null && (type = fTypeInfoProvider.getElementTypeInfo()) != null) {
            printQName(type.getTypeNamespace(), type.getTypeName());
        }
        else {
            fOut.print("null");
        }
        fOut.print(',');
        fOut.print("attributes=");
        if (attributes == null) {
            fOut.println("null");
        }
        else {
            fOut.print('{');
            int length = attributes.getLength();
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    fOut.print(',');
                }
                String attrURI = attributes.getURI(i);
                String attrLocalName = attributes.getLocalName(i);
                fOut.print('{');
                fOut.print("name=");
                printQName(attrURI, attrLocalName);
                fOut.print(',');
                fOut.print("type=");
                if (fTypeInfoProvider != null && (type = fTypeInfoProvider.getAttributeTypeInfo(i)) != null) {
                    printQName(type.getTypeNamespace(), type.getTypeName());
                }
                else {
                    fOut.print("null");
                }
                fOut.print(',');
                fOut.print("id=");
                fOut.print(fTypeInfoProvider != null && fTypeInfoProvider.isIdAttribute(i) ? "\"true\"" : "\"false\"");
                fOut.print(',');
                fOut.print("specified=");
                fOut.print(fTypeInfoProvider == null || fTypeInfoProvider.isSpecified(i) ? "\"true\"" : "\"false\"");
                fOut.print('}');
            }
            fOut.print('}');
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startElement(String,String,String,Attributes)
    
    /** End element. */
    public void endElement(String uri, String localName, String qname)
        throws SAXException {

        fIndent--;
        printIndent();
        fOut.print("endElement(");
        fOut.print("name=");
        printQName(uri, localName);
        fOut.println(')');
        fOut.flush();

    } // endElement(String,String,String)
    
    /** End document. */
    public void endDocument() throws SAXException {
        fIndent--;
        printIndent();
        fOut.println("endDocument()");
        fOut.flush();
    } // endDocument()
    
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
    // Public methods
    //
    
    
    /** Sets the output stream for printing. */
    public void setOutput(OutputStream stream, String encoding)
        throws UnsupportedEncodingException {

        if (encoding == null) {
            encoding = "UTF8";
        }

        java.io.Writer writer = new OutputStreamWriter(stream, encoding);
        fOut = new PrintWriter(writer);

    } // setOutput(OutputStream,String)
    
    //
    // Protected methods
    //
    
    /** Sets the TypeInfoProvider used by this writer. */
    protected void setTypeInfoProvider(TypeInfoProvider provider) {
        fTypeInfoProvider = provider;
    }
    
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
    
    /** Prints the indent. */
    protected void printIndent() {
        for (int i = 0; i < fIndent; i++) {
            fOut.print(' ');
        }
    } // printIndent()
    
    protected void printQName(String uri, String localName) {
        if (uri != null && uri.length() > 0) {
            printQuotedString('{' + uri + "}" + localName);
            return;
        }
        printQuotedString(localName);
    }
    
    /** Print quoted string. */
    protected void printQuotedString(String s) {

        if (s == null) {
            fOut.print("null");
            return;
        }
        fOut.print('"');
        fOut.print(s);
        fOut.print('"');

    } // printQuotedString(String)
    
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
        XMLReader parser = null;
        Vector schemas = null;
        Vector instances = null;
        String schemaLanguage = DEFAULT_SCHEMA_LANGUAGE;
        boolean schemaFullChecking = DEFAULT_SCHEMA_FULL_CHECKING;
        boolean honourAllSchemaLocations = DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS;
        boolean validateAnnotations = DEFAULT_VALIDATE_ANNOTATIONS;
        boolean generateSyntheticAnnotations = DEFAULT_GENERATE_SYNTHETIC_ANNOTATIONS;
        
        // process arguments
        for (int i = 0; i < argv.length; ++i) {
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
                if (option.equals("p")) {
                    // get parser name
                    if (++i == argv.length) {
                        System.err.println("error: Missing argument to -p option.");
                        continue;
                    }
                    String parserName = argv[i];
                    
                    // create parser
                    try {
                        parser = XMLReaderFactory.createXMLReader(parserName);
                    }
                    catch (Exception e) {
                        try {
                            Parser sax1Parser = ParserFactory.makeParser(parserName);
                            parser = new ParserAdapter(sax1Parser);
                            System.err.println("warning: Features and properties not supported on SAX1 parsers.");
                        }
                        catch (Exception ex) {
                            parser = null;
                            System.err.println("error: Unable to instantiate parser ("+parserName+")");
                            e.printStackTrace(System.err);
                            System.exit(1);
                        }
                    }
                    continue;
                }
                if (arg.equals("-a")) {
                    // process -a: schema documents
                    if (schemas == null) {
                        schemas = new Vector();
                    }
                    while (i + 1 < argv.length && !(arg = argv[i + 1]).startsWith("-")) {
                        schemas.add(arg);
                        ++i;
                    }
                    continue;
                }
                if (arg.equals("-i")) {
                    // process -i: instance documents
                    if (instances == null) {
                        instances = new Vector();
                    }
                    while (i + 1 < argv.length && !(arg = argv[i + 1]).startsWith("-")) {
                        instances.add(arg);
                        ++i;
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
                if (option.equals("h")) {
                    printUsage();
                    continue;
                }
                System.err.println("error: unknown option ("+option+").");
                continue;
            }
        }
        
        // use default parser?
        if (parser == null) {
            // create parser
            try {
                parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
            }
            catch (Exception e) {
                System.err.println("error: Unable to instantiate parser ("+DEFAULT_PARSER_NAME+")");
                e.printStackTrace(System.err);
                System.exit(1);
            }
        }
        
        try {
            // Create writer
            TypeInfoWriter writer = new TypeInfoWriter();
            writer.setOutput(System.out, "UTF8");
            
            // Create SchemaFactory and configure
            SchemaFactory factory = SchemaFactory.newInstance(schemaLanguage);
            factory.setErrorHandler(writer);
            
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
            if (schemas != null && schemas.size() > 0) {
                final int length = schemas.size();
                StreamSource[] sources = new StreamSource[length];
                for (int j = 0; j < length; ++j) {
                    sources[j] = new StreamSource((String) schemas.elementAt(j));
                }
                schema = factory.newSchema(sources);
            }
            else {
                schema = factory.newSchema();
            }
            
            // Setup validator and parser
            ValidatorHandler validator = schema.newValidatorHandler();
            parser.setContentHandler(validator);
            if (validator instanceof DTDHandler) {
                parser.setDTDHandler((DTDHandler) validator);
            }
            parser.setErrorHandler(writer);
            validator.setContentHandler(writer);
            validator.setErrorHandler(writer);
            writer.setTypeInfoProvider(validator.getTypeInfoProvider());
            
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
            
            // Validate instance documents and print type information
            if (instances != null && instances.size() > 0) {
                final int length = instances.size();
                for (int j = 0; j < length; ++j) {
                    parser.parse((String) instances.elementAt(j));
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
        
        System.err.println("usage: java jaxp.TypeInfoWriter (options) ...");
        System.err.println();
        
        System.err.println("options:");
        System.err.println("  -l name     Select schema language by name.");
        System.err.println("  -p name     Select parser by name.");
        System.err.println("  -a uri ...  Provide a list of schema documents");
        System.err.println("  -i uri ...  Provide a list of instance documents to validate");
        System.err.println("  -f  | -F    Turn on/off Schema full checking.");
        System.err.println("              NOTE: Not supported by all schema factories and validators.");
        System.err.println("  -hs | -HS   Turn on/off honouring of all schema locations.");
        System.err.println("              NOTE: Not supported by all schema factories and validators.");
        System.err.println("  -va | -VA   Turn on/off validation of schema annotations.");
        System.err.println("              NOTE: Not supported by all schema factories and validators.");
        System.err.println("  -ga | -GA   Turn on/off generation of synthetic schema annotations.");
        System.err.println("              NOTE: Not supported by all schema factories and validators.");
        System.err.println("  -h          This help screen.");
        
        System.err.println();
        System.err.println("defaults:");
        System.err.println("  Schema language:                 " + DEFAULT_SCHEMA_LANGUAGE);
        System.err.println("  Parser:                          " + DEFAULT_PARSER_NAME);
        System.err.print("  Schema full checking:            ");
        System.err.println(DEFAULT_SCHEMA_FULL_CHECKING ? "on" : "off");
        System.err.print("  Honour all schema locations:     ");
        System.err.println(DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS ? "on" : "off");
        System.err.print("  Validate annotations:            ");
        System.err.println(DEFAULT_VALIDATE_ANNOTATIONS ? "on" : "off");
        System.err.print("  Generate synthetic annotations:  ");
        System.err.println(DEFAULT_GENERATE_SYNTHETIC_ANNOTATIONS ? "on" : "off");
        
    } // printUsage()

} // class TypeInfoWriter
