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

package dom.range;

import java.io.StringReader;

import org.apache.xerces.dom.DocumentImpl;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ranges.Range;
import org.xml.sax.InputSource;

import dom.Writer;

/** 
 * This RangeTest tests all of the cases delineated as examples
 * in the DOM Level 2 Range specification, and a few others.
 * <p>These do not by any means completely test the API and 
 * corner cases.
 * 
 * @version $Id$
 */
public class Test {
    
    static final boolean DEBUG = false;
    static boolean fStdOut = false;
    
    static final String [] tests = {
        "<FOO>AB<MOO>CD</MOO>CD</FOO>",
        "<FOO>A<MOO>BC</MOO>DE</FOO>",
        "<FOO>XY<BAR>ZW</BAR>Q</FOO>",
        "<FOO><BAR1>AB</BAR1><BAR2/><BAR3>CD</BAR3></FOO>",
        "<A><B><M/><C><D/><E/><F/><HELLO/></C><N/><O/></B>"+
        "<Z><X/><Y/></Z>"+
        "<G/><Q><V/><W/></Q></A>"
    };
    static final String [] deleteResult = {
        "<FOO>ACD</FOO>",
        "<FOO>A<MOO>B</MOO>E</FOO>",
        "<FOO>X<BAR>W</BAR>Q</FOO>",
        "<FOO><BAR1>A</BAR1><BAR3>D</BAR3></FOO>",
        "<A><B><M></M><C><D></D></C></B><Q><W></W></Q></A>"
    };
    static final String [] extractResult = {
        "B<MOO>CD</MOO>",
        "<MOO>C</MOO>D",
        "Y<BAR>Z</BAR>",
        "<BAR1>B</BAR1><BAR2></BAR2><BAR3>C</BAR3>",
        "<B><C><E></E><F></F><HELLO></HELLO></C>"+
        "<N></N><O></O></B><Z><X></X><Y></Y></Z><G></G><Q><V></V></Q>"
    };
    
    static final String INSERT="***";
    static final String [] insertResult = {
"<FOO>A"+INSERT+"B<MOO>CD</MOO>CD</FOO>",
"<FOO>A<MOO>B"+INSERT+"C</MOO>DE</FOO>",
"<FOO>X"+INSERT+"Y<BAR>ZW</BAR>Q</FOO>",
"<FOO><BAR1>A"+INSERT+"B</BAR1><BAR2></BAR2><BAR3>CD</BAR3></FOO>",
"<A><B><M></M><C><D></D>"+INSERT+"<E></E><F></F><HELLO></HELLO></C>"+
"<N></N><O></O></B><Z><X></X><Y></Y></Z><G></G><Q><V></V><W></W></Q></A>"
    };
    

    static final String SURROUND="SURROUND";

    static final String [] surroundResult = {
"<FOO>A<"+SURROUND+">B<MOO>CD</MOO>C</"+SURROUND+">D</FOO>",
"<FOO>A<MOO>B<"+SURROUND+">C</"+SURROUND+"></MOO>DE</FOO>",
"<FOO>X<"+SURROUND+">Y<BAR>ZW</BAR></"+SURROUND+">Q</FOO>",
"<FOO><BAR1>AB</BAR1><"+SURROUND+"><BAR2></BAR2></"+SURROUND+"><BAR3>CD</BAR3></FOO>",
"<A><B><M></M><C><D></D><E></E><F></F><HELLO></HELLO></C>"+
"<N></N><O></O></B><Z><"+SURROUND+"><X></X><Y></Y></"+SURROUND+"></Z>"+
"<G></G><Q><V></V><W></W></Q></A>"
    };
    
    static final String [] rangeDelete = {
    "<P>Abcd efgh The Range ijkl</P>",
    "<p>Abcd efgh The Range ijkl</p>",
    "<P>ABCD efgh The <EM>Range</EM> ijkl</P>",
    "<P>Abcd efgh The Range ijkl</P>",
    "<P>Abcd <EM>efgh The Range ij</EM>kl</P>"
    };
    //
    static final String [] rangeDeleteResult = {
    "<P>Abcd ^Range ijkl</P>",
    "<p>Abcd ^kl</p>",
    "<P>ABCD ^<EM>ange</EM> ijkl</P>",
    "<P>Abcd ^he Range ijkl</P>",
    "<P>Abcd ^kl</P>"
    };
    
