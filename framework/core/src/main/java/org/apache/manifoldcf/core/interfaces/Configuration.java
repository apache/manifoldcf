/* $Id: Configuration.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import java.util.*;
import java.io.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.core.common.XMLDoc;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/** This class represents XML configuration information, in its most basic incarnation.
*/
public class Configuration implements IHierarchyParent
{
  public static final String _rcsid = "@(#)$Id: Configuration.java 988245 2010-08-23 18:39:35Z kwright $";

  // JSON special key values
  
  protected static final String JSON_ATTRIBUTE = "_attribute_";
  protected static final String JSON_VALUE = "_value_";
  protected static final String JSON_CHILDREN = "_children_";
  protected static final String JSON_TYPE = "_type_";
  
  // The root node type
  protected String rootNodeLabel;
  // The children
  protected List<ConfigurationNode> children = new ArrayList<ConfigurationNode>();
  // Read-only flag
  protected boolean readOnly = false;

  /** Constructor.
  */
  public Configuration()
  {
    rootNodeLabel = "data";
  }

  /** Constructor.
  *@param rootNodeLabel is the root node label to use.
  */
  public Configuration(String rootNodeLabel)
  {
    this.rootNodeLabel = rootNodeLabel;
  }

  /** Create a new object of the appropriate class.
  *@return the newly-created configuration object.
  */
  protected Configuration createNew()
  {
    return new Configuration();
  }
  
  /** Create a new child node of the appropriate type and class.
  *@return the newly-created node.
  */
  protected ConfigurationNode createNewNode(String type)
  {
    return new ConfigurationNode(type);
  }
  
  /** Note the removal of all outer nodes.
  */
  protected void clearOuterNodes()
  {
  }
  
  /** Note the addition of a new outer node.
  *@param node is the node that was just read.
  */
  protected void addOuterNode(ConfigurationNode node)
  {
  }
  
  /** Note the removal of an outer node.
  *@param node is the node that was just removed.
  */
  protected void removeOuterNode(ConfigurationNode node)
  {
  }
  
  /** Create a duplicate.
  */
  protected Configuration createDuplicate(boolean readOnly)
  {
    if (readOnly && this.readOnly)
      return this;
    // Create a new object.
    Configuration rval = createNew();
    rval.readOnly = readOnly;
    // Copy the children.
    if (children != null)
    {
      int i = 0;
      while (i < children.size())
      {
        ConfigurationNode child = children.get(i++);
        // Duplicate the child
        ConfigurationNode newChild = child.createDuplicate(readOnly);
        rval.addChild(rval.getChildCount(),newChild);
      }
    }
    return rval;
  }
  
  /** Make the configuration read-only */
  public void makeReadOnly()
  {
    if (readOnly)
      return;
    if (children != null)
    {
      int i = 0;
      while (i < children.size())
      {
        ConfigurationNode child = children.get(i++);
        child.makeReadOnly();
      }
    }
    readOnly = true;
  }

  /** Get as XML
  *@return the xml corresponding to these Configuration.
  */
  public String toXML()
    throws ManifoldCFException
  {
    XMLDoc doc = new XMLDoc();
    // name of root node in definition
    Object top = doc.createElement(null,rootNodeLabel);
    // Now, go through all children
    int i = 0;
    while (i < children.size())
    {
      ConfigurationNode node = children.get(i++);
      writeNode(doc,top,node);
    }

    return doc.getXML();
  }

