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

package sax;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.xml.sax.AttributeList;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DocumentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.ErrorHandler;
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
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;

/**
 * Provides a complete trace of SAX2 events for files parsed. This is
 * useful for making sure that a SAX parser implementation faithfully
 * communicates all information in the document to the SAX handlers.
 *
 * @author Andy Clark, IBM
 * @author Arnaud Le Hors, IBM
 *
 * @version $Id$
 */
public class DocumentTracer
    extends DefaultHandler
    implements ContentHandler, DTDHandler, ErrorHandler, // SAX2
               DeclHandler, LexicalHandler, // SAX2 extensions
               DocumentHandler // deprecated in SAX2
    {

    //
    // Constants
    //

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
    
    /** Honour all schema locations feature id (http://apache.org/xml/features/honour-all-schemaLocations). */
    protected static final String HONOUR_ALL_SCHEMA_LOCATIONS_ID = "http://apache.org/xml/features/honour-all-schemaLocations";
    
    /** Validate schema annotations feature id (http://apache.org/xml/features/validate-annotations) */
    protected static final String VALIDATE_ANNOTATIONS_ID = "http://apache.org/xml/features/validate-annotations";
    
    /** Dynamic validation feature id (http://apache.org/xml/features/validation/dynamic). */
    protected static final String DYNAMIC_VALIDATION_FEATURE_ID = "http://apache.org/xml/features/validation/dynamic";
    
    /** Load external DTD feature id (http://apache.org/xml/features/nonvalidating/load-external-dtd). */
    protected static final String LOAD_EXTERNAL_DTD_FEATURE_ID = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    
    /** XInclude feature id (http://apache.org/xml/features/xinclude). */
    protected static final String XINCLUDE_FEATURE_ID = "http://apache.org/xml/features/xinclude";
    
    /** XInclude fixup base URIs feature id (http://apache.org/xml/features/xinclude/fixup-base-uris). */
    protected static final String XINCLUDE_FIXUP_BASE_URIS_FEATURE_ID = "http://apache.org/xml/features/xinclude/fixup-base-uris";
    
    /** XInclude fixup language feature id (http://apache.org/xml/features/xinclude/fixup-language). */
    protected static final String XINCLUDE_FIXUP_LANGUAGE_FEATURE_ID = "http://apache.org/xml/features/xinclude/fixup-language";

    // property ids

    /** Lexical handler property id (http://xml.org/sax/properties/lexical-handler). */
    protected static final String LEXICAL_HANDLER_PROPERTY_ID = "http://xml.org/sax/properties/lexical-handler";

    // default settings

    /** Default parser name. */
    protected static final String DEFAULT_PARSER_NAME = "org.apache.xerces.parsers.SAXParser";

    /** Default namespaces support (true). */
    protected static final boolean DEFAULT_NAMESPACES = true;
    
    /** Default namespace prefixes (false). */
    protected static final boolean DEFAULT_NAMESPACE_PREFIXES = false;

    /** Default validation support (false). */
    protected static final boolean DEFAULT_VALIDATION = false;
    
    /** Default load external DTD (true). */
    protected static final boolean DEFAULT_LOAD_EXTERNAL_DTD = true;

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

    /** Print writer. */
    protected PrintWriter fOut;

    /** Indent level. */
    protected int fIndent;

    //
    // Constructors
    //

    /** Default constructor. */
    public DocumentTracer() {
        setOutput(new PrintWriter(System.out));
    } // <init>()

    //
    // Public methods
    //

    /** Sets the output stream for printing. */
    public void setOutput(OutputStream stream, String encoding)
        throws UnsupportedEncodingException {

        if (encoding == null) {
            encoding = "UTF8";
        }

        Writer writer = new OutputStreamWriter(stream, encoding);
        fOut = new PrintWriter(writer);

    } // setOutput(OutputStream,String)

    /** Sets the output writer. */
    public void setOutput(Writer writer) {

        fOut = writer instanceof PrintWriter
             ? (PrintWriter)writer : new PrintWriter(writer);

    } // setOutput(Writer)

    //
    // ContentHandler and DocumentHandler methods
    //

    /** Set document locator. */
    public void setDocumentLocator(Locator locator) {

        printIndent();
        fOut.print("setDocumentLocator(");
        fOut.print("locator=");
        fOut.print(locator);
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

    /** Processing instruction. */
    public void processingInstruction(String target, String data)
        throws SAXException {

        printIndent();
        fOut.print("processingInstruction(");
        fOut.print("target=");
        printQuotedString(target);
        fOut.print(',');
        fOut.print("data=");
        printQuotedString(data);
        fOut.println(')');
        fOut.flush();

    } // processingInstruction(String,String)

    /** Characters. */
    public void characters(char[] ch, int offset, int length)
        throws SAXException {

        printIndent();
        fOut.print("characters(");
        fOut.print("text=");
        printQuotedString(ch, offset, length);
        fOut.println(')');
        fOut.flush();

    } // characters(char[],int,int)

    /** Ignorable whitespace. */
    public void ignorableWhitespace(char[] ch, int offset, int length)
        throws SAXException {

        printIndent();
        fOut.print("ignorableWhitespace(");
        fOut.print("text=");
        printQuotedString(ch, offset, length);
        fOut.println(')');
        fOut.flush();

    } // ignorableWhitespace(char[],int,int)

    /** End document. */
    public void endDocument() throws SAXException {

        fIndent--;
        printIndent();
        fOut.println("endDocument()");
        fOut.flush();

    } // endDocument()

    //
    // ContentHandler methods
    //

    /** Start prefix mapping. */
    public void startPrefixMapping(String prefix, String uri)
        throws SAXException {

        printIndent();
        fOut.print("startPrefixMapping(");
        fOut.print("prefix=");
        printQuotedString(prefix);
        fOut.print(',');
        fOut.print("uri=");
        printQuotedString(uri);
        fOut.println(')');
        fOut.flush();

    } // startPrefixMapping(String,String)

    /** Start element. */
    public void startElement(String uri, String localName, String qname,
                             Attributes attributes) throws SAXException {

        printIndent();
        fOut.print("startElement(");
        fOut.print("uri=");
        printQuotedString(uri);
        fOut.print(',');
        fOut.print("localName=");
        printQuotedString(localName);
        fOut.print(',');
        fOut.print("qname=");
        printQuotedString(qname);
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
                String attrLocalName = attributes.getLocalName(i);
                String attrQName = attributes.getQName(i);
                String attrURI = attributes.getURI(i);
                String attrType = attributes.getType(i);
                String attrValue = attributes.getValue(i);
                fOut.print('{');
                fOut.print("uri=");
                printQuotedString(attrURI);
                fOut.print(',');
                fOut.print("localName=");
                printQuotedString(attrLocalName);
                fOut.print(',');
                fOut.print("qname=");
                printQuotedString(attrQName);
                fOut.print(',');
                fOut.print("type=");
                printQuotedString(attrType);
                fOut.print(',');
                fOut.print("value=");
                printQuotedString(attrValue);
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
        fOut.print("uri=");
        printQuotedString(uri);
        fOut.print(',');
        fOut.print("localName=");
        printQuotedString(localName);
        fOut.print(',');
        fOut.print("qname=");
        printQuotedString(qname);
        fOut.println(')');
        fOut.flush();

    } // endElement(String,String,String)

    /** End prefix mapping. */
    public void endPrefixMapping(String prefix) throws SAXException {

        printIndent();
        fOut.print("endPrefixMapping(");
        fOut.print("prefix=");
        printQuotedString(prefix);
        fOut.println(')');
        fOut.flush();

    } // endPrefixMapping(String)

    /** Skipped entity. */
    public void skippedEntity(String name) throws SAXException {

        printIndent();
        fOut.print("skippedEntity(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.println(')');
        fOut.flush();

    } // skippedEntity(String)

    //
    // DocumentHandler methods
    //

    /** Start element. */
    public void startElement(String name, AttributeList attributes)
        throws SAXException {

        printIndent();
        fOut.print("startElement(");
        fOut.print("name=");
        printQuotedString(name);
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
                    System.out.print(',');
                }
                String attrName = attributes.getName(i);
                String attrType = attributes.getType(i);
                String attrValue = attributes.getValue(i);
                fOut.print('{');
                fOut.print("name=");
                printQuotedString(attrName);
                fOut.print(',');
                fOut.print("type=");
                printQuotedString(attrType);
                fOut.print(',');
                fOut.print("value=");
                printQuotedString(attrValue);
                fOut.print('}');
            }
            fOut.print('}');
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startElement(String,AttributeList)

    /** End element. */
    public void endElement(String name) throws SAXException {

        fIndent--;
        printIndent();
        fOut.print("endElement(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.println(')');
        fOut.flush();

    } // endElement(String)

    //
    // DTDHandler methods
    //

    /** Notation declaration. */
    public void notationDecl(String name, String publicId, String systemId)
        throws SAXException {

        printIndent();
        fOut.print("notationDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("publicId=");
        printQuotedString(publicId);
        fOut.print(',');
        fOut.print("systemId=");
        printQuotedString(systemId);
        fOut.println(')');
        fOut.flush();

    } // notationDecl(String,String,String)

    /** Unparsed entity declaration. */
    public void unparsedEntityDecl(String name,
                                   String publicId, String systemId,
                                   String notationName) throws SAXException {
        printIndent();
        fOut.print("unparsedEntityDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("publicId=");
        printQuotedString(publicId);
        fOut.print(',');
        fOut.print("systemId=");
        printQuotedString(systemId);
        fOut.print(',');
        fOut.print("notationName=");
        printQuotedString(notationName);
        fOut.println(')');
        fOut.flush();

    } // unparsedEntityDecl(String,String,String,String)

    //
    // LexicalHandler methods
    //

    /** Start DTD. */
    public void startDTD(String name, String publicId, String systemId)
        throws SAXException {

        printIndent();
        fOut.print("startDTD(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("publicId=");
        printQuotedString(publicId);
        fOut.print(',');
        fOut.print("systemId=");
        printQuotedString(systemId);
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startDTD(String,String,String)

    /** Start entity. */
    public void startEntity(String name) throws SAXException {

        printIndent();
        fOut.print("startEntity(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startEntity(String)

    /** Start CDATA section. */
    public void startCDATA() throws SAXException {

        printIndent();
        fOut.println("startCDATA()");
        fOut.flush();
        fIndent++;

    } // startCDATA()

    /** End CDATA section. */
    public void endCDATA() throws SAXException {

        fIndent--;
        printIndent();
        fOut.println("endCDATA()");
        fOut.flush();

    } // endCDATA()

    /** Comment. */
    public void comment(char[] ch, int offset, int length)
        throws SAXException {

        printIndent();
        fOut.print("comment(");
        fOut.print("text=");
        printQuotedString(ch, offset, length);
        fOut.println(')');
        fOut.flush();

    } // comment(char[],int,int)

    /** End entity. */
    public void endEntity(String name) throws SAXException {

        fIndent--;
        printIndent();
        fOut.print("endEntity(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.println(')');

    } // endEntity(String)

    /** End DTD. */
    public void endDTD() throws SAXException {

        fIndent--;
        printIndent();
        fOut.println("endDTD()");
        fOut.flush();

    } // endDTD()

    //
    // DeclHandler methods
    //

    /** Element declaration. */
    public void elementDecl(String name, String contentModel)
        throws SAXException {

        printIndent();
        fOut.print("elementDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("contentModel=");
        printQuotedString(contentModel);
        fOut.println(')');
        fOut.flush();

    } // elementDecl(String,String)

    /** Attribute declaration. */
    public void attributeDecl(String elementName, String attributeName,
                              String type, String valueDefault,
                              String value) throws SAXException {

        printIndent();
        fOut.print("attributeDecl(");
        fOut.print("elementName=");
        printQuotedString(elementName);
        fOut.print(',');
        fOut.print("attributeName=");
        printQuotedString(attributeName);
        fOut.print(',');
        fOut.print("type=");
        printQuotedString(type);
        fOut.print(',');
        fOut.print("valueDefault=");
        printQuotedString(valueDefault);
        fOut.print(',');
        fOut.print("value=");
        printQuotedString(value);
        fOut.println(')');
        fOut.flush();

    } // attributeDecl(String,String,String,String,String)

    /** Internal entity declaration. */
    public void internalEntityDecl(String name, String text)
        throws SAXException {

        printIndent();
        fOut.print("internalEntityDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("text=");
        printQuotedString(text);
        fOut.println(')');
        fOut.flush();

    } // internalEntityDecl(String,String)

    /** External entity declaration. */
    public void externalEntityDecl(String name,
                                   String publicId, String systemId)
        throws SAXException {

        printIndent();
        fOut.print("externalEntityDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("publicId=");
        printQuotedString(publicId);
        fOut.print(',');
        fOut.print("systemId=");
        printQuotedString(systemId);
        fOut.println(')');
        fOut.flush();

    } // externalEntityDecl(String,String,String)

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

    /** Print quoted string. */
    protected void printQuotedString(String s) {

        if (s == null) {
            fOut.print("null");
            return;
        }

        fOut.print('"');
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            normalizeAndPrint(c);
        }
        fOut.print('"');

    } // printQuotedString(String)

    /** Print quoted string. */
    protected void printQuotedString(char[] ch, int offset, int length) {

        fOut.print('"');
        for (int i = 0; i < length; i++) {
            normalizeAndPrint(ch[offset + i]);
        }
        fOut.print('"');

    } // printQuotedString(char[],int,int)

    /** Normalize and print. */
    protected void normalizeAndPrint(char c) {

        switch (c) {
            case '\n': {
                fOut.print("\\n");
                break;
            }
            case '\r': {
                fOut.print("\\r");
                break;
            }
            case '\t': {
                fOut.print("\\t");
                break;
            }
            case '\\': {
                fOut.print("\\\\");
                break;
            }
            case '"': {
                fOut.print("\\\"");
                break;
            }
            default: {
                fOut.print(c);
            }
        }

    } // normalizeAndPrint(char)

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
    }

    //
    // MAIN
    //

    /** Main. */
    public static void main(String[] argv) throws Exception {

        // is there anything to do?
        if (argv.length == 0) {
            printUsage();
            System.exit(1);
        }

        // variables
        DocumentTracer tracer = new DocumentTracer();
        PrintWriter out = new PrintWriter(System.out);
        XMLReader parser = null;
        boolean namespaces = DEFAULT_NAMESPACES;
        boolean namespacePrefixes = DEFAULT_NAMESPACE_PREFIXES;
        boolean validation = DEFAULT_VALIDATION;
        boolean externalDTD = DEFAULT_LOAD_EXTERNAL_DTD;
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
                        }
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
                if (option.equalsIgnoreCase("xd")) {
                    externalDTD = option.equals("xd");
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
                    parser = XMLReaderFactory.createXMLReader(DEFAULT_PARSER_NAME);
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
                parser.setFeature(NAMESPACE_PREFIXES_FEATURE_ID, namespacePrefixes);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+NAMESPACE_PREFIXES_FEATURE_ID+")");
            }
            try {
                parser.setFeature(VALIDATION_FEATURE_ID, validation);
            }
            catch (SAXException e) {
                System.err.println("warning: Parser does not support feature ("+VALIDATION_FEATURE_ID+")");
            }
            try {
                parser.setFeature(LOAD_EXTERNAL_DTD_FEATURE_ID, externalDTD);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+LOAD_EXTERNAL_DTD_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+LOAD_EXTERNAL_DTD_FEATURE_ID+")");
            }
            try {
                parser.setFeature(SCHEMA_VALIDATION_FEATURE_ID, schemaValidation);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+SCHEMA_VALIDATION_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+SCHEMA_VALIDATION_FEATURE_ID+")");
            }
            try {
                parser.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, schemaFullChecking);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
            }
            try {
                parser.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS_ID, honourAllSchemaLocations);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
            }
            try {
                parser.setFeature(VALIDATE_ANNOTATIONS_ID, validateAnnotations);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+VALIDATE_ANNOTATIONS_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+VALIDATE_ANNOTATIONS_ID+")");
            }
            try {
                parser.setFeature(DYNAMIC_VALIDATION_FEATURE_ID, dynamicValidation);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+DYNAMIC_VALIDATION_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+DYNAMIC_VALIDATION_FEATURE_ID+")");
            }
            try {
                parser.setFeature(XINCLUDE_FEATURE_ID, xincludeProcessing);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+XINCLUDE_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+XINCLUDE_FEATURE_ID+")");
            }
            try {
                parser.setFeature(XINCLUDE_FIXUP_BASE_URIS_FEATURE_ID, xincludeFixupBaseURIs);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+XINCLUDE_FIXUP_BASE_URIS_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+XINCLUDE_FIXUP_BASE_URIS_FEATURE_ID+")");
            }
            try {
                parser.setFeature(XINCLUDE_FIXUP_LANGUAGE_FEATURE_ID, xincludeFixupLanguage);
            }
            catch (SAXNotRecognizedException e) {
                System.err.println("warning: Parser does not recognize feature ("+XINCLUDE_FIXUP_LANGUAGE_FEATURE_ID+")");
            }
            catch (SAXNotSupportedException e) {
                System.err.println("warning: Parser does not support feature ("+XINCLUDE_FIXUP_LANGUAGE_FEATURE_ID+")");
            }

            // set handlers
            parser.setDTDHandler(tracer);
            parser.setErrorHandler(tracer);
            if (parser instanceof XMLReader) {
                parser.setContentHandler(tracer);
                try {
                    parser.setProperty("http://xml.org/sax/properties/declaration-handler", tracer);
                }
                catch (SAXException e) {
                    e.printStackTrace(System.err);
                }
                try {
                    parser.setProperty("http://xml.org/sax/properties/lexical-handler", tracer);
                }
                catch (SAXException e) {
                    e.printStackTrace(System.err);
                }
            }
            else {
                ((Parser)parser).setDocumentHandler(tracer);
            }

            // parse file
            try {
                parser.parse(arg);
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
        }

    } // main(String[])

    //
    // Private static methods
    //

    /** Prints the usage. */
    private static void printUsage() {

        System.err.println("usage: java sax.DocumentTracer (options) uri ...");
        System.err.println();

        System.err.println("options:");
        System.err.println("  -p name     Select parser by name.");
        System.err.println("  -n  | -N    Turn on/off namespace processing.");
        System.err.println("  -np | -NP   Turn on/off namespace prefixes.");
        System.err.println("              NOTE: Requires use of -n.");
        System.err.println("  -v  | -V    Turn on/off validation.");
        System.err.println("  -xd | -XD   Turn on/off loading of external DTDs.");
        System.err.println("              NOTE: Always on when -v in use and not supported by all parsers.");
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
        System.err.print("  Namespaces: ");
        System.err.println(DEFAULT_NAMESPACES ? "on" : "off");
        System.err.print("  Prefixes:   ");
        System.err.println(DEFAULT_NAMESPACE_PREFIXES ? "on" : "off");
        System.err.print("  Validation: ");
        System.err.println(DEFAULT_VALIDATION ? "on" : "off");
        System.err.print("  Load External DTD: ");
        System.err.println(DEFAULT_LOAD_EXTERNAL_DTD ? "on" : "off");
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

} // class DocumentTracer