    static final String INSERT2="<P>Abcd efgh XY blah ijkl</P>";
    static final String INSERTED_TEXT = "INSERTED TEXT";
    
    static final String [] rangeInsertResult = {
    "<P>Abcd efgh INSERTED TEXTXY blah ijkl</P>",
    "<P>Abcd efgh XINSERTED TEXTY blah ijkl</P>",
    "<P>Abcd efgh XYINSERTED TEXT blah ijkl</P>",
    "<P>Abcd efgh XY blahINSERTED TEXT ijkl</P>"
    };
    
    
    public static void main(String args[]) {
        // is there anything to do?
        if ( args.length == 0 ) {
            printUsage();
            System.exit(1);
        }
        if (args.length > 1) {
            if (args[1].equals("yes")){
                fStdOut = true;
            }
            
            
        }
        new Test(args[0]);
    }
    
   /** Prints the usage. */
   private static void printUsage() {

      System.err.println("usage: java dom.range.Test (option) (stdout)");
      System.err.println();
      System.err.println("options:");
      System.err.println("  all             all tests");
      System.err.println("  delete          delete test");
      System.err.println("  extract         extract test");
      System.err.println("  clone           clone test");
      System.err.println("  insert          insert test");
      System.err.println("  surround        surround test");
      System.err.println("  insert2         insert mutation test");
      System.err.println("  delete2         delete mutation test");
      System.err.println("stdout:");
      System.err.println("  yes             print to standard output");
      System.err.println("  no              don't print any messages to standard output [default]");


   } // printUsage()
    
    
    public Test(String arg) {
        if (arg.equals("all")) {
            boolean all = false;
            all = performTest("delete");
            all = performTest("extract")&&all;
            all = performTest("clone")&&all;
            all = performTest("insert")&&all;
            all = performTest("surround")&&all;
            all = performTest("insert2")&&all;
            all = performTest("delete2")&&all;
            if (all) {
                System.out.println("Done.");
                
            }
            else {
                System.out.println("*** ONE OR MORE TESTS FAILED! ***");
                System.exit(1);
            }
            
        } else {
            performTest(arg);            
        }
    }
    