  /** Get as JSON.
  *@return the json corresponding to this Configuration.
  */
  public String toJSON()
    throws ManifoldCFException
  {
    JSONWriter writer = new JSONWriter();
    writer.startObject();
    // We do NOT use the root node label, unlike XML.
      
    // Now, do children.  To get the arrays right, we need to glue together all children with the
    // same type, which requires us to do an appropriate pass to gather that stuff together.
    // Since we also need to maintain order, it is essential that we detect the out-of-order condition
    // properly, and use an alternate representation if we should find it.
    Map<String,List<ConfigurationNode>> childMap = new HashMap<String,List<ConfigurationNode>>();
    List<String> childList = new ArrayList<String>();
    // The new JSON parser uses hash order for object keys.  So it isn't good enough to just detect that there's an
    // intermingling.  Instead we need to the existence of more that one key; that implies that we need to do order preservation.
    String lastChildType = null;
    boolean needAlternate = false;
    int i = 0;
    while (i < getChildCount())
    {
      ConfigurationNode child = findChild(i++);
      String key = child.getType();
      List<ConfigurationNode> list = childMap.get(key);
      if (list == null)
      {
        // We found no existing list, so create one
        list = new ArrayList<ConfigurationNode>();
        childMap.put(key,list);
        childList.add(key);
      }
      // Key order comes into play when we have elements of different types within the same child. 
      if (lastChildType != null && !lastChildType.equals(key))
      {
        needAlternate = true;
        break;
      }
      list.add(child);
      lastChildType = key;
    }
        
    if (needAlternate)
    {
      // Can't use the array representation.  We'll need to start do a _children_ object, and enumerate
      // each child.  So, the JSON will look like:
      // <key>:{_attribute_<attr>:xxx,_children_:[{_type_:<child_key>, ...},{_type_:<child_key_2>, ...}, ...]}
      writer.key(JSON_CHILDREN);
      writer.startArray();
      i = 0;
      while (i < getChildCount())
      {
        ConfigurationNode child = findChild(i++);
        writeNode(writer,child,false,true);
      }
      writer.endArray();
    }
    else
    {
      // We can collapse child nodes to arrays and still maintain order.
      // The JSON will look like this:
      // <key>:{_attribute_<attr>:xxx,<child_key>:[stuff],<child_key_2>:[more_stuff] ...}
      int q = 0;
      while (q < childList.size())
      {
        String key = childList.get(q++);
        List<ConfigurationNode> list = childMap.get(key);
        if (list.size() > 1)
        {
          // Write it as an array
          writer.key(key);
          writer.startArray();
          i = 0;
          while (i < list.size())
          {
            ConfigurationNode child = list.get(i++);
            writeNode(writer,child,false,false);
          }
          writer.endArray();
        }
        else
        {
          // Write it as a singleton
          writeNode(writer,list.get(0),true,false);
        }
      }
    }
    writer.endObject();

    // Convert to a string.
    return writer.toString();
  }
  
