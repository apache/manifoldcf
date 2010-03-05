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

package dom.serialize;

import java.io.FileOutputStream;

import org.apache.xerces.dom.DOMImplementationImpl;
import org.apache.xerces.dom.DOMOutputImpl;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * This class is testing namespace algorithm during serialization.
 * The class takes as a parameter xml document, parses it using the DOM parser.
 * By default it will perform modifications to the tree, and then serialize
 * the document using LSSerializer.
 * 
 * @author Elena Litani, IBM
 * @version $Id$
 */
public class TestNS {
    public static void main( String[] argv) {
        try {

            System.out.println("DOM Serializer test for namespace algorithm.");
            DOMParser parser = new DOMParser();
            LSOutput dOut = new DOMOutputImpl();

            if (argv.length == 0) {
                printUsage();
                System.exit(1);
            }
            int repetition = 0;
            boolean namespaces = true;
            boolean validation = false;
            boolean schemaValidation = false;
            boolean deferred = true;
            boolean modify = true;
            boolean stdout = false;
            boolean createEntity = true;
            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                if (arg.startsWith("-")) {
                    String option = arg.substring(1);
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
                        } catch (NumberFormatException e) {
                            System.err.println("error: invalid number ("+number+").");
                        }
                        continue;
                    }

                    if (option.equalsIgnoreCase("d")) {
                        deferred = option.equals("d");
                        continue;
                    }

                    if (option.equalsIgnoreCase("e")) {
                        createEntity = option.equals("e");
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
                    if (option.equalsIgnoreCase("m")) {
                        modify = option.equals("m");
                        continue;
                    }
                    if (option.equalsIgnoreCase("o")) {
                        stdout = option.equals("o");
                        continue;
                    }
                }
                parser.setFeature( "http://xml.org/sax/features/validation", validation );
                parser.setFeature( "http://apache.org/xml/features/dom/defer-node-expansion", deferred);
                //parser.setFeature("http://xml.org/sax/features/external-general-entities", true);
                parser.setFeature( "http://xml.org/sax/features/namespaces",namespaces);
                parser.setFeature("http://apache.org/xml/features/dom/include-ignorable-whitespace", true);
                parser.setFeature("http://apache.org/xml/features/dom/create-entity-ref-nodes", createEntity);
                parser.parse( argv[i] );

                Document core = parser.getDocument();
                DocumentType doctype = core.getDoctype();
                if (doctype !=null) {
                
                NamedNodeMap entities = doctype.getEntities();
                if (entities!=null) {
                    Node entity = entities.getNamedItem("book");
                    if (entity != null) {
                        System.out.println("ENTITY book="+entity.getNodeValue());
                        Node child = entity.getFirstChild();
                        while (child != null) {
                            System.out.println("==>child: '"+child.getNodeName()+"' " + child.getNodeValue());
                            child = child.getNextSibling();
                        }

                    }
                }
                }

                if (modify) {

                    System.out.println("Modifying document...");
                    if (namespaces) {

                        NodeList ls2 = core.getElementsByTagName("empty");
                        Node testElem = ls2.item(0);
                        // testing empty element
                        if (testElem !=null) {
                            NamedNodeMap testAttr = testElem.getAttributes();
                            testAttr.removeNamedItemNS("http://www.w3.org/2000/xmlns/", "xmlns");
                        }
                    }
                    Element root = core.getDocumentElement();
                    // find first element child
                    Node element = root.getFirstChild();
                    while (element!=null && element.getNodeType() != Node.ELEMENT_NODE) {
                        element = element.getNextSibling();
                    }
                    if (element == null) {
                        System.err.println("Test failed: no element node was found.");
                        System.exit(1);
                    } else {
                        System.out.println("Modifying information for the element: "+element.getNodeName());
                    }

                    
                    element.appendChild(core.createComment("attributes: make sure that NS* prefix is created"));
                    element.appendChild(core.createTextNode("\n"));
                    Element e1 = core.createElementNS(null, "root");
                    element.appendChild(e1);
                    e1.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xx", "foo");
                    e1.setAttributeNS("http://rsa2", "xx:attr", "value");
                    element.appendChild(e1);
                    element.appendChild(core.createTextNode("\n\n"));


                    // add element in same scope                            
                    element.appendChild(core.createComment("add element in the same scope"));
                    element.appendChild(core.createTextNode("\n"));
                    element.appendChild(core.createElementNS("urn:schemas-xmlsoap-org:soap.v1","s:child1"));
                    element.appendChild(core.createTextNode("\n\n"));


                    // add element with s bound to different namespace
                    element.appendChild(core.createComment("add element with s bound to different namespace"));
                    element.appendChild(core.createTextNode("\n"));
                    element.appendChild(core.createElementNS("http://child2","s:child2"));
                    element.appendChild(core.createTextNode("\n\n"));

                    // add element with no prefix bound to different scope than default prefix
                    element.appendChild(core.createComment("add element with no prefix bound to different scope than default prefix"));
                    element.appendChild(core.createTextNode("\n"));
                    element.appendChild(core.createElementNS("http://child3/default","child3"));
                    element.appendChild(core.createTextNode("\n\n"));


                    element.appendChild(core.createComment("add element no prefix no namespace"));
                    element.appendChild(core.createTextNode("\n"));
                    element.appendChild(core.createElementNS(null,"child4"));
                    element.appendChild(core.createTextNode("\n\n"));

                    element.appendChild(core.createComment("make sure only one 'xmlns:m1'declaration appears not several"));
                    element.appendChild(core.createTextNode("\n"));
                    e1 = core.createElementNS("http://rsa", "m1:root");
                    element.appendChild(e1);
                    Element e2 = core.createElementNS("http://rsa", "m1:e1");
                    e2.setAttributeNS("http://rsa", "m1:a1", "v");
                    e2.setAttributeNS("http://rsa2", "m2:a2", "v");
                    e1.appendChild(e2);
                    element.appendChild(core.createTextNode("\n\n"));
                    

                    Text text;
                    // add xmlns attribute
                    element.appendChild(core.createComment("create element: prefix bound to http://child7,"
                                                           +" local declaration of xmlns:prefix = http://child8"));
                    element.appendChild(core.createTextNode("\n"));
                    Element elm = core.createElementNS("http://child7","prefix:child7");
                    Attr attr = core.createAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:prefix");
                    attr.setValue("http://child8");
                    elm.setAttributeNode(attr);
                    element.appendChild(elm);
                    element.appendChild(core.createTextNode("\n\n"));


                    // add element with empty string as uri
                    element.appendChild(core.createComment("add child5, uri=null, xmlns:p=emptyStr (invalid)"));
                    element.appendChild(core.createTextNode("\n"));
                    elm = core.createElementNS(null,"child5");
                    attr = core.createAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:p");
                    attr.setValue(null);
                    elm.setAttributeNode(attr);
                    element.appendChild(elm);
                    element.appendChild(core.createTextNode("\n\n"));


       
                    // add 2 xmlns attribute
                    element.appendChild(core.createComment("create element: with 2 xmlns"));
                    element.appendChild(core.createTextNode("\n"));
                    elm = core.createElementNS("http://child6","child6");
                    attr = core.createAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns");
                    attr.setValue("http://default");
                    elm.setAttributeNode(attr);

                    attr = core.createAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns");
                    attr.setValue("http://default2");
                    elm.setAttributeNode(attr);
                    element.appendChild(elm);
                    element.appendChild(core.createTextNode("\n\n"));


                    // work on attributes algorithm
                    element.appendChild(core.createComment("\n1) attr3 (with no prefix) and bound to http://attr3 (that is not declared)."+
                                                           "\n2) attr1 attribute with null namespace"+
                                                           "\n3) attr2 with declared s - no change\n"));
                    element.appendChild(core.createTextNode("\n"));
                    elm = core.createElementNS("urn:schemas-xmlsoap-org:soap.v1","s:testAttributes");

                    attr = core.createAttributeNS(null, "attr1");
                    elm.setAttributeNode(attr);

                    attr = core.createAttributeNS("urn:schemas-xmlsoap-org:soap.v1", "s:attr2"); 
                    elm.setAttributeNode(attr);

                    attr = core.createAttributeNS("http://attr3", "attr3"); 
                    elm.setAttributeNode(attr);
                    element.appendChild(elm);
                    element.appendChild(core.createTextNode("\n\n"));

                    /*
                    element.appendChild(core.createComment("NON-WELLFORMED! xml:space attribute, \ndoml1 valid/invalid namespace declarations, \ndoml2 invalid declarations xmlns:foo=\"\""));
                    element.appendChild(core.createTextNode("\n"));
                    elm = core.createElementNS(null,"spaces");
                    attr = core.createAttributeNS("http://www.w3.org/XML/1998/namespace", "boo:space");
                    elm.setAttributeNode(attr);
                    attr = core.createAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:foo");
                    elm.setAttributeNode(attr);

                    attr = core.createAttribute("s:level1Node");
                    elm.setAttributeNode(attr);

                    attr = core.createAttribute("xmlns:");
                    elm.setAttributeNode(attr);
                    attr = core.createAttribute("xmlns:k");
                    elm.setAttributeNode(attr);
                    attr = core.createAttribute("xmlns:k:d");
                    elm.setAttributeNode(attr);

                    element.appendChild(elm);
                    element.appendChild(core.createTextNode("\n\n"));
                    */


                    // more attributes tests

                    element.appendChild(core.createComment("\n1) attr_B with no prefix and http://attr_B"+
                                                           "\n2) attr_A had no prefix and http://attr_A. There is local default decl bound to the same namespace"));
                    element.appendChild(core.createTextNode("\n"));
                    elm = core.createElementNS("urn:schemas-xmlsoap-org:soap.v1","s:testAttributes2");
                    attr = core.createAttributeNS("http://attr_A", "attr_A");
                    elm.setAttributeNode(attr);
                    attr = core.createAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns");
                    attr.setValue("http://attr_A");
                    elm.setAttributeNode(attr);

                    
                    attr = core.createAttributeNS("http://attr_B", "attr_B");
                    elm.setAttributeNode(attr);
                    element.appendChild(elm);

                    element.appendChild(core.createTextNode("\n"));


                }

                //((CoreDocumentImpl)core).normalizeDocument();
                
                
                /*
                OutputFormat format = new OutputFormat((Document)core);
                format.setLineSeparator(LineSeparator.Windows);
                format.setIndenting(true);
                format.setLineWidth(0);             
                format.setPreserveSpace(true);
                */

                
                // create DOM Serializer
                LSSerializer writer = ((DOMImplementationLS)DOMImplementationImpl.getDOMImplementation()).createLSSerializer();
                

                // Serializer that ouputs tree in not pretty print format
                if (stdout) {

                    //XMLSerializer serializer = new XMLSerializer(System.out, format);
                    //serializer.asDOMSerializer();
                    //serializer.serialize((Document)core);
                    //writer.setFeature("entities",true);
                    //writer.writeNode(System.out, core);
                      dOut.setByteStream(System.out);
                      writer.write(core,dOut);
                } else {

                    System.out.println("Serializing output to output.xml...");
                    //XMLSerializer toFile = new XMLSerializer (new FileWriter("output.xml"), format);
                    //toFile.asDOMSerializer();
                    //toFile.serialize((Document)core);
                    dOut.setByteStream(new FileOutputStream("output.xml"));
                    writer.write(core,dOut);
                }

                /** Test SAX Serializer 
                 System.out.println("Testing SAX Serializer...");
                XMLSerializer saxSerializer;
                if (stdout) {                
                    saxSerializer = new XMLSerializer (System.out, format);
                } else {
                    saxSerializer = new XMLSerializer (new FileWriter("sax_output.xml"), format);
                }

                format.setEncoding("US-ASCII");
                saxSerializer.startDocument();
                //saxSerializer.processingInstruction("foo", "bar");
                saxSerializer.startDTD("foo", "bar", "baz");
                saxSerializer.startElement("myNamespace", "a", "foo:a", null);
                saxSerializer.startElement("myNamespace", "b", "foo:b", null);
                saxSerializer.startCDATA();
                char data[] = {'a', ']', ']', '>', '\u0089', 'n'};     

                saxSerializer.characters(data, 0, 6);
                saxSerializer.endCDATA();
                saxSerializer.comment("A & B");
                saxSerializer.startElement("myNamespace", "c", "foo:c", null);
                saxSerializer.endElement("myNamespace", "c", "foo:c");
                saxSerializer.endElement("myNamespace", "b", "foo:b");

                saxSerializer.endElement("myNamespace", "a", "foo:a");
                saxSerializer.endDocument();
                System.out.println("Serializing output to sax_output.xml...");
                 */       

            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private static void printUsage() {

        System.err.println("usage: java dom.serialize.TestNS (options) uri ...");
        System.err.println();

        System.err.println("options:");
        System.err.println("  -x number   Select number of repetitions.");
        System.err.println("              NOTE: Requires use of -n.");
        System.err.println("  -o  | -O    Turn on/off serialization to standard output.");
        System.err.println("  -m  | -M    Turn on/off document modification.");
        System.err.println("  -n  | -N    Turn on/off namespace processing.");
        System.err.println("  -v  | -V    Turn on/off validation.");
        System.err.println("  -s  | -S    Turn on/off Schema validation support.");        
        System.err.println();



    } // printUsage()
}