    public boolean performTest(String arg) {
        boolean passed = true;
        try {
            Writer writer = new Writer(false);
            DOMParser parser = new DOMParser();
            if (!arg.equals("delete2") && !arg.equals("insert2")) {
            if (fStdOut) System.out.println("\n*************** Test == "+arg+" ***************");
            for (int i = 0; i < tests.length; i++) {
                if (fStdOut) System.out.println("\n\nTest["+i+"]");
                if (fStdOut) System.out.println("\nBefore "+arg+": document="+tests[i]+":");
                parser.parse(new InputSource(new StringReader(tests[i])));
                DocumentImpl document = (DocumentImpl)parser.getDocument();
                Range range = document.createRange();
                Node root = document.getDocumentElement();
                boolean surround = false;
                Node surroundNode=document.createElement(SURROUND);
                if (arg.equals("surround")) {
                    surround = true;
                }
                
                if (i == 0) { 
                    range.setStart(root.getFirstChild(), 1);
                    range.setEndBefore(root.getLastChild());
                    if (surround)
                        range.setEnd(root.getLastChild(),1);
                    
                }
                else if (i == 1) {
                    Node n1 = root.getFirstChild().getNextSibling().
                    getFirstChild();
                    range.setStart(n1, 1);
                    range.setEnd(root.getLastChild(), 1);
                    if (surround)
                        range.setEnd(n1,2);
                }
                else if (i == 2) {
                    range.setStart(root.getFirstChild(), 1);
                    Node n2 = root.getFirstChild().getNextSibling().getFirstChild();
                    range.setEnd(n2, 1);
                    if (surround)
                        range.setEndBefore(root.getLastChild());
                }
                else if (i == 3) {
                    Node n3 = root.getFirstChild().getFirstChild();
                    range.setStart(n3, 1);
                    range.setEnd(root.getLastChild().getFirstChild(), 1);
                    if (surround) {
                        range.selectNode(root.getFirstChild().getNextSibling());
                    }
                }
                else if (i == 4) {
                    Node n4 = root.getFirstChild().getFirstChild().getNextSibling().getFirstChild();
                    range.setStartAfter(n4);
                    range.setEndAfter(root.getLastChild().getFirstChild());
                    if (surround) {
                        range.selectNodeContents(root.getFirstChild().getNextSibling());
                    }
                }
                
                if (fStdOut) System.out.println("range.toString="+range.toString());
                DocumentFragment frag = null;
                
                if (arg.equals("surround")) {
                    try {
                        if (fStdOut) System.out.println("surroundNode="+surroundNode);
                        range.surroundContents(surroundNode);
                    } catch (org.w3c.dom.ranges.RangeException e) {
                        if (fStdOut) System.out.println(e);
                    }
                   String result = toString(document);
                   if (fStdOut) System.out.println("After surround: document="+result+":");
                   if (!result.equals(surroundResult[i])) {
                        if (fStdOut) System.out.println("Should be: document="+surroundResult[i]+":");
                        passed = false;
                        if (fStdOut) System.out.println("Test FAILED!");
                        if (fStdOut) System.out.println("*** Surround document Test["+i+"] FAILED!");
                   }
                }
                
                if (arg.equals("insert")) {
                    range.insertNode(document.createTextNode(INSERT));
                   String result = toString(document);
                   if (fStdOut) System.out.println("After  insert: document="+result+":");
                   if (!result.equals(insertResult[i])) {
                        if (fStdOut) System.out.println("Should be: document="+insertResult[i]+":");
                        passed = false;
                        if (fStdOut) System.out.println("Test FAILED!");
                        if (fStdOut) System.out.println("*** Insert document Test["+i+"] FAILED!");
                   }
                    
                } else 
                if (arg.equals("delete")) {
                   range.deleteContents();
                   String result = toString(document);
                   if (fStdOut) System.out.println("After delete:"+result+":");
                   if (!result.equals(deleteResult[i])) {
                        if (fStdOut) System.out.println("Should be: document="+deleteResult[i]+":");
                        passed = false;
                        if (fStdOut) System.out.println("Test FAILED!");
                        if (fStdOut) System.out.println("*** Delete document Test["+i+"] FAILED!");
                   }
                }
                else 
                if (arg.equals("extract")) {
                    frag = range.extractContents();
                    //range.insertNode(document.createTextNode("^"));
                   String result = toString(document);
                   if (fStdOut) System.out.println("After extract: document="+result+":");
                   if (!result.equals(deleteResult[i])) {
                        if (fStdOut) System.out.println("Should be: document="+deleteResult[i]+":");
                        passed = false;
                        if (fStdOut) System.out.println("*** Extract document Test["+i+"] FAILED!");
                   }
                   String fragResult = toString(frag);
                   if (fStdOut) System.out.println("After extract: fragment="+fragResult+":");
                   if (!fragResult.equals(extractResult[i])) {
                        if (fStdOut) System.out.println("Should be: fragment="+extractResult[i]+":");
                        passed = false;
                        if (fStdOut) System.out.println("*** Extract Fragment Test["+i+"] FAILED!");
                   }
                }
                   
                else 
                if (arg.equals("clone")) {
                    frag = range.cloneContents();
                   String fragResult = toString(frag);
                   if (fStdOut) System.out.println("After clone: fragment="+fragResult);
                   if (!fragResult.equals(extractResult[i])) {
                        if (fStdOut) System.out.println("Should be: fragment="+extractResult[i]+":");
                        passed = false;
                        if (fStdOut) System.out.println("*** Clone Fragment Test["+i+"] FAILED!");
                   }
                }
                
            }
            
            } else
            if (arg.equals("insert2")) {
            if (fStdOut) System.out.println("\n*************** Test == "+arg+" ***************");
            for (int i = 0; i < 4; i++) {

                if (fStdOut) System.out.println("\n\nTest["+i+"]");
                if (fStdOut) System.out.println("\nBefore "+arg+": document="+INSERT2+":");
                parser.parse(new InputSource(new StringReader(INSERT2)));
                DocumentImpl document = (DocumentImpl)parser.getDocument();
                Node root = document.getDocumentElement();
                Range range = document.createRange();
                range.setStart(root.getFirstChild(),11);
                range.setEnd(root.getFirstChild(),18);
                Range rangei = document.createRange();
                if (i == 0) { 
                    rangei.setStart(root.getFirstChild(), 10);
                    rangei.setEnd(root.getFirstChild(), 10);
                }
                if (i == 1) { 
                    rangei.setStart(root.getFirstChild(), 11);
                    rangei.setEnd(root.getFirstChild(), 11);
                }
                if (i == 2) { 
                    rangei.setStart(root.getFirstChild(), 12);
                    rangei.setEnd(root.getFirstChild(), 12);
                }
                if (i == 3) { 
                    rangei.setStart(root.getFirstChild(), 17);
                    rangei.setEnd(root.getFirstChild(), 17);
                }
                //if (fStdOut) System.out.println("range: start1=="+range.getStartContainer());
                //root.insertBefore(document.createTextNode("YES!"), root.getFirstChild());
                //if (fStdOut) System.out.println("range: start2=="+range.getStartContainer());
   
                if (DEBUG) if (fStdOut) System.out.println("before insert start="+range.getStartOffset());
                if (DEBUG) if (fStdOut) System.out.println("before insert end="+range.getEndOffset());
                rangei.insertNode(document.createTextNode(INSERTED_TEXT));
                if (DEBUG) if (fStdOut) System.out.println("after insert start="+range.getStartOffset());
                if (DEBUG) if (fStdOut) System.out.println("after insert end="+range.getEndOffset());
                
                String result = toString(document);
                if (fStdOut) System.out.println("After insert2: document="+result+":");
                if (!result.equals(rangeInsertResult[i])) {
                    if (fStdOut) System.out.println("Should be: document="+rangeInsertResult[i]+":");
                    passed = false;
                    if (fStdOut) System.out.println("Test FAILED!");
                    if (fStdOut) System.out.println("*** Delete Ranges document Test["+i+"] FAILED!");
                }
            }
            } else
            if (arg.equals("delete2")) {
            //
            // Range Deletion, acting upon another range.
            //
       
            if (fStdOut) System.out.println("\n*************** Test == "+arg+" ***************");
            for (int i = 0; i < rangeDelete.length; i++) {
                if (fStdOut) System.out.println("\n\nTest["+i+"]");
                if (fStdOut) System.out.println("\nBefore "+arg+": document="+rangeDelete[i]+":");
                parser.parse(new InputSource(new StringReader(rangeDelete[i])));
                DocumentImpl document = (DocumentImpl)parser.getDocument();
                Range range = document.createRange();
                Range ranged = document.createRange();
                Node root = document.getDocumentElement();
                boolean surround = false;
                Node surroundNode=document.createElement(SURROUND);
                if (arg.equals("surround")) {
                    surround = true;
                }
                
                if (i == 0) { 
                    ranged.setStart(root.getFirstChild(), 5);
                    ranged.setEnd(root.getFirstChild(), 14);
                    
                    range.setStart(root.getFirstChild(), 11);
                    range.setEnd(root.getFirstChild(), 19);
                }
                else if (i == 1) {
                    ranged.setStart(root.getFirstChild(), 5);
                    ranged.setEnd(root.getFirstChild(), 22);
                    
                    range.setStart(root.getFirstChild(), 11);
                    range.setEnd(root.getFirstChild(), 21);
                }
                else if (i == 2) {
                    ranged.setStart(root.getFirstChild(), 5);
                    ranged.setEnd(root.getFirstChild().getNextSibling()
                        .getFirstChild(), 1);
                        
                    range.setStart(root.getFirstChild(), 11);
                    
                    range.setEndAfter(root.getFirstChild().getNextSibling()
                        .getFirstChild());
                }
                else if (i == 3) {
                    ranged.setStart(root.getFirstChild(), 5);
                    ranged.setEnd(root.getFirstChild(), 11);
                    
                    range.setStart(root.getFirstChild(), 11);
                    range.setEnd(root.getFirstChild(), 21);
                }
                else if (i == 4) {
                    ranged.selectNode(root.getFirstChild().getNextSibling());
                    
                    range.setStart(root.getFirstChild().getNextSibling()
                        .getFirstChild(), 6);
                    range.setEnd(root.getFirstChild().getNextSibling()
                        .getFirstChild(), 15);
                }
                
                DocumentFragment frag = null;
                
                if (arg.equals("delete2")) {
                    if (DEBUG) {
                   if (fStdOut) System.out.println("BEFORE deleteContents()");
                   if (fStdOut) System.out.println("ranged: startc="+ranged.getStartContainer());
                   if (fStdOut) System.out.println("ranged: starto="+ranged.getStartOffset());
                   if (fStdOut) System.out.println("ranged:   endc="+ranged.getEndContainer());
                   if (fStdOut) System.out.println("ranged:   endo="+ranged.getEndOffset());
             
                   if (fStdOut) System.out.println("range: startc="+range.getStartContainer());
                   if (fStdOut) System.out.println("range: starto="+range.getStartOffset());
                   if (fStdOut) System.out.println("range:   endc="+range.getEndContainer());
                   if (fStdOut) System.out.println("range:   endo="+range.getEndOffset());
                    }
                   ranged.deleteContents();
                   String result = null;
                   if (DEBUG) {
                   if (fStdOut) System.out.println("AFTER deleteContents()");
                   result = toString(document);
                   if (fStdOut) System.out.println("ranged: startc="+ranged.getStartContainer());
                   if (fStdOut) System.out.println("ranged: starto="+ranged.getStartOffset());
                   if (fStdOut) System.out.println("ranged:   endc="+ranged.getEndContainer());
                   if (fStdOut) System.out.println("ranged:   endo="+ranged.getEndOffset());
             
                   if (fStdOut) System.out.println("range: startc="+range.getStartContainer());
                   if (fStdOut) System.out.println("range: starto="+range.getStartOffset());
                   if (fStdOut) System.out.println("range:   endc="+range.getEndContainer());
                   if (fStdOut) System.out.println("range:   endo="+range.getEndOffset());
                   }
                   
                   ranged.insertNode(document.createTextNode("^"));
                   
                   result = toString(document);
                   if (fStdOut) System.out.println("After delete2: document="+result+":");
                   if (!result.equals(rangeDeleteResult[i])) {
                        if (fStdOut) System.out.println("Should be: document="+rangeDeleteResult[i]+":");
                        passed = false;
                        if (fStdOut) System.out.println("Test FAILED!");
                        if (fStdOut) System.out.println("*** Delete Ranges document Test["+i+"] FAILED!");
                   }
                }
            }
                
            }
            
       
        }
        catch (org.xml.sax.SAXParseException spe) {
            passed = false;
        }
        catch (org.xml.sax.SAXException se) {
            if (se.getException() != null)
                se.getException().printStackTrace(System.err);
            else
                se.printStackTrace(System.err);
            passed = false;
        }
        catch (Exception e) {
            e.printStackTrace(System.err);
            passed = false;
        }
        if (!passed) if (fStdOut) System.out.println("*** The "+arg+" Test FAILED! ***");
        
        return passed;
    }
    StringBuffer sb;
    boolean canonical = true;
    