  /** Write a specification node.
  *@param doc is the document.
  *@param parent is the parent.
  *@param node is the node.
  */
  protected static void writeNode(XMLDoc doc, Object parent, ConfigurationNode node)
    throws ManifoldCFException
  {
    // Get the type
    String type = node.getType();
    String value = node.getValue();
    Object o = doc.createElement(parent,type);
    Iterator iter = node.getAttributes();
    while (iter.hasNext())
    {
      String attribute = (String)iter.next();
      String attrValue = node.getAttributeValue(attribute);
      // Add to the element
      doc.setAttribute(o,attribute,attrValue);
    }

    if (value != null)
      doc.createText(o,value);
    // Now, do children
    int i = 0;
    while (i < node.getChildCount())
    {
      ConfigurationNode child = node.findChild(i++);
      writeNode(doc,o,child);
    }
  }

  
  /** Write a JSON specification node.
  *@param writer is the JSON writer.
  *@param node is the node.
  *@param writeKey is true if the key needs to be written, false otherwise.
  */
  protected static void writeNode(JSONWriter writer, ConfigurationNode node, boolean writeKey, boolean writeSpecialKey)
    throws ManifoldCFException
  {
    // Node types correspond directly to keys.  Attributes correspond to "_attribute_<attribute_name>".
    // Get the type
    if (writeKey)
    {
      String type = node.getType();
      writer.key(type);
    }
    else if (writeSpecialKey)
    {
      writer.startObject();
      writer.key(JSON_TYPE);
      writer.value(node.getType());
    }
      
    // Problem: Two ways of handling a naked 'value'.  First way is to NOT presume a nested object is needed.  Second way is to require a nested
    // object.  On reconstruction, the right thing will happen, and a naked value will become a node with a value, while an object will become
    // a node that has an optional "_value_" key inside it.
    String value = node.getValue();
    if (value != null && node.getAttributeCount() == 0 && node.getChildCount() == 0)
    {
      writer.value(value);
    }
    else
    {
      if (!writeSpecialKey)
        writer.startObject();
        
      if (value != null)
      {
        writer.key(JSON_VALUE);
        writer.value(value);
      }
        
      Iterator<String> iter = node.getAttributes();
      while (iter.hasNext())
      {
        String attribute = iter.next();
        String attrValue = node.getAttributeValue(attribute);
        writer.key(JSON_ATTRIBUTE+attribute);
        writer.value(attrValue);
      }

      // Now, do children.  To get the arrays right, we need to glue together all children with the
      // same type, which requires us to do an appropriate pass to gather that stuff together.
      // Since we also need to maintain order, it is essential that we detect the out-of-order condition
      // properly, and use an alternate representation if we should find it.
      Map<String,List<ConfigurationNode>> childMap = new HashMap<String,List<ConfigurationNode>>();
      List<String> childList = new ArrayList<String>();
      String lastChildType = null;
      boolean needAlternate = false;
      int i = 0;
      while (i < node.getChildCount())
      {
        ConfigurationNode child = node.findChild(i++);
        String key = child.getType();
        List<ConfigurationNode> list = childMap.get(key);
        if (list == null)
        {
          list = new ArrayList<ConfigurationNode>();
          childMap.put(key,list);
          childList.add(key);
        }
        else
        {
          if (!lastChildType.equals(key))
          {
            needAlternate = true;
            break;
          }
        }
        list.add(child);
        lastChildType = key;
      }
        
      if (needAlternate)
      {
        // Can't use the array representation.  We'll need to start do a _children_ object, and enumerate
        // each child.  So, the JSON will look like:
        // <key>:{_attribute_<attr>:xxx,_children_:[{_type_:<child_key>, ...},{_type_:<child_key_2>, ...}, ...]}
        writer.key(JSON_CHILDREN);
        writer.startArray();
        i = 0;
        while (i < node.getChildCount())
        {
          ConfigurationNode child = node.findChild(i++);
          writeNode(writer,child,false,true);
        }
        writer.endArray();
      }
      else
      {
        // We can collapse child nodes to arrays and still maintain order.
        // The JSON will look like this:
        // <key>:{_attribute_<attr>:xxx,<child_key>:[stuff],<child_key_2>:[more_stuff] ...}
        int q = 0;
        while (q < childList.size())
        {
          String key = childList.get(q++);
          List<ConfigurationNode> list = childMap.get(key);
          if (list.size() > 1)
          {
            // Write the key
            writer.key(key);
            // Write it as an array
            writer.startArray();
            i = 0;
            while (i < list.size())
            {
              ConfigurationNode child = list.get(i++);
              writeNode(writer,child,false,false);
            }
            writer.endArray();
          }
          else
          {
            // Write it as a singleton
            writeNode(writer,list.get(0),true,false);
          }
        }
      }
      if (!writeSpecialKey)
        writer.endObject();
    }
    if (writeSpecialKey)
      writer.endObject();
  }
  
  /** Read from XML.
  *@param xml is the input XML.
  */
  public void fromXML(String xml)
    throws ManifoldCFException
  {
    XMLDoc doc = new XMLDoc(xml);
    initializeFromDoc(doc);
  }
  
  /** Read from JSON.
  *@param json is the input JSON.
  */
  public void fromJSON(String json)
    throws ManifoldCFException
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");

