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

/**
* Versions.java
*
* This file was auto-generated from WSDL
* by the Apache Axis WSDL2Java emitter.
*/

package org.apache.manifoldcf.crawler.connectors.sharepoint;

public class VersionsHeader  implements java.io.Serializable {
  private java.lang.String[] version;

  public VersionsHeader() {
  }

  public java.lang.String[] getVersion() {
    return version;
  }

  public void setVersion(java.lang.String[] version) {
    this.version = version;
  }

  public java.lang.String getVersion(int i) {
    return version[i];
  }

  public void setVersion(int i, java.lang.String value) {
    this.version[i] = value;
  }

  private java.lang.Object __equalsCalc = null;
  public synchronized boolean equals(java.lang.Object obj) {
    if (!(obj instanceof VersionsHeader)) return false;
      VersionsHeader other = (VersionsHeader) obj;
    if (obj == null) return false;
    if (this == obj) return true;
      if (__equalsCalc != null) {
      return (__equalsCalc == obj);
    }
    __equalsCalc = obj;
    boolean _equals;
    _equals = true &&
      ((this.version==null && other.getVersion()==null) ||
      (this.version!=null &&
      java.util.Arrays.equals(this.version, other.getVersion())));
    __equalsCalc = null;
    return _equals;
  }

  private boolean __hashCodeCalc = false;
  public synchronized int hashCode() {
    if (__hashCodeCalc) {
      return 0;
    }
    __hashCodeCalc = true;
    int _hashCode = 1;
    if (getVersion() != null) {
      for (int i=0;
        i<java.lang.reflect.Array.getLength(getVersion());
      i++) {
        java.lang.Object obj = java.lang.reflect.Array.get(getVersion(), i);
        if (obj != null &&
          !obj.getClass().isArray()) {
          _hashCode += obj.hashCode();
        }
      }
    }
    __hashCodeCalc = false;
    return _hashCode;
  }

  // Type metadata
  private static org.apache.axis.description.TypeDesc typeDesc =
    new org.apache.axis.description.TypeDesc(VersionsHeader.class);

  static {
    typeDesc.setXmlType(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/dsp", "Versions"));
    org.apache.axis.description.ElementDesc elemField = new org.apache.axis.description.ElementDesc();
    elemField.setFieldName("version");
    elemField.setXmlName(new javax.xml.namespace.QName("http://schemas.microsoft.com/sharepoint/dsp", "version"));
    elemField.setXmlType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
    elemField.setMinOccurs(0);
    typeDesc.addFieldDesc(elemField);
  }

  /**
  * Return type metadata object
  */
  public static org.apache.axis.description.TypeDesc getTypeDesc() {
    return typeDesc;
  }

  /**
  * Get Custom Serializer
  */
  public static org.apache.axis.encoding.Serializer getSerializer(
    java.lang.String mechType,
    java.lang.Class _javaType,
    javax.xml.namespace.QName _xmlType) {
    return
    new  org.apache.axis.encoding.ser.BeanSerializer(
      _javaType, _xmlType, typeDesc);
  }

  /**
  * Get Custom Deserializer
  */
  public static org.apache.axis.encoding.Deserializer getDeserializer(
    java.lang.String mechType,
    java.lang.Class _javaType,
    javax.xml.namespace.QName _xmlType) {
    return
    new  org.apache.axis.encoding.ser.BeanDeserializer(
      _javaType, _xmlType, typeDesc);
  }

}
