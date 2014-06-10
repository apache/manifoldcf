/* $Id: XMLDoc.java 988245 2010-08-23 18:39:35Z kwright $ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.manifoldcf.core.common;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.w3c.dom.*;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.system.Logging;

public class XMLDoc
{
  public static final String _rcsid = "@(#)$Id: XMLDoc.java 988245 2010-08-23 18:39:35Z kwright $";

  private static final String _wildchar = "*";
  private static final String _slash = "/";
  private static int _blocksiz = 1024;
  private Document _doc = null;                   // parsed xml doc tree

  /** Return the document root; may be null
  * @return document object
  */
  protected Object getDocument()                  {return _doc;}
  protected void setDocument(Object d)    {_doc = (Document)d;}

  /* Path is form of root/node/node...      and data
  * is returned from bottom most node specified.
  *
  * Worker function to process some simple wildcards in
  * a specified xpath-like string

  * THIS IS THE ONLY WAY TO GET THE XML NODES FROM THE DOC
  * IE SPECIFING A PATH

  * NOTE wildcards are supported BUT the evaluation of wildcards
  * is not recursive.  IE if the path presented is THIS/[wildchar]/THAT
  * only ONE list of element is returned for the first child of THIS.
  * All children of THIS that have subchildren THAT are NOT returned!
  *
  * @param xnode like path
  * @param start node
  */
  public ArrayList processPath(String path, Object o)
  {
    ArrayList l = new ArrayList();
    processPath(l, path, o);
    return l;
  }

  public void processPath(ArrayList returnList, String path,
    Object currentRoot)
  {

    Object element = currentRoot;
    StringBuilder bf = new StringBuilder();
    boolean bWild = false;
    ArrayList working = new ArrayList();

    if (path.endsWith(_slash))
    {
      path += _wildchar;
    }

    StringTokenizer tokenizer = new StringTokenizer(path, _slash, false);

    int depth=0, pathDepth = tokenizer.countTokens();
    String attribute=null, value=null;

    while(tokenizer.hasMoreTokens())
    {
      depth++;

      // Tokenizer returns true always at least
      // once, so watch out for dead string
      String s = tokenizer.nextToken().trim();
      if (s != null && s.length() > 0)
      {
        String elementName;
        attribute = value =null;

        s = s.trim();

        // Check for "pathelement qualifier" in
        // each term, for example a path could be
        // "root/user name=Fred"  meaning find the
        // the user element where attribute name==Fred.
        // This extension is fixed and immutable and is
        // not well error checked
        if (s.indexOf('=') > -1)
        {
          // Any "wildcards" are recorded.
          bWild = true;

          // Well known form
          int i = s.indexOf(' ');
          elementName = s.substring(0, i);
          s = s.substring(i).trim();

          i = s.indexOf('=');
          attribute = s.substring(0, i);
          value = s.substring(i+1);

          bf.append('/').append(elementName).append(attribute);
          bf.append('=').append(value);
        }
        else
        {
          elementName = s;
          if (elementName.equals(_wildchar))
          {
            elementName = null; // find anything
          }
          else
          {
            bf.append("/").append(s);
          }
        }

        // Finding specific instance??
        ArrayList l = getElements(element, elementName);
        element = null; // forget path to this point

        // If depth==pathDepth, just save the final arraylist
        if (depth==pathDepth)
        {
          working.addAll(l);
        }
        else
        {
          int i = searchArrayForAttribute(l, 0, attribute, value);
          if (i != -1)
          {
            element = l.get(i);
          }
        }

        if (element==null)
        {
          break;  //!
        }
      }
    }


    // UGH - so, what we do here is take the list
    // and prune out stuff that doesn't match
    if (bWild)
    {
      for (int i = 0; i < working.size(); i++)
      {
        int j = searchArrayForAttribute(working, i, attribute, value);
        if (j > -1)
        {
          // Add a simple XML element (node)
          // to the list
          returnList.add(working.get(j));
        }
        else
        {
          // no more matching nodes
          break;
        }
      }
    }
    else
    {
      // It's everything, but it is in simple
      // XML element (node) form.
      returnList.addAll(working);
    }
  }

  /** Having collected an arraylist from a given
  * depth in the tree, scan the node for the current
  * attribute specified (part of wildcard matching
  * of xpath-like element specification)
  *
  * @param l list of elements found
  * @param i starting index
  * @param attribute String to find
  * @param value String attribute value to match
  */
  protected int searchArrayForAttribute(ArrayList l, int i, String attribute, String value)
  {
    int index = -1;

    for (; i < l.size(); i++)
    {
      Object element = l.get(i);

      if (attribute == null || attribute.length() == 0)
      {
        index = i;
        break;  // nothing special, first one
      }
      else if (value.equals(getValue(element, attribute)))
      {
        index = i;
        break;
      }
    }

    return index;
  }

  /** Serialize the document object to a safe string
  * @return xml raw text
  */
  public String getXML()
    throws ManifoldCFException
  {
    return new String(toByteArray(), StandardCharsets.UTF_8);

  }

  /** Get XML with no entity preamble */
  public String getXMLNoEntityPreamble()
    throws ManifoldCFException
  {
    String initial = getXML();
    int index = initial.indexOf(">");
    return initial.substring(index+1);
  }
  
  /** Convert the response for transmit
  * @return xml in byte array
  */
  public byte[] toByteArray()
    throws ManifoldCFException
  {
    ByteArrayOutputStream os = new ByteArrayOutputStream(_blocksiz);
    dumpOutput(os);
    return os.toByteArray();
  }


  /** Creates the empty doc
  */
  public XMLDoc()
    throws ManifoldCFException
  {
    try
    {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setEntityResolver(new MyEntityResolver());
      _doc = builder.newDocument();
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Error setting up parser: "+e.getMessage(),e);
    }
  }

  /** Construct a new document tree from a string form of
  * an xml document
  * @param data xml to parse
  */
  public XMLDoc(String data)
    throws ManifoldCFException
  {
      ByteArrayInputStream bis = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
      _doc = init(bis);


  }

  /** Construct a new document tree from a StringBuilder form of
  * an xml document
  * @param data xml to parse
  */
  public XMLDoc(StringBuilder data)
    throws ManifoldCFException
  {
      ByteArrayInputStream bis =
        new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8));
      _doc = init(bis);

  }

  /** Build a document object tree from an input
  * stream
  * @param is InputStream of xml to parse
  */
  public XMLDoc(InputStream is)
    throws ManifoldCFException
  {
    _doc = init(is);
  }

  /** Construct a document from all the children of an existing element object from another document.
  */
  public XMLDoc(XMLDoc oldDoc, Object parent)
    throws ManifoldCFException
  {
    try
    {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setEntityResolver(new MyEntityResolver());
      _doc = builder.newDocument();

      // Now, loop through the document or element's children and transfer them
      NodeList nodes;
      if (parent == null)
        nodes = oldDoc._doc.getChildNodes();
      else
        nodes = ((Node)parent).getChildNodes();
      int sz = nodes.getLength();
      for (int index = 0; index < sz; index++)
      {
        Node node = nodes.item(index);
        if (node.getNodeType() == Node.ELEMENT_NODE)
          _doc.appendChild(duplicateNode(node));
      }
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Error setting up parser: "+e.getMessage(),e);
    }
  }

  private Document init(InputStream is)
    throws ManifoldCFException
  {
    Document doc = null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(false);
      DocumentBuilder builder;

      builder = factory.newDocumentBuilder();
      builder.setEntityResolver(new MyEntityResolver());

      doc = builder.parse(is);
    }
    catch (Exception e)
    {
      if (Logging.misc.isDebugEnabled())
      {
        // We want to output some context.  But there are two problems.
        // First, we don't know the encoding.  Second, we don't have infinite memory.
        
        StringWriter sw = new StringWriter();
        try
        {
          // This won't work for all streams, but we catch the exception
          is.reset();
          byte[] buf = new byte[65536]; 
          int len = is.read(buf);
          if (len != -1)
          {
            // Append the bytes we have, and stop.  Presume the encoding is utf-8;
            // if we're wrong it will come out as garbage, but that can't be helped.
            sw.append(new String(buf, 0, len, StandardCharsets.UTF_8));
            if (len == buf.length)
              sw.append("...");
          }
        }
        catch(Exception e1)
        {
          // ignore
        }
        Logging.misc.debug(sw.toString(), e);
      }
      throw new ManifoldCFException("XML parsing error: "+e.getMessage(),e);
    }

    return doc;
  }

  /** Return the value of a named attribute
  * @param elo Object to ask
  * @param a String attribute to find
  * @return String value
  */
  public final String getValue(Object elo, String a)
  {
    Element el = (Element)elo;
    return (String)(el.getAttribute(a));
  }

  /** Return element name.
  * May return null if node not
  * of type Element
  * @param el Object to ask
  * @return String value
  */
  public final String getNodeName(Object el)
  {
    String name = null;
    Node node = (Node)el;

    if (node.getNodeType() == Node.ELEMENT_NODE)
    {
      name = node.getNodeName();
    }
    return name;
  }

  /** Get TEXT element value as single string.
  * @param obj Element to grab data
  * @return TXT collapsed for this element
  * ie [tag]Julie[/tag] returns "Julie"
  */
  public final String getData(Object obj)
  {
    Node enode = (Node)obj;
    StringBuilder data = new StringBuilder();
    NodeList cdata = enode.getChildNodes();

    // expect just 1
    int sz = cdata.getLength();
    for (int j = 0; j < sz; j++)
    {
      Node node = cdata.item(j);
      if (node.getNodeType() == Node.TEXT_NODE)
      {
        Text sec = (Text)node;
        sec.normalize();
        data.append(sec.getData().trim());
      }
      else if (node.getNodeType() == Node.CDATA_SECTION_NODE)
      {
        CDATASection sec = (CDATASection)node;
        data.append(sec.getData().trim());
      }

    }

    return data.toString();
  }

  /** Return root node
  * @return untyped object for later use
  */
  public Object getRoot()
  {
    return getRoot(_doc);
  }
  /** Return root node
  * @param obj Object document, might not be 'this'
  * @return untyped object for later use
  */
  public Object getRoot(Object obj)
  {
    NodeList nodes = ((Document)obj).getChildNodes();
    return nodes.item(0);
  }

  /** Return all nodes belonging to this node;
  * Suppling null means the document is root.
  * @param n Object to ask
  * @return ArrayList of objects
  */
  private final ArrayList getElements(Object n)
  {
    return getElements(n, null);
  }

  /** Extract the attribute names from the given
  * node.  If 'n' is not a node, no attributes
  * will be returned but the array will not be null
  * @param n Object to ask
  * @return ArrayList of attribute names
  */
  public final ArrayList getAttributes(Object n)
  {
    ArrayList atts = new ArrayList();

    NamedNodeMap map = ((Node)n).getAttributes();
    for (int i = 0; i < map.getLength(); i++)
    {
      Attr att = (Attr)map.item(i);
      atts.add(att.getName());
    }
    return atts;
  }

  /** Return the first object to match tagname
  * @param parent Object
  * @param tagname String nodename
  * @return null or found element (Object)
  */
  public Object getElement(Object parent, String tagname)
  {
    ArrayList l = getElements(parent, tagname);
    if (l.size() < 1)
    {
      return null;
    }
    return l.get(0);
  }

  /**
  * Get the elements of this element by name
  * @param parent Object element
  * @param tagname String matching elements (tag name), comma seperated ok
  * @return ArrayListist of nodes
  */
  private final ArrayList getElements(Object parent, String tagname)
  {
    ArrayList list = new ArrayList();
    NodeList nodes = (parent==null ? _doc.getChildNodes()
    : ((Node)parent).getChildNodes());
    int sz = nodes.getLength();

    ArrayList tags = new ArrayList();
    int tagsz = 0;

    // Supplied tagname(s)?
    if (tagname!=null)
    {
      StringTokenizer st = new StringTokenizer(tagname, ",");
      while (st.hasMoreTokens())
      {
        tags.add(st.nextToken());
      }
    }

    // Process found elements
    tagsz = tags.size();
    for (int index = 0; index < sz; index++)
    {
      Node node = nodes.item(index);
      if (node.getNodeType() == Node.ELEMENT_NODE)
      {
        String theTag = node.getNodeName();

        // Add all
        if (tagsz == 0)
        {
          list.add(node);
        }

        // Add matches only
        else
        {
          for (int j = 0; j < tagsz; j++)
          {
            if (theTag.equalsIgnoreCase((String)tags.get(j)))
            {
              list.add(node);
              break;  // done, one match only possible
            }
          }
        }
      }
    }

    return list;
  }

  /*************************************************************************
  *************************************************************************
  *************************************************************************
  */

  /** Create an element
  * @param who Object parent Node
  * @param ename String element name
  * @return Object element
  */
  public Object createElement(Object who, String ename)
  {
    Element element = _doc.createElement(ename);

    if (who==null)
    {
      _doc.appendChild(element);
    }
    else
    {
      ((Element)who).appendChild(element);
    }

    return element;
  }

  /** Add the children of another document's node as the children of this node.
  */
  public void addDocumentElement(Object where, XMLDoc oldDoc, Object parent)
  {
    // Now, loop through the document or element's children and transfer them
    NodeList nodes;
    if (parent == null)
      nodes = oldDoc._doc.getChildNodes();
    else
      nodes = ((Node)parent).getChildNodes();
    int sz = nodes.getLength();
    for (int index = 0; index < sz; index++)
    {
      Node node = nodes.item(index);
      if (where == null)
        _doc.appendChild(duplicateNode(node));
      else
        ((Element)where).appendChild(duplicateNode(node));
    }
  }

  /** Set an attribute on an element
  * @param e Object element to modify
  * @param sName String attribute name
  * @param sValue String attribute value
  */
  public void setAttribute(Object e, String sName, String sValue)
  {
    ((Element)e).setAttribute(sName, sValue);
  }


  /** Create a free-form data value (vs attribute value=)
  * @param who Object
  * @param data String text to add as cdata/text
  */
  public Object createText(Object who, String data)
  {
    Text element = _doc.createTextNode(data);

    if (who==null)
    {
      _doc.appendChild(element);
    }
    else
    {
      ((Element)who).appendChild(element);
    }

    return element;
  }

  /** Make a (deep) copy of a node.
  *@param node is the node object
  *@return the local copy.
  */
  protected Node duplicateNode(Node node)
  {
    Node rval;

    // First, figure out what type it is
    int type = node.getNodeType();
    switch (type)
    {
    case Node.ELEMENT_NODE:
      rval = _doc.createElement(node.getNodeName());
      // Copy attributes
      NamedNodeMap nmap = node.getAttributes();
      int i = 0;
      while (i < nmap.getLength())
      {
        Attr attribute = (Attr)nmap.item(i++);
        ((Element)rval).setAttribute(attribute.getName(),attribute.getValue());
      }
      // Copy children
      NodeList children = node.getChildNodes();
      i = 0;
      while (i < children.getLength())
      {
        rval.appendChild(duplicateNode(children.item(i++)));
      }
      break;

    case Node.TEXT_NODE:
      // Get the data
      rval = _doc.createTextNode(((Text)node).getData());
      break;

    case Node.CDATA_SECTION_NODE:
      // Create a CDATA section
      rval = _doc.createCDATASection(((CDATASection)node).getNodeValue());
      break;

    case Node.COMMENT_NODE:
      rval = _doc.createComment(((Comment)node).getNodeValue());
      break;

    default:
      //System.out.println("Unknown node: "+Integer.toString(type));
      return null;
    }
    return rval;
  }

  // Transform the output for serialization
  private void dumpOutput(OutputStream os)
    throws ManifoldCFException
  {
    try
    {
      StreamResult res = new StreamResult(os);
      TransformerFactory tFactory = TransformerFactory.newInstance();
      Transformer transformer = tFactory.newTransformer();

      DOMSource source = new DOMSource(_doc);
      transformer.transform(source, res);
    }
    catch (Exception e)
    {
      throw new ManifoldCFException("Error dumping output: "+e.getMessage(),e);
    }
  }

  protected static class MyEntityResolver implements org.xml.sax.EntityResolver
  {
    public org.xml.sax.InputSource resolveEntity(java.lang.String publicId, java.lang.String systemId)
      throws SAXException, java.io.IOException
    {
      // ALL references resolve to blank documents
      return new org.xml.sax.InputSource(new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes(StandardCharsets.UTF_8)));
    }
  }


}
