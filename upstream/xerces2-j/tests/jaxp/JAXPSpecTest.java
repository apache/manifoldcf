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

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** 
 * This testcase tests for following scenarios as per JAXP 1.2 specification.
 *
 * 1.Parser(SAX and DOM) should ignore SchemaLanguage property when validation
 *  feature is set to false.
 * 2.SAXParser should throw SAXNotSupportedException when SchemaSource property is
 *  set without setting SchemaLanguage property.
 * 3.DOMParser should throw IllegalArgumentException when SchemaSource property is
 *  set without setting SchemaLanguage property.
 *
 * @author k.venugopal@sun.com
 * 
 * @version $Id$
 */
public class JAXPSpecTest extends DefaultHandler {
    
    /** TestCase Main.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        
        JAXPSpecTest jaxpTest = new JAXPSpecTest();
        
        if (args.length == 0) {
            JAXPSpecTest.printUsage();
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if(arg.equals("testSchemaLanguageSAX"))
                jaxpTest.testSchemaLanguageSAX();
            else if(arg.equals("testSchemaSourceSAX"))
                jaxpTest.testSchemaSourceSAX();
            else if(arg.equals("testSchemaLanguageDOM"))
                jaxpTest.testSchemaLanguageDOM();
            else if(arg.equals("testSchemaSourceDOM"))
                jaxpTest.testSchemaSourceDOM();
            else
                JAXPSpecTest.printUsage();
        }
        
    }
    
    /** 
     * Schema Language property should be ignored if
     * validation feature is set to false
     * @throws Exception
     */
    
    public void testSchemaLanguageSAX() throws Exception{
        
        System.out.println(" Running JAXPSpecTest.testSchemaLanguageSAX ");
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setValidating(false);
        SAXParser saxParser = spf.newSAXParser();
        //Schema Language Property should be ignored since validation is set to false.
        saxParser.setProperty(
        "http://java.sun.com/xml/jaxp/properties/schemaLanguage",
        "http://www.w3.org/2001/XMLSchema");
        saxParser.parse("tests/jaxp/data/personal-schema.xml",this);
        System.out.println(" JAXPSpecTest.testSchemaLanguageSAX Passed ");
    }
    
    /** SAXParser should throw SAXNotSupportedException when SchemaSource property is
     * set without setting SchemaLanguage property
     * @throws Exception
     */
    
    public void testSchemaSourceSAX() throws Exception{
        try{
            System.out.println(" Running JAXPSpecTest.testSchemaSourceSAX ");
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setValidating(true);
            SAXParser saxParser = spf.newSAXParser();
            //Schema Language property should be set before setting schema source property.
            //setting this property should throw SAXNotSupportedException
            saxParser.setProperty(
            "http://java.sun.com/xml/jaxp/properties/schemaSource",
            "tests/jaxp/data/personal-schema.xsd");
            saxParser.parse("tests/jaxp/data/personal-schema.xml",this);
        }catch(SAXNotSupportedException ne){
            System.out.println(" JAXPSpecTest.testSchemaSourceSAX Passed");
        }
    }
    
    /** Schema Language property should be ignored if
     * validation feature is set to false
     * @throws Exception  */
    
    public void testSchemaLanguageDOM() throws Exception {
        
        System.out.println(" Running JAXPSpecTest.testSchemaLanguageDOM ");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        //Schema Language Property should be ignored since validation is set to false.
        dbf.setAttribute(
        "http://java.sun.com/xml/jaxp/properties/schemaLanguage",
        "http://www.w3.org/2001/XMLSchema");
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        docBuilder.setErrorHandler(this);
        Document document = docBuilder.parse(
        new File("tests/jaxp/data/personal-schema.xml"));
        System.out.println(" JAXPSpecTest.testSchemaLanguageDOM Passed");
        
    }
    
    /** DOMParser should throw IllegalArgumentException when SchemaSource property is
     * set without setting SchemaLanguage property.
     * @throws Exception
     */
    
    public void testSchemaSourceDOM() throws Exception {
        try{
            System.out.println(" Running JAXPSpecTest.testSchemaSourceDOM ");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(true);
            //Schema Language property should be set before setting schema source property.
            //setting this property should throw IllegalArgumentException
            dbf.setAttribute(
            "http://java.sun.com/xml/jaxp/properties/schemaSource",
            "tests/jaxp/data/personal-schema.xsd");
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            docBuilder.setErrorHandler(this);
            Document document = docBuilder.parse(
            "tests/jaxp/data/personal-schema.xml");
        } catch (IllegalArgumentException e) {
            System.out.println(" JAXPSpecTest.testSchemaSourceDOM Passed");
        }
        
    }
    
    
    /** Handles Warnings
     * @param ex
     * @throws SAXException
     */
    public void warning(SAXParseException ex) throws SAXException {
        printError("Warning", ex);
    }
    
    /** Handles Errors.
     * @param ex
     * @throws SAXException
     */
    
    public void error(SAXParseException ex) throws SAXException {
        printError("Error", ex);
    }
    
    /** Handles Fatal errors.
     * @param ex
     * @throws SAXException
     */
    
    public void fatalError(SAXParseException ex) throws SAXException {
        printError("Fatal Error", ex);
        throw ex;
    }
    
    
    /**
     * @param type
     * @param ex
     */
    private void printError(String type, SAXParseException ex) {
        
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
        
    }
    
    private static void printUsage(){
        System.err.println("Usage : JAXPSpecTest testSchemaLanguageSAX testSchemaSourceSAX testSchemaLanguageDOM testSchemaSourceDOM  ... ");
    }
    
    
}

