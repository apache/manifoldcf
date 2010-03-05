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

package dom.treewalker;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;


/**
 * The class tests TreeWalkerImpl.firstChild() and TreeWalkerImpl.nextSibling();
 * The class generates simple XML document and traverses it.
 * 
 * @author Christian Geuer-Pollmann <geuer-pollmann@nue.et-inf.uni-siegen.de>
 * @version $Id$
 */
public class TestFirstChild {

    public static void main(String args[]) throws Exception {


        System.out.println(" --- "
                           + org.apache.xerces.impl.Version.getVersion()
                           + " --- ");
        Document doc = getNodeSet1();
        NodeFilter nodefilter = null;
        boolean entityReferenceExpansion = true;
        int whatToShow = NodeFilter.SHOW_ALL;
        TreeWalker treewalker =
        ((DocumentTraversal) doc).createTreeWalker(doc, whatToShow,
                                                   nodefilter, entityReferenceExpansion);
        ByteArrayOutputStream bytearrayoutputstream =
        new ByteArrayOutputStream();
        PrintWriter printwriter =
        new PrintWriter(new OutputStreamWriter(bytearrayoutputstream,
                                               "UTF8"));

        process2(treewalker, printwriter);
        printwriter.flush();

        System.out.println();
        System.out.println("Testing the following XML document:\n" + new String(bytearrayoutputstream.toByteArray()));
    }

    /**
     * Method getNodeSet1
     *
     * @return
     * @throws ParserConfigurationException
     */
    private static Document getNodeSet1()
    throws ParserConfigurationException {

        DocumentBuilderFactory dfactory =
        DocumentBuilderFactory.newInstance();

        dfactory.setValidating(false);
        dfactory.setNamespaceAware(true);

        DocumentBuilder db = dfactory.newDocumentBuilder();
        Document doc = db.newDocument();
        Element root = doc.createElement("RootElement");
        Element e1 = doc.createElement("Element1");
        Element e2 = doc.createElement("Element2");
        Element e3 = doc.createElement("Element3");
        Text e3t = doc.createTextNode("Text in Element3");

        e3.appendChild(e3t);
        root.appendChild(e1);
        root.appendChild(e2);
        root.appendChild(e3);
        doc.appendChild(root);

        String s1 ="<RootElement><Element1/><Element2/><Element3>Text in Element3</Element3></RootElement>";

        return doc;
    }

    /**
     * recursively traverses the tree
     *
     * for simplicity, I don't handle comments, Attributes, PIs etc.
     * Only Text, Document and Element
     *
     * @param treewalker
     * @param printwriter
     */
    private static void process2(TreeWalker treewalker,
                                 PrintWriter printwriter) {

        Node currentNode = treewalker.getCurrentNode();

        switch (currentNode.getNodeType()) {
        
        case Node.TEXT_NODE :
        case Node.CDATA_SECTION_NODE :
            printwriter.print(currentNode.getNodeValue());
            break;

        case Node.ENTITY_REFERENCE_NODE :
        case Node.DOCUMENT_NODE :
        case Node.ELEMENT_NODE :
        default :
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                printwriter.print('<');
                printwriter.print(currentNode.getNodeName());
                printwriter.print(">");
            }

            Node node1 = treewalker.firstChild();

            if (node1 == null) {
                System.out.println(getNodeTypeString(currentNode.getNodeType())
                                   + "_NODE parent: "
                                   + currentNode.getNodeName()
                                   + " has no children ");
            }
            else {
                System.out.println(getNodeTypeString(currentNode.getNodeType())
                                   + "_NODE parent: "
                                   + currentNode.getNodeName()
                                   + " has children ");

                while (node1 != null) {
                    {
                        String qStr = "";

                        for (Node q = node1; q != null; q = q.getParentNode()) {
                            qStr = q.getNodeName() + "/" + qStr;
                        }

                        System.out.println(getNodeTypeString(currentNode.getNodeType())
                                           + "_NODE process child " + qStr);
                    }


                    // recursion !!!
                    process2(treewalker, printwriter);

                    node1 = treewalker.nextSibling();
                    if (node1 != null) {
                        System.out.println("treewalker.nextSibling() = "
                                           + node1.getNodeName());
                    }
                } // while(node1 != null)
            }

            System.out.println("setCurrentNode() back to "
                               + currentNode.getNodeName());
            treewalker.setCurrentNode(currentNode);

            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                printwriter.print("</");
                printwriter.print(currentNode.getNodeName());
                printwriter.print(">");
            }

            break;
        }
    }

    /** Field nodeTypeString */
    private static String[] nodeTypeString = new String[]{ "", "ELEMENT",
        "ATTRIBUTE",
        "TEXT_NODE",
        "CDATA_SECTION",
        "ENTITY_REFERENCE",
        "ENTITY",
        "PROCESSING_INSTRUCTION",
        "COMMENT",
        "DOCUMENT",
        "DOCUMENT_TYPE",
        "DOCUMENT_FRAGMENT",
        "NOTATION"};



    /**
     *    
     *  Transforms <code>org.w3c.dom.Node.XXX_NODE</code> NodeType values into
     *  XXX Strings.
     * 
     *  @param nodeType as taken from the {@link org.w3c.dom.Node#getNodeType}
     * function
     *  @return the String value.
     *  @see org.w3c.dom.Node#getNodeType
     * @param nodeType
     * @return 
     */
    public static String getNodeTypeString(short nodeType) {

        if ((nodeType > 0) && (nodeType < 13)) {
            return nodeTypeString[nodeType];
        }
        else {
            return "";
        }
    }

}
