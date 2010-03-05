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
package dom.traversal;


import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeFilter;

 /** An example filter which enables the client to set a <b>name</b> value 
  *  accept those node names which <b>match</b> (or explicitly <b>not match</b>) 
  *  the set name value.
  */
 public class NameNodeFilter implements NodeFilter {
    
    String fName;
    boolean fMatch = true;
            
        /** The name to compare with the node name. If null, all node names  
         *  are successfully matched. 
         */
        public void setName(String name) {
            this.fName = name;
        }
        
        /** Return the name to compare with node name. If null, all node names  
         *  are successfully matched. */
        public String getName() {
            return this.fName;
        }
        
        /** 
         *  Controls whether the node name is accepted when it <b>does</b> match 
         *  the setName value, or when it <b>does not</b> match the setName value. 
         *  If the setName value is null this match value does not matter, and
         *  all names will match.
         *  If match is true, the node name is accepted when it matches. 
         *  If match is false, the node name is accepted when does not match. 
         */
        public void setMatch(boolean match) {
            this.fMatch = match;
        }
        
        /** Return match value. */
        public boolean getMatch() {
            return this.fMatch;
        }
        
        /** acceptNode determines if this filter accepts a node name or not. */ 
        public short acceptNode(Node n) {

            if (fName == null || fMatch && n.getNodeName().equals(fName) 
            ||  !fMatch && !n.getNodeName().equals(fName))
                return FILTER_ACCEPT;
            else 
                return FILTER_REJECT;
        }
    }