    clearChildren();
    final JSONReader object = new JSONReader(json);
    object.startObject();
    // Convert the object into our configuration
    final Iterator<String> iter = object.getKeys();
    while (iter.hasNext())
    {
      final String key = iter.next();
      object.valueForKey(key);
      if (object.isArray())
      {
        // Iterate through.
        object.startArray();
        while (object.nextElement()) {
          processObject(key,object);
        }
        object.endArray();
      }
      else
        processObject(key,object);
    }
    object.endObject();
  }
  
  /** Process a JSON object */
  protected void processObject(String key, JSONReader x)
    throws ManifoldCFException
  {
    if (x.isObject())
    {
      // Nested single object
      ConfigurationNode cn = readNode(key,x);
      addChild(getChildCount(),cn);
    }
    else if (x.isNull())
    {
      // Null object.  Don't enter the key.
    }
    else if (key.equals(JSON_CHILDREN))
    {
      // Children, as a list of separately enumerated child nodes.
      if (!(x.isArray()))
        throw new ManifoldCFException("Expected array contents for '"+JSON_CHILDREN+"' node");
      x.startArray();
      while (x.nextElement()) {
        ConfigurationNode nestedCn = readNode((String)null,x);
        addChild(getChildCount(),nestedCn);
      }
      x.endArray();
    }
    else
    {
      // It's a string or a number or some scalar value
      final String value = x.readValue();
      ConfigurationNode cn = createNewNode(key);
      cn.setValue(value);
      addChild(getChildCount(),cn);
    }
  }
  
  /** Read a node from a json object */
  protected ConfigurationNode readNode(String key, JSONReader object)
    throws ManifoldCFException
  {
    object.startObject();
    // Override key if type field is found.
    if (object.valueForKey(JSON_TYPE))
    {
      if (!object.isValue()) {
        throw new ManifoldCFException("JSON_TYPE key does not have a string value");
      }
      key = object.readValue();
    }
    if (key == null)
      throw new ManifoldCFException("No type found for node");
    final ConfigurationNode rval = createNewNode(key);
    final Iterator<String> iter = object.getKeys();
    while (iter.hasNext())
    {
      final String nestedKey = iter.next();
      if (!nestedKey.equals(JSON_TYPE))
      {
        object.valueForKey(nestedKey);
        if (object.isArray())
        {
          // Iterate through.
          object.startArray();
          while (object.nextElement()) {
            processObject(rval,nestedKey,object);
          }
          object.endArray();
        }
        else
          processObject(rval,nestedKey,object);
      }
    }
    object.endObject();
    return rval;
  }
  
  /** Process a JSON object */
  protected void processObject(ConfigurationNode cn, String key, JSONReader x)
    throws ManifoldCFException
  {
    if (x.isObject())
    {
      // Nested single object
      ConfigurationNode nestedCn = readNode(key,x);
      cn.addChild(cn.getChildCount(),nestedCn);
    }
    else if (x.isNull())
    {
      // Null object.  Don't enter the key.
    }
    else
    {
      // It's a string or a number or some scalar value
      String value = x.readValue();
      // Is it an attribute, or a value?
      if (key.startsWith(JSON_ATTRIBUTE))
      {
        // Attribute.  Set the attribute in the current node.
        cn.setAttribute(key.substring(JSON_ATTRIBUTE.length()),value);
      }
      else if (key.equals(JSON_VALUE))
      {
        // Value.  Set the value in the current node.
        cn.setValue(value);
      }
      else if (key.equals(JSON_CHILDREN))
      {
        // Children, as a list of separately enumerated child nodes.
        if (!(x.isArray()))
          throw new ManifoldCFException("Expected array contents for '"+JSON_CHILDREN+"' node");
        
        x.startArray();
        while (x.nextElement()) {
          ConfigurationNode nestedCn = readNode((String)null,x);
          cn.addChild(cn.getChildCount(),nestedCn);
        }
        x.endArray();
      }
      else
      {
        // Something we don't recognize, which can only be a simplified key/value pair.
        // Create a child node representing the key/value pair.
        ConfigurationNode nestedCn = createNewNode(key);
        nestedCn.setValue(value);
        cn.addChild(cn.getChildCount(),nestedCn);
      }
    }
  }

  /** Read from an XML binary stream.
  *@param xmlstream is the input XML stream.  Does NOT close the stream.
  */
  public void fromXML(InputStream xmlstream)
    throws ManifoldCFException
  {
    XMLDoc doc = new XMLDoc(xmlstream);
    initializeFromDoc(doc);
  }

  protected void initializeFromDoc(XMLDoc doc)
    throws ManifoldCFException
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    clearChildren();
    ArrayList list = new ArrayList();
    doc.processPath(list, "*", null);

    if (list.size() != 1)
    {
      throw new ManifoldCFException("Bad xml - missing outer '"+rootNodeLabel+"' node - there are "+Integer.toString(list.size())+" nodes");
    }
    Object parent = list.get(0);
    if (!doc.getNodeName(parent).equals(rootNodeLabel))
      throw new ManifoldCFException("Bad xml - outer node is not '"+rootNodeLabel+"'");

    list.clear();
    doc.processPath(list, "*", parent);

    // Outer level processing.
    int i = 0;
    while (i < list.size())
    {
      Object o = list.get(i++);
      ConfigurationNode node = readNode(doc,o);
      addChild(getChildCount(),node);
    }
  }

  /** Read a configuration node from XML.
  *@param doc is the document.
  *@param object is the object.
  *@return the specification node.
  */
  protected ConfigurationNode readNode(XMLDoc doc, Object object)
    throws ManifoldCFException
  {
    String type = doc.getNodeName(object);
    ConfigurationNode rval = createNewNode(type);
    String value = doc.getData(object);
    rval.setValue(value);
    // Do attributes
    ArrayList list = doc.getAttributes(object);
    int i = 0;
    while (i < list.size())
    {
      String attribute = (String)list.get(i++);
      String attrValue = doc.getValue(object,attribute);
      rval.setAttribute(attribute,attrValue);
    }
    // Now, do children
    list.clear();
    doc.processPath(list,"*",object);
    i = 0;
    while (i < list.size())
    {
      Object o = list.get(i);
      ConfigurationNode node = readNode(doc,o);
      rval.addChild(i++,node);
    }
    return rval;
  }

  /** Get child count.
  *@return the count.
  */
  public int getChildCount()
  {
    return children.size();
  }

  /** Get child n.
  *@param index is the child number.
  *@return the child node.
  */
  public ConfigurationNode findChild(int index)
  {
    return children.get(index);
  }

  /** Remove child n.
  *@param index is the child to remove.
  */
  public void removeChild(int index)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    ConfigurationNode node = children.remove(index);
    removeOuterNode(node);
  }

  /** Add child at specified position.
  *@param index is the position to add the child.
  *@param child is the child to add.
  */
  public void addChild(int index, ConfigurationNode child)
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    children.add(index,child);
    addOuterNode(child);
  }

  /** Clear children.
  */
  public void clearChildren()
  {
    if (readOnly)
      throw new IllegalStateException("Attempt to change read-only object");
    children.clear();
    clearOuterNodes();
  }

  /** Calculate a hash code */
  public int hashCode()
  {
    int rval = 0;
    int i = 0;
    while (i < children.size())
    {
      rval += children.get(i++).hashCode();
    }
    return rval;
  }

  /** Do a comparison */
  public boolean equals(Object o)
  {
    if (!(o instanceof Configuration))
      return false;
    Configuration p = (Configuration)o;
    if (children.size() != p.children.size())
      return false;
    int i = 0;
    while (i < children.size())
    {
      if (!children.get(i).equals(p.children.get(i)))
        return false;
      i++;
    }
    return true;
  }

  /** Construct a human-readable string */
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    if (children != null)
    {
      int i = 0;
      while (i < children.size())
      {
        if (i > 0)
          sb.append(", ");
        ConfigurationNode cn = children.get(i++);
        sb.append(cn.toString());
      }
    }
    sb.append("]");
    return sb.toString();
  }
  
  protected static class JSONWriter {
    
    private final List<JSONObject> objectStack = new ArrayList<>();
    private final List<String> keyStack = new ArrayList<>();
    private final List<JSONArray> arrayStack = new ArrayList<>();
    private JSONObject currentObject = null;
    private JSONArray currentArray = null;
    private String currentKey = null;
    
    private JSONObject finalObject = null;
    private JSONArray finalArray = null;
    
    public JSONWriter() {
    }
    
    public void startObject() {
      pushState();
      currentObject = new JSONObject();
      currentKey = null;
      currentArray = null;
    }

    public void key(final String key) {
      currentKey = key;
    }
    
    public void value(final String value) {
      if (currentKey != null) {
        currentObject.put(currentKey, value);
      } else if (currentArray != null) {
        currentArray.add(value);
      } else {
        throw new RuntimeException("Naked value found with no context!");
      }
    }
    
    public void endObject() {
      final JSONObject object = currentObject;
      // Pop everything
      popState();
      // Either an array is active, or an object is active, or nothing is active
      if (currentObject != null) {
        currentObject.put(currentKey, object);
      } else if (currentArray != null) {
        currentArray.add(object);
      } else {
        finalObject = object;
      }
    }
    
    public void startArray() {
      pushState();
      currentObject = null;
      currentKey = null;
      currentArray = new JSONArray();
    }
    
    public void endArray() {
      final JSONArray array = currentArray;
      // Pop everything
      popState();
      // Either an array is active, or an object is active, or nothing is active
      if (currentObject != null) {
        currentObject.put(currentKey, array);
      } else if (currentArray != null) {
        currentArray.add(array);
      } else {
        finalArray = array;
      }
    }
    
    public String toString() {
      if (finalObject != null) {
        return finalObject.toJSONString();
      } else if (finalArray != null) {
        return finalArray.toJSONString();
      } else {
        return "";
      }
    }
    
    protected void pushState() {
      objectStack.add(currentObject);
      keyStack.add(currentKey);
      arrayStack.add(currentArray);
    }
    
    protected void popState() {
      currentObject = objectStack.remove(objectStack.size()-1);
      currentKey = keyStack.remove(keyStack.size()-1);
      currentArray = arrayStack.remove(arrayStack.size()-1);
    }

  }
  
  protected static class JSONReader {
    
    private final List<JSONObject> objectStack = new ArrayList<>();
    private final List<Iterator> arrayStack = new ArrayList<>();
    private JSONObject currentObject = null;
    private Iterator currentArrayIterator = null;
    
    private Object next = null;

    public JSONReader(final String json) throws ManifoldCFException {
      final JSONParser parser = new JSONParser();
      try {
        next = parser.parse(new StringReader(json));
      } catch (Exception e) {
        throw new ManifoldCFException("Bad json: "+e.getMessage(),e);
      }
    }
    
    public boolean isObject() {
      return next != null && (next instanceof JSONObject);
    }
    
    public void startObject() {
      pushState();
      currentObject = (JSONObject)next;
      currentArrayIterator = null;
    }

    public Iterator<String> getKeys() {
      return currentObject.keySet().iterator();
    }
    
    public boolean valueForKey(final String key) {
      next = currentObject.get(key);
      return next != null;
    }
    
    public void endObject() {
      popState();
    }
    
    public boolean isArray() {
      return next != null && (next instanceof JSONArray);
    }
    
    public void startArray() {
      pushState();
      currentObject = null;
      currentArrayIterator = ((JSONArray)next).iterator();
    }
    
    public boolean nextElement() {
      if (currentArrayIterator.hasNext()) {
        next = currentArrayIterator.next();
        return true;
      }
      return false;
    }
    
    public void endArray() {
      popState();
    }

    public boolean isNull() {
      return next == null;
    }
    
    public boolean isValue() {
      return next != null && !(next instanceof JSONObject || next instanceof JSONArray);
    }
    
    public String readValue() {
      return next.toString();
    }
    
    protected void pushState() {
      objectStack.add(currentObject);
      arrayStack.add(currentArrayIterator);
    }
    
    protected void popState() {
      currentObject = objectStack.remove(objectStack.size()-1);
      currentArrayIterator = arrayStack.remove(arrayStack.size()-1);
    }
    
  }
}
