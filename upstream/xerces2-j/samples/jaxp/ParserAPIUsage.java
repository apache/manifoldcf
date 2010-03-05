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

import java.util.Vector;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * <p>A sample which demonstrates usage of the JAXP 1.3 Parser API.</p>
 * 
 * @author Michael Glavassevich, IBM
 * @author Ankit Pasricha, IBM
 * 
 * @version $Id$
 */
public class ParserAPIUsage extends DefaultHandler {
    
    // default settings
    
    /** Default API to use. */
    protected static final String DEFAULT_API_TO_USE = "sax";
    
    /** Default XInclude processing support (false). */
    protected static final boolean DEFAULT_XINCLUDE = false;
    
    /** Default secure processing support (false). */
    protected static final boolean DEFAULT_SECURE_PROCESSING = false;
    
    //
    // Constructors
    //

    /** Default constructor. */
    public ParserAPIUsage() {
    } // <init>()
    
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
    
    public static void main(String[] argv) {
        
        // is there anything to do?
        if (argv.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        // variables
        ParserAPIUsage parserAPIUsage = new ParserAPIUsage();
        Vector schemas = null;
        String docURI = argv[argv.length - 1];
        String apiToUse = DEFAULT_API_TO_USE;
        boolean xincludeProcessing = DEFAULT_XINCLUDE;
        boolean secureProcessing = DEFAULT_SECURE_PROCESSING;
        
        // process arguments
        for (int i = 0; i < argv.length - 1; ++i) {
            String arg = argv[i];
            if (arg.startsWith("-")) {
                String option = arg.substring(1);
                if (arg.equals("-a")) {
                    // process -a: schema documents
                    if (schemas == null) {
                        schemas = new Vector();
                    }
                    while (i + 1 < argv.length - 1 && !(arg = argv[i + 1]).startsWith("-")) {
                        schemas.add(arg);
                        ++i;
                    }
                    continue;
                }
                if (arg.equals("-api")) {
                    if (i + 1 < argv.length - 1 && !(arg = argv[i + 1]).startsWith("-")) {
                        if (arg.equals("sax") || arg.equals("dom")) {
                            apiToUse = arg;
                        }
                        else {
                            System.err.println("error: unknown source type ("+arg+").");
                        }
                    }
                    continue;
                }
                if (option.equalsIgnoreCase("xi")) {
                    xincludeProcessing = option.equals("xi");
                    continue;
                }
                if (option.equalsIgnoreCase("sp")) {
                    secureProcessing = option.equals("sp");
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
            // Build Schema from sources if there are any
            Schema schema = null;
            if (schemas != null && schemas.size() > 0) {
                SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                factory.setErrorHandler(parserAPIUsage);
                final int length = schemas.size();
                StreamSource[] sources = new StreamSource[length];
                for (int j = 0; j < length; ++j) {
                    sources[j] = new StreamSource((String) schemas.elementAt(j));
                }
                schema = factory.newSchema(sources);
            }
            
            if ("dom".equals(apiToUse)) {
                // Create a DocumentBuilderFactory
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                dbf.setXIncludeAware(xincludeProcessing);
                dbf.setSchema(schema);
                dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, secureProcessing);
                
                // Create a DocumentBuilder
                DocumentBuilder db = dbf.newDocumentBuilder();
                
                // Parse, reset the parser and then parse again.
                db.setErrorHandler(parserAPIUsage);
                db.parse(docURI);
                db.reset();
                db.setErrorHandler(parserAPIUsage);
                db.parse(docURI);
            }
            // "sax".equals(apiToUse)
            else {
                // Create a SAXParserFactory
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(true);
                spf.setXIncludeAware(xincludeProcessing);
                spf.setSchema(schema);
                spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, secureProcessing);
                
                // Create a SAXParser
                SAXParser sp = spf.newSAXParser();
                
                // Parse, reset the parser and then parse again.
                sp.parse(docURI, parserAPIUsage);
                sp.reset();
                sp.parse(docURI, parserAPIUsage);
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

    private static void printUsage() {

        System.err.println("usage: java jaxp.ParserAPIUsage (options) uri");
        System.err.println();
        
        System.err.println("options:");
        System.err.println("  -a uri ...      Provide a list of schema documents.");
        System.err.println("  -api (sax|dom)  Select API to use (sax|dom).");
        System.err.println("  -xi | -XI       Turn on/off XInclude processing.");
        System.err.println("  -sp | -SP       Turn on/off secure processing.");
        System.err.println("  -h              This help screen.");
        
        System.err.println();
        System.err.println("defaults:");
        System.err.println("  API to use:            " + DEFAULT_API_TO_USE);
        System.err.print("  XInclude:              ");
        System.err.println(DEFAULT_XINCLUDE ? "on" : "off");
        System.err.print("  Secure processing:     ");
        System.err.println(DEFAULT_SECURE_PROCESSING ? "on" : "off");
        
    } // printUsage()
    
} // ParserAPIUsage
