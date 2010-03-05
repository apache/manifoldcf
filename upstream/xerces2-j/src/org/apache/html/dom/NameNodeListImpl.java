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

package org.apache.html.dom;

import org.apache.xerces.dom.DeepNodeListImpl;
import org.apache.xerces.dom.ElementImpl;
import org.apache.xerces.dom.NodeImpl;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class implements the DOM's NodeList behavior for
 * HTMLDocuemnt.getElementsByName().
 * 
 * @xerces.internal
 *
 * @version $Id$
 * @since  PR-DOM-Level-1-19980818.
 * @see DeepNodeListImpl
 */
public class NameNodeListImpl 
    extends DeepNodeListImpl
    implements NodeList {
    
    
    /** Constructor. */
    public NameNodeListImpl(NodeImpl rootNode, String tagName) {
	super( rootNode, tagName );
    }  
    

    /** 
     * Iterative tree-walker. When you have a Parent link, there's often no
     * need to resort to recursion. NOTE THAT only Element nodes are matched
     * since we're specifically supporting getElementsByTagName().
     */
    protected Node nextMatchingElementAfter(Node current) {
        
        Node next;
        while (current != null) {
            // Look down to first child.
            if (current.hasChildNodes()) {
                current = (current.getFirstChild());
            }
            
            // Look right to sibling (but not from root!)
            else if (current != rootNode && null != (next = current.getNextSibling())) {
                current = next;
            }
            
            // Look up and right (but not past root!)
            else {
                next = null;
                for (; current != rootNode; // Stop when we return to starting point
                     current = current.getParentNode()) {
                    
                    next = current.getNextSibling();
                    if (next != null)
                        break;
                }
                current = next;
            }
            
            // Have we found an Element with the right tagName?
            // ("*" matches anything.)
            if (current != rootNode && current != null
                && current.getNodeType() ==  Node.ELEMENT_NODE  ) {
                String name = ((ElementImpl) current).getAttribute( "name" );
                if ( name.equals("*") || name.equals(tagName))
                    return current;
            }
            
            // Otherwise continue walking the tree
        }
        
        // Fell out of tree-walk; no more instances found
        return null;
        
    } // nextMatchingElementAfter(int):Node
    
} // class NameNodeListImpl
