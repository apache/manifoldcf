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
import javax.swing.tree.TreeNode;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Notation;

/**
 *  DOMTree class to enter every DOM node into a Swing JTree tree.
 *  The forward and backward mappings between Nodes to TreeNodes are kept.
 *
 * @version $Id$
 */
public class DOMTreeFull
    extends JTree 
    {
    
    private static final long serialVersionUID = 3978144335541975344L;

    //
    // Constructors
    //

    /** Default constructor. */
    public DOMTreeFull() {
        this(null);
        }

    /** Constructs a tree with the specified document. */
    public DOMTreeFull(Node root) {
        super(new Model());

        // set tree properties
        setRootVisible(false);

        // set properties
        setRootNode(root);

        } // <init>()

    //
    // Public methods
    //

    /** Sets the root. */
    public void setRootNode(Node root) {
        ((Model)getModel()).setRootNode(root);
        expandRow(0);
        }

    /** Returns the root. */
    public Node getRootNode() {
        return ((Model)getModel()).getRootNode();
        }

    /** get the org.w3c.Node for a MutableTreeNode. */
    public Node getNode(Object treeNode) {
        return ((Model)getModel()).getNode(treeNode);
    }

    /** get the TreeNode for the org.w3c.Node */
    public TreeNode getTreeNode(Object node) {
        return ((Model)getModel()).getTreeNode(node);
    }

    //
    // Classes
    //

    /**
     * DOM tree model.
     *
     */
    public static class Model 
        extends DefaultTreeModel
        implements Serializable
        {
        
        private static final long serialVersionUID = 3258131375181018673L;

        //
        // Data
        //

        /** Root. */
        private Node root;
        /** Node Map. */
        private Hashtable nodeMap = new Hashtable();
        /** Tree Node Map. */
        private Hashtable treeNodeMap = new Hashtable();        

        //
        // Constructors
        //

        /** Default constructor. */
        public Model() {
            this(null);
            }

        /** Constructs a model from the specified root. */
        public Model(Node node) {
            super(new DefaultMutableTreeNode());
            if (node!=null)
            setRootNode(node);
            }

        //
        // Public methods
        //

        /** Sets the root. */
        public synchronized void setRootNode(Node root) {
            
            // save root
            this.root = root;

            // clear tree and re-populate
            DefaultMutableTreeNode where = (DefaultMutableTreeNode)getRoot();
            where.removeAllChildren();
            nodeMap.clear();
            treeNodeMap.clear();

            buildTree(root, where);
            
            fireTreeStructureChanged(this, new Object[] { getRoot() }, new int[0], new Object[0]);

            } // setRootNode(Node)

        /** Returns the root. */
        public Node getRootNode() {
            return root;
            }

        /** get the org.w3c.Node for a MutableTreeNode. */
        public Node getNode(Object treeNode) {
            return (Node)nodeMap.get(treeNode);
        }
        public Hashtable getAllNodes() {
            return nodeMap;
        }
        /** get the org.w3c.Node for a MutableTreeNode. */
        public TreeNode getTreeNode(Object node) {
            Object object = treeNodeMap.get(node);
            return (TreeNode)object;
        }

        //
        // Private methods
        //

        /** Builds the tree. */
        private void buildTree(Node node, MutableTreeNode where) {
            
            // is there anything to do?
            if (node == null) { return; }

            MutableTreeNode treeNode = insertNode(node, where);

            // iterate over children of this node
            NodeList nodes = node.getChildNodes();
            int len = (nodes != null) ? nodes.getLength() : 0;
            for (int i = 0; i < len; i++) {
                Node child = nodes.item(i);
                buildTree(child, treeNode);

            }


        } // buildTree()

        /** Inserts a node and returns a reference to the new node. */
        private MutableTreeNode insertNode(String what, MutableTreeNode where) {

            MutableTreeNode node = new DefaultMutableTreeNode(what);
            insertNodeInto(node, where, where.getChildCount());
            return node;

            } // insertNode(Node,MutableTreeNode):MutableTreeNode
            

        /** Inserts a text node. */
        public MutableTreeNode insertNode(Node what, MutableTreeNode where) {
                MutableTreeNode treeNode = insertNode(DOMTreeFull.toString(what), where);
                nodeMap.put(treeNode, what); 
                treeNodeMap.put(what, treeNode);
                return treeNode;
            }
        } // class Model
        
        public static String whatArray[] = new String [] { 
                "ALL",
                "ELEMENT",
                "ATTRIBUTE",
                "TEXT",
                "CDATA_SECTION",
                "ENTITY_REFERENCE",
                "ENTITY",
                "PROCESSING_INSTRUCTION", 
                "COMMENT", 
                "DOCUMENT", 
                "DOCUMENT_TYPE",
                "DOCUMENT_FRAGMENT",
                "NOTATION" 
                };
        //
        // Public methods
        //
                
        public static String toString(Node node) {
            StringBuffer sb = new StringBuffer();
                
            // is there anything to do?
            if (node == null) {
                return "";
            }

            int type = node.getNodeType();
            sb.append(whatArray[type]);
            sb.append(" : ");
            sb.append(node.getNodeName());
            String value = node.getNodeValue();
            if (value != null) {
                sb.append(" Value: \"");
                sb.append(value);
                sb.append("\"");
            }
                    
            switch (type) {
                    
                // document
                case Node.DOCUMENT_NODE: {
                    break;
                }

                // element with attributes
                case Node.ELEMENT_NODE: {
                    Attr attrs[] = sortAttributes(node.getAttributes());
                    if (attrs.length > 0)
                        sb.append(" ATTRS:");
                    for (int i = 0; i < attrs.length; i++) {
                        Attr attr = attrs[i];
                            
                        sb.append(' ');
                        sb.append(attr.getNodeName());
                        sb.append("=\"");
                        sb.append(normalize(attr.getNodeValue()));
                        sb.append('"');
                    }
                    sb.append('>');
                    break;
                }

                // handle entity reference nodes
                case Node.ENTITY_REFERENCE_NODE: {
                    break;
                }

                // cdata sections
                case Node.CDATA_SECTION_NODE: {
                    break;
                }

                // text
                case Node.TEXT_NODE: {
                    break;
                }

                // processing instruction
                case Node.PROCESSING_INSTRUCTION_NODE: {
                    break;
                }
                    
                // comment node 
                case Node.COMMENT_NODE: {
                    break;
                }
                // DOCTYPE node
                case Node.DOCUMENT_TYPE_NODE: {
                    break;
                }
                // Notation node
                case Node.NOTATION_NODE: {
                        sb.append("public:");
                        String id = ((Notation)node).getPublicId();
                        if (id == null) {
                            sb.append("PUBLIC ");
                            sb.append(id);
                            sb.append(" ");
                        }
                        id = ((Notation)node).getSystemId();
                        if (id == null) {
                            sb.append("system: ");
                            sb.append(id);
                            sb.append(" ");
                        }
                    break;
                }
            }
            return sb.toString();
        }
            
        /** Normalizes the given string. */
        static protected String normalize(String s) {
            StringBuffer str = new StringBuffer();

            int len = (s != null) ? s.length() : 0;
            for (int i = 0; i < len; i++) {
                char ch = s.charAt(i);
                switch (ch) {
                    case '<': {
                        str.append("&lt;");
                        break;
                    }
                    case '>': {
                        str.append("&gt;");
                        break;
                    }
                    case '&': {
                        str.append("&amp;");
                        break;
                    }
                    case '"': {
                        str.append("&quot;");
                        break;
                    }
                    case '\r':
                    case '\n': 
                    default: {
                        str.append(ch);
                    }
                }
            }

            return str.toString();

        } // normalize(String):String

        /** Returns a sorted list of attributes. */
        static protected Attr[] sortAttributes(NamedNodeMap attrs) {

            int len = (attrs != null) ? attrs.getLength() : 0;
            Attr array[] = new Attr[len];
            for (int i = 0; i < len; i++) {
                array[i] = (Attr)attrs.item(i);
            }
            for (int i = 0; i < len - 1; i++) {
                String name  = array[i].getNodeName();
                int    index = i;
                for (int j = i + 1; j < len; j++) {
                    String curName = array[j].getNodeName();
                    if (curName.compareTo(name) < 0) {
                        name  = curName;
                        index = j;
                    }
                }
                if (index != i) {
                    Attr temp    = array[i];
                    array[i]     = array[index];
                    array[index] = temp;
                }
            }

            return array;

        } // sortAttributes(NamedNodeMap):Attr[]        

    } // class DOMTreeFull

