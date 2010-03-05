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

package ui;

import java.io.Serializable;
import java.util.Hashtable;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Displays a DOM document in a tree control.
 *
 * @author  Andy Clark, IBM
 * @version $Id$
 */
public class DOMTree
    extends JTree 
    {
    
    private static final long serialVersionUID = 3977582510937224497L;

    //
    // Constructors
    //

    /** Default constructor. */
    public DOMTree() {
        this(null);
        }

    /** Constructs a tree with the specified document. */
    public DOMTree(Document document) {
        super(new Model());

        // set tree properties
        setRootVisible(false);

        // set properties
        setDocument(document);

        } // <init>()

    //
    // Public methods
    //

    /** Sets the document. */
    public void setDocument(Document document) {
        ((Model)getModel()).setDocument(document);
        expandRow(0);
        }

    /** Returns the document. */
    public Document getDocument() {
        return ((Model)getModel()).getDocument();
        }

    /** get the org.w3c.Node for a MutableTreeNode. */
    public Node getNode(Object treeNode) {
        return ((Model)getModel()).getNode(treeNode);
    }

    //
    // Classes
    //

    /**
     * DOM tree model.
     *
     * @author  Andy Clark, IBM
     * @version
     */
    static class Model 
        extends DefaultTreeModel
        implements Serializable
        {
        
        private static final long serialVersionUID = 3257286915924571186L;

        //
        // Data
        //

        /** Document. */
        private Document document;
        /** Node Map. */
        private Hashtable nodeMap = new Hashtable();
        

        //
        // Constructors
        //

        /** Default constructor. */
        public Model() {
            this(null);
            }

        /** Constructs a model from the specified document. */
        public Model(Document document) {
            super(new DefaultMutableTreeNode());
            setDocument(document);
            }

        //
        // Public methods
        //

        /** Sets the document. */
        public synchronized void setDocument(Document document) {

            // save document
            this.document = document;

            // clear tree and re-populate
            ((DefaultMutableTreeNode)getRoot()).removeAllChildren();
            nodeMap.clear();
            buildTree();
            fireTreeStructureChanged(this, new Object[] { getRoot() }, new int[0], new Object[0]);

            } // setDocument(Document)

        /** Returns the document. */
        public Document getDocument() {
            return document;
            }

        /** get the org.w3c.Node for a MutableTreeNode. */
        public Node getNode(Object treeNode) {
            return (Node)nodeMap.get(treeNode);
        }

        //
        // Private methods
        //

        /** Builds the tree. */
        private void buildTree() {
            
            // is there anything to do?
            if (document == null) { return; }

            // iterate over children of this node
            NodeList nodes = document.getChildNodes();
            int len = (nodes != null) ? nodes.getLength() : 0;
            MutableTreeNode root = (MutableTreeNode)getRoot();
            for (int i = 0; i < len; i++) {
                Node node = nodes.item(i);
                switch (node.getNodeType()) {
                    case Node.DOCUMENT_NODE: {
                        root = insertDocumentNode(node, root);
                        break;
                        }

                    case Node.ELEMENT_NODE: {
                        insertElementNode(node, root);
                        break;
                        }

                    default: // ignore

                    } // switch

                } // for 

            } // buildTree()

        /** Inserts a node and returns a reference to the new node. */
        private MutableTreeNode insertNode(String what, MutableTreeNode where) {

            MutableTreeNode node = new DefaultMutableTreeNode(what);
            insertNodeInto(node, where, where.getChildCount());
            return node;

            } // insertNode(Node,MutableTreeNode):MutableTreeNode
            
        /** Inserts the document node. */
        private MutableTreeNode insertDocumentNode(Node what, MutableTreeNode where) {
            MutableTreeNode treeNode = insertNode("<"+what.getNodeName()+'>', where);
            nodeMap.put(treeNode, what);
            return treeNode;
            }

        /** Inserts an element node. */
        private MutableTreeNode insertElementNode(Node what, MutableTreeNode where) {

            // build up name
            StringBuffer name = new StringBuffer();
            name.append('<');
            name.append(what.getNodeName());
            NamedNodeMap attrs = what.getAttributes();
            int attrCount = (attrs != null) ? attrs.getLength() : 0;
            for (int i = 0; i < attrCount; i++) {
                Node attr = attrs.item(i);
                name.append(' ');
                name.append(attr.getNodeName());
                name.append("=\"");
                name.append(attr.getNodeValue());
                name.append('"');
                }
            name.append('>');

            // insert element node
            
            MutableTreeNode element = insertNode(name.toString(), where);
            nodeMap.put(element, what);
            
            // gather up attributes and children nodes
            NodeList children = what.getChildNodes();
            int len = (children != null) ? children.getLength() : 0;
            for (int i = 0; i < len; i++) {
                Node node = children.item(i);
                switch (node.getNodeType()) {
                    case Node.CDATA_SECTION_NODE: { 
                       insertCDataSectionNode( node, element ); //Add a Section Node
                       break;
                      }
                    case Node.TEXT_NODE: {
                        insertTextNode(node, element);
                        break;
                        }
                    case Node.ELEMENT_NODE: {
                        insertElementNode(node, element);
                        break;
                        }
                    }
                }

            return element;

            } // insertElementNode(Node,MutableTreeNode):MutableTreeNode

        /** Inserts a text node. */
        private MutableTreeNode insertTextNode(Node what, MutableTreeNode where) {
            String value = what.getNodeValue().trim();
            if (value.length() > 0) {
                MutableTreeNode treeNode = insertNode(value, where);
                nodeMap.put(treeNode, what);            
                return treeNode;
                }
            return null;
            }

        
      /** Inserts a CData Section Node. */
      private MutableTreeNode insertCDataSectionNode(Node what, MutableTreeNode where) {
         StringBuffer CSectionBfr = new StringBuffer();         
         //--- optional --- CSectionBfr.append( "<![CDATA[" );
         CSectionBfr.append( what.getNodeValue() );
         //--- optional --- CSectionBfr.append( "]]>" );
         if (CSectionBfr.length() > 0) {
            MutableTreeNode treeNode = insertNode(CSectionBfr.toString(), where);
            nodeMap.put(treeNode, what);            
            return treeNode;
            }
         return null;
         }


      } // class Model



    } // class DOMTree
