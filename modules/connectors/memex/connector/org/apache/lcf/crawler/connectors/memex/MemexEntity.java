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

/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
*/

package org.apache.lcf.crawler.connectors.memex;

import java.util.*;
import java.io.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;
import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import com.memex.mie.*;

/**
*
* @author mxadmin
*/
class MemexEntity {

  private String name = "";
  private String displayname = "";
  private String URN = "";
  private String prefix = "";
  private String[] fields = null;
  private String[] labels = null;

  private Hashtable fieldlabels = new Hashtable();
  private Hashtable labelfields = new Hashtable();

  private static HashMap<String,String> metaDBs;
  static
  {
    metaDBs = new HashMap<String,String>();
    metaDBs.put("CA","mxCase");
    metaDBs.put("UG","mxUserGroup");
    metaDBs.put("e","mxEntity");
    metaDBs.put("XX","mxServer");
    metaDBs.put("YY","mxAudit");
    metaDBs.put("DI","mxDisseminate");
    metaDBs.put("AC","mxAction");
    metaDBs.put("LK","mxLinkDB");
    // I couldn't find a prefix for this one...
    //metaDBs.put("mxSession","mxSession");
    metaDBs.put("LC","LinkChart");
  }

  public MemexEntity(String newname, String newURN, String newPrefix, String newdisplayname, String newfields, String newlabels)
  {
    this(newname,newURN,newPrefix,newdisplayname,newfields,newlabels,null);
  }

  public MemexEntity(String newname, String newURN, String newPrefix, String newdisplayname, String newfields, String newlabels, Document entityForm)
  {
    name = newname;
    URN = newURN;
    prefix = newPrefix;
    if(newdisplayname == null || newdisplayname.length() == 0)
      newdisplayname = newname;
    displayname = newdisplayname;

    //If there is a form file, scan through it for subrecords
    //and the fields they contain.
    Hashtable<String, String> subrecordfields = new Hashtable<String, String>();
    if(entityForm != null){
      NodeList tabNodes = entityForm.getElementsByTagName("tab");
      for(int x = 0; x < tabNodes.getLength(); x++) {
        NodeList controlNodes = tabNodes.item(x).getChildNodes();
        for(int i = 0; i < controlNodes.getLength(); i++) {
          Node node = controlNodes.item(i);
          NamedNodeMap attributes = node.getAttributes();
          if(attributes != null){
            Node subrecord = attributes.getNamedItem("subrecord");
            if(subrecord != null){
              Node fieldname = attributes.getNamedItem("fieldname");
              //This node represents a subrecord form item mapped
              //to a field
              if(fieldname != null){
                subrecordfields.put(fieldname.getNodeValue(), subrecord.getNodeValue());
              }
            }
          }
        }
      }
      //Every entity contains the docfilter (attachments) subrecord
      //regardless of whether the fields are shown on the form.
      if(!(subrecordfields.contains("attachname"))){
        subrecordfields.put("attachname", "docfilter");
      }
      if(!(subrecordfields.contains("attachtext"))){
        subrecordfields.put("attachtext", "docfilter");
      }
    }

    if((!(newfields == null))&&(!(newfields.equals("")))){
      fields = newfields.split(",");
      //If the field is in a subrecord, prefix the fieldname with
      //the subrecord name followed by a dot
      for(int i = 0; i < fields.length; i++){
        if((subrecordfields != null)&&(subrecordfields.containsKey(fields[i]))){
          fields[i] = subrecordfields.get(fields[i]) + "." + fields[i];
        }
      }
      fields = this.addSystemFields(fields);
      Arrays.sort(fields, String.CASE_INSENSITIVE_ORDER);
    }
    if((!(newlabels == null))&&(!(newlabels.equals("")))){
      String[] labelsArray = newlabels.split(",");
      for(int i = 0; i < labelsArray.length; i++){
        String[] label = labelsArray[i].split(":");
        fieldlabels.put(label[0], label[1]);
        labelfields.put(label[1], label[0]);
      }
    }

    //Not every field will necessarilly have been set a display name. In these cases,
    //display name defaults to the field name
    if(!(fields == null)){
      labels = new String[fields.length];
      for(int i = 0; i < fields.length; i++){
        if(fieldlabels.containsKey(fields[i])){
          labels[i] = (String)fieldlabels.get(fields[i]);
        }else{
          labels[i] = fields[i];
          fieldlabels.put(fields[i], fields[i]);
          labelfields.put(fields[i], fields[i]);
        }
      }
      Arrays.sort(labels, String.CASE_INSENSITIVE_ORDER);
    }
  }

  /** Return the entity prefix */
  public String getPrefix()
  {
    return prefix;
  }

  /** Return the name */
  public String getName()
  {
    return name;
  }

  /** Return the display name */
  public String getDisplayName()
  {
    return displayname;
  }

  /** Return the URN */
  public String getURN()
  {
    return URN;
  }

  /** Return true if entity is metadb */
  public boolean isMetaDB()
  {
    return metaDBs.get(prefix) != null;
  }

  /** Return the field names */
  public String[] getFields()
  {
    return fields;
  }

  // Private methods

  private String[] addSystemFields(String[] oldfields){

    String[] newfields = new String[oldfields.length + 40];

    for(int i = 0; i < oldfields.length; i++){
      newfields[i+40] = oldfields[i];
    }
    newfields[0] = "sysurn";
    newfields[1] = "syswithheldmsg";
    newfields[2] = "sysdatecreated";
    newfields[3] = "systimecreated";
    newfields[4] = "syscreatedby";
    newfields[5] = "sysdateupdated";
    newfields[6] = "systimeupdated";
    newfields[7] = "sysupdatedby";
    newfields[8] = "syscategory";
    newfields[9] = "attachments";
    newfields[10] = "reviewdate";
    newfields[11] = "supcomments";
    newfields[12] = "supdate";
    newfields[13] = "supstatus";
    newfields[14] = "flagmessage";
    newfields[15] = "flagcontact";
    newfields[16] = "syscases";
    newfields[17] = "sysarea";
    newfields[18] = "sysprotectedinfo";
    newfields[19] = "sysxcoord";
    newfields[20] = "sysycoord";
    newfields[21] = "supervisedby";
    newfields[22] = "syssubstantiates";
    newfields[23] = "sysarchived";
    newfields[24] = "syssubstantiatedby";
    newfields[25] = "reviewemail";
    newfields[26] = "covertemail";
    newfields[27] = "attachname";
    newfields[28] = "attachtext";
    newfields[29] = "filelinks";
    newfields[30] = "flaggedby";
    newfields[31] = "interestmessage";
    newfields[32] = "interestcontact";
    newfields[33] = "disseminations";
    newfields[34] = "assessments";
    newfields[35] = "interesttime";
    newfields[36] = "interestdate";
    newfields[37] = "interestby";
    newfields[38] = "flagtime";
    newfields[39] = "flagdate";

    return newfields;

  }

  protected String getFieldName(String label){
    if(labelfields.containsKey(label)){
      return (String)labelfields.get(label);
    }else{
      return "";
    }
  }
  protected String getLabel(String field){
    if(fieldlabels.containsKey(field)){
      return (String)fieldlabels.get(field);
    }else{
      return "";
    }
  }

}