    String toString(Node node) {
        sb = new StringBuffer();
        return print(node);
    
    }
   
   /** Prints the specified node, recursively. */
   public String print(Node node) {

      // is there anything to do?
      if ( node == null ) {
         return sb.toString();
      }

      int type = node.getNodeType();
      switch ( type ) {
         // print document
         case Node.DOCUMENT_NODE: {
               return print(((Document)node).getDocumentElement());
               //out.flush();
               //break;
            }

            // print element with attributes
         case Node.ELEMENT_NODE: {
               sb.append('<');
               sb.append(node.getNodeName());
               Attr attrs[] = sortAttributes(node.getAttributes());
               for ( int i = 0; i < attrs.length; i++ ) {
                  Attr attr = attrs[i];
                  sb.append(' ');
                  sb.append(attr.getNodeName());
                  sb.append("=\"");
                  sb.append(normalize(attr.getNodeValue()));
                  sb.append('"');
               }
               sb.append('>');
               NodeList children = node.getChildNodes();
               if ( children != null ) {
                  int len = children.getLength();
                  for ( int i = 0; i < len; i++ ) {
                     print(children.item(i));
                  }
               }
               break;
            }

            // handle entity reference nodes
         case Node.ENTITY_REFERENCE_NODE: {
               if ( canonical ) {
                  NodeList children = node.getChildNodes();
                  if ( children != null ) {
                     int len = children.getLength();
                     for ( int i = 0; i < len; i++ ) {
                        print(children.item(i));
                     }
                  }
               } else {
                  sb.append('&');
                  sb.append(node.getNodeName());
                  sb.append(';');
               }
               break;
            }

            // print cdata sections
         case Node.CDATA_SECTION_NODE: {
               if ( canonical ) {
                  sb.append(normalize(node.getNodeValue()));
               } else {
                  sb.append("<![CDATA[");
                  sb.append(node.getNodeValue());
                  sb.append("]]>");
               }
               break;
            }

            // print text
         case Node.TEXT_NODE: {
               sb.append(normalize(node.getNodeValue()));
               break;
            }

            // print processing instruction
         case Node.PROCESSING_INSTRUCTION_NODE: {
               sb.append("<?");
               sb.append(node.getNodeName());
               String data = node.getNodeValue();
               if ( data != null && data.length() > 0 ) {
                  sb.append(' ');
                  sb.append(data);
               }
               sb.append("?>");
               break;
            }
            // handle entity reference nodes
         case Node.DOCUMENT_FRAGMENT_NODE: {
            NodeList children = node.getChildNodes();
            if ( children != null ) {
                int len = children.getLength();
                for ( int i = 0; i < len; i++ ) {
                     print(children.item(i));
                }
            }
               break;
            }
      }

      if ( type == Node.ELEMENT_NODE ) {
         sb.append("</");
         sb.append(node.getNodeName());
         sb.append('>');
      }

      return sb.toString();

   } // print(Node)

