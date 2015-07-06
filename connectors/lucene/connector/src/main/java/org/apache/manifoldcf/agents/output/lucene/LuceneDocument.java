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
package org.apache.manifoldcf.agents.output.lucene;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import com.google.common.base.Objects;

public class LuceneDocument {

  private Document doc;

  private static final FieldType TYPE_STORED_WITH_TV = new FieldType(TextField.TYPE_STORED);
  static {
    TYPE_STORED_WITH_TV.setStoreTermVectors(true);
    TYPE_STORED_WITH_TV.setStoreTermVectorOffsets(true);
    TYPE_STORED_WITH_TV.setStoreTermVectorPositions(true);
    TYPE_STORED_WITH_TV.freeze();
  }

  private static final FieldType TYPE_NOT_STORED_WITH_TV = new FieldType(TextField.TYPE_NOT_STORED);
  static {
    TYPE_NOT_STORED_WITH_TV.setStoreTermVectors(true);
    TYPE_NOT_STORED_WITH_TV.setStoreTermVectorOffsets(true);
    TYPE_NOT_STORED_WITH_TV.setStoreTermVectorPositions(true);
    TYPE_NOT_STORED_WITH_TV.freeze();
  }

  public LuceneDocument() {
    doc = new Document();
  }

  public LuceneDocument addStringField(String name, String value, boolean store) {
    Store stored = (store) ? Field.Store.YES : Field.Store.NO;
    doc.add(new StringField(name, value, stored));
    return this;
  }

  public LuceneDocument addTextField(String name, String value, boolean store) {
    FieldType type = (store) ? TYPE_STORED_WITH_TV : TYPE_NOT_STORED_WITH_TV;
    doc.add(new Field(name, value, type));
    return this;
  }

  public Document toDocument() {
    return doc;
  }

  public static LuceneDocument addField(LuceneDocument from, String field, String value, Map<String,Map<String,Object>> fieldsInfo) {
    String fieldtype = (String)fieldsInfo.get(field).get(LuceneClient.ATTR_FIELDTYPE);
    boolean store = (boolean)Objects.firstNonNull(fieldsInfo.get(field).get(LuceneClient.ATTR_STORE), false);

    if (fieldtype.equals(LuceneClient.FIELDTYPE_TEXT)) {
      from.addTextField(field, value, store);
    } else if (fieldtype.equals(LuceneClient.FIELDTYPE_STRING)) {
      from.addStringField(field, value, store);
    }

    @SuppressWarnings("unchecked")
    List<String> copyFields = (List<String>)Objects.firstNonNull(fieldsInfo.get(field).get(LuceneClient.ATTR_COPY_TO), new ArrayList<String>());
    for (String tofield : copyFields) {
      from = addField(from, tofield, value, fieldsInfo);
    }
    return from;
  }

}