   /** Returns a sorted list of attributes. */
   protected Attr[] sortAttributes(NamedNodeMap attrs) {

      int len = (attrs != null) ? attrs.getLength() : 0;
      Attr array[] = new Attr[len];
      for ( int i = 0; i < len; i++ ) {
         array[i] = (Attr)attrs.item(i);
      }
      for ( int i = 0; i < len - 1; i++ ) {
         String name  = array[i].getNodeName();
         int    index = i;
         for ( int j = i + 1; j < len; j++ ) {
            String curName = array[j].getNodeName();
            if ( curName.compareTo(name) < 0 ) {
               name  = curName;
               index = j;
            }
         }
         if ( index != i ) {
            Attr temp    = array[i];
            array[i]     = array[index];
            array[index] = temp;
         }
      }

      return (array);

   } // sortAttributes(NamedNodeMap):Attr[]
    
   /** Normalizes the given string. */
   protected String normalize(String s) {
      StringBuffer str = new StringBuffer();

      int len = (s != null) ? s.length() : 0;
      for ( int i = 0; i < len; i++ ) {
         char ch = s.charAt(i);
         switch ( ch ) {
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
            case '\n': {
                  if ( canonical ) {
                     str.append("&#");
                     str.append(Integer.toString(ch));
                     str.append(';');
                     break;
                  }
                  // else, default append char
               }
            default: {
                  str.append(ch);
               }
         }
      }

      return (str.toString());

   } // normalize(String):String
   
    
}
