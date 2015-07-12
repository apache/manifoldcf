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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.util.BytesRef;
import org.apache.manifoldcf.agents.output.lucene.LuceneClient.TermVector;

import com.google.common.base.Objects;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;

public class LuceneDocument {

  private Document doc;

  private static final FieldType STORED = new FieldType();
  static {
    STORED.setOmitNorms(false);
    STORED.setIndexOptions(IndexOptions.NONE);
    STORED.setTokenized(false);
    STORED.setStored(true);
    STORED.freeze();
  }

  private static final FieldType STRING_NOT_STORED = new FieldType();
  static {
    STRING_NOT_STORED.setOmitNorms(true);
    STRING_NOT_STORED.setIndexOptions(IndexOptions.DOCS);
    STRING_NOT_STORED.setTokenized(false);
    STRING_NOT_STORED.setStored(false);
    STRING_NOT_STORED.freeze();
  }

  private static final FieldType TEXT_NOT_STORED = new FieldType();
  static {
    TEXT_NOT_STORED.setOmitNorms(false);
    TEXT_NOT_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
    TEXT_NOT_STORED.setTokenized(true);
    TEXT_NOT_STORED.setStored(false);
    TEXT_NOT_STORED.freeze();
  }

  @Deprecated
  private static final FieldType TEXT_STORED_WITH_TV = new FieldType(TextField.TYPE_STORED);
  static {
    TEXT_STORED_WITH_TV.setStoreTermVectors(true);
    TEXT_STORED_WITH_TV.setStoreTermVectorOffsets(true);
    TEXT_STORED_WITH_TV.setStoreTermVectorPositions(true);
    TEXT_STORED_WITH_TV.freeze();
  }

  @Deprecated
  private static final FieldType TEXT_NOT_STORED_WITH_TV = new FieldType(TEXT_NOT_STORED);
  static {
    TEXT_NOT_STORED_WITH_TV.setStoreTermVectors(true);
    TEXT_NOT_STORED_WITH_TV.setStoreTermVectorOffsets(true);
    TEXT_NOT_STORED_WITH_TV.setStoreTermVectorPositions(true);
    TEXT_NOT_STORED_WITH_TV.freeze();
  }

  private static final FieldType TEXT_NOT_STORED_WITH_TV_YES = new FieldType(TEXT_NOT_STORED);
  static {
    TEXT_NOT_STORED_WITH_TV_YES.setStoreTermVectors(true);
    TEXT_NOT_STORED_WITH_TV_YES.freeze();
  }

  private static final FieldType TEXT_NOT_STORED_WITH_TV_POSITIONS = new FieldType(TEXT_NOT_STORED);
  static {
    TEXT_NOT_STORED_WITH_TV_POSITIONS.setStoreTermVectors(true);
    TEXT_NOT_STORED_WITH_TV_POSITIONS.setStoreTermVectorPositions(true);
    TEXT_NOT_STORED_WITH_TV_POSITIONS.freeze();
  }

  private static final FieldType TEXT_NOT_STORED_WITH_TV_OFFSETS = new FieldType(TEXT_NOT_STORED);
  static {
    TEXT_NOT_STORED_WITH_TV_OFFSETS.setStoreTermVectors(true);
    TEXT_NOT_STORED_WITH_TV_OFFSETS.setStoreTermVectorOffsets(true);
    TEXT_NOT_STORED_WITH_TV_OFFSETS.freeze();
  }

  private static final FieldType TEXT_NOT_STORED_WITH_TV_POSITIONS_OFFSETS = new FieldType(TEXT_NOT_STORED);
  static {
    TEXT_NOT_STORED_WITH_TV_POSITIONS_OFFSETS.setStoreTermVectors(true);
    TEXT_NOT_STORED_WITH_TV_POSITIONS_OFFSETS.setStoreTermVectorPositions(true);
    TEXT_NOT_STORED_WITH_TV_POSITIONS_OFFSETS.setStoreTermVectorOffsets(true);
    TEXT_NOT_STORED_WITH_TV_POSITIONS_OFFSETS.freeze();
  }

  public LuceneDocument() {
    doc = new Document();
  }

  @Deprecated
  public LuceneDocument addStringField(String name, String value, boolean store) {
    Store stored = (store) ? Field.Store.YES : Field.Store.NO;
    doc.add(new StringField(name, value, stored));
    return this;
  }

  @Deprecated
  public LuceneDocument addTextField(String name, String value, boolean store) {
    FieldType type = (store) ? TEXT_STORED_WITH_TV : TEXT_NOT_STORED_WITH_TV;
    doc.add(new Field(name, value, type));
    return this;
  }

  public LuceneDocument addStringField(String name, BytesRef value) {
    doc.add(new Field(name, value, STRING_NOT_STORED));
    return this;
  }

  public LuceneDocument addTextField(String name, Reader value, String termvector) {
    FieldType ftype = null;
    if (termvector.equals(TermVector.NO.toString())) {
      ftype = TEXT_NOT_STORED;
    } else if (termvector.equals(TermVector.YES.toString())) {
      ftype = TEXT_NOT_STORED_WITH_TV_YES;
    } else if (termvector.equals(TermVector.WITH_POSITIONS.toString())) {
      ftype = TEXT_NOT_STORED_WITH_TV_POSITIONS;
    } else if (termvector.equals(TermVector.WITH_OFFSETS.toString())) {
      ftype = TEXT_NOT_STORED_WITH_TV_OFFSETS;
    } else if (termvector.equals(TermVector.WITH_POSITIONS_OFFSETS.toString())) {
      ftype = TEXT_NOT_STORED_WITH_TV_POSITIONS_OFFSETS;
    }
    doc.add(new Field(name, value, ftype));
    return this;
  }

  public LuceneDocument addStoredField(String name, BytesRef value) {
    doc.add(new Field(name, value, STORED));
    return this;
  }

  public Document toDocument() {
    return doc;
  }

  @Deprecated
  public static LuceneDocument addFieldDeprecated(LuceneDocument from, String field, String value, Map<String,Map<String,Object>> fieldsInfo) {
    String fieldtype = (String)fieldsInfo.get(field).get(LuceneClient.ATTR_FIELDTYPE);
    boolean store = (boolean)Objects.firstNonNull(fieldsInfo.get(field).get(LuceneClient.ATTR_STORE), false);

    if (fieldtype.equals(LuceneClient.FieldType.TEXT.toString())) {
      from.addTextField(field, value, store);
    } else if (fieldtype.equals(LuceneClient.FieldType.STRING.toString())) {
      from.addStringField(field, value, store);
    }

    @SuppressWarnings("unchecked")
    List<String> copyFields = (List<String>)Objects.firstNonNull(fieldsInfo.get(field).get(LuceneClient.ATTR_COPY_TO), new ArrayList<String>());
    for (String tofield : copyFields) {
      from = addFieldDeprecated(from, tofield, value, fieldsInfo);
    }
    return from;
  }

  public static LuceneDocument addField(LuceneDocument from, String field, Object value, Map<String,Map<String,Object>> fieldsInfo) throws IOException {
    String type = (String)fieldsInfo.get(field).get(LuceneClient.ATTR_FIELDTYPE);
    boolean store = (boolean)Objects.firstNonNull(fieldsInfo.get(field).get(LuceneClient.ATTR_STORE), false);
    String termvector = (String)Objects.firstNonNull(fieldsInfo.get(field).get(LuceneClient.ATTR_TERM_VECTOR), TermVector.NO.toString());
    @SuppressWarnings("unchecked")
    List<String> copyFields = (List<String>)Objects.firstNonNull(fieldsInfo.get(field).get(LuceneClient.ATTR_COPY_TO), new ArrayList<String>());

    if (value instanceof InputStream) {
      byte[] b = ByteStreams.toByteArray((InputStream)value);
      BytesRef br = new BytesRef(b);

      if (type.equals(LuceneClient.FieldType.TEXT.toString())) {
        from.addTextField(field, ByteSource.wrap(BytesRef.deepCopyOf(br).bytes).asCharSource(StandardCharsets.UTF_8).openBufferedStream(), termvector);
      } else if (type.equals(LuceneClient.FieldType.STRING.toString())) {
        from.addStringField(field, BytesRef.deepCopyOf(br));
      }
      if (store) {
        from.addStoredField(field, BytesRef.deepCopyOf(br));
      }
      for (String tofield : copyFields) {
        InputStream toValue = new ByteArrayInputStream(BytesRef.deepCopyOf(br).bytes);
        from = addField(from, tofield, toValue, fieldsInfo);
      }
    }

    if (value instanceof String) {
      byte[] b = value.toString().getBytes(StandardCharsets.UTF_8);
      BytesRef br = new BytesRef(b);

      if (type.equals(LuceneClient.FieldType.TEXT.toString())) {
        from.addTextField(field, new StringReader(BytesRef.deepCopyOf(br).utf8ToString()), termvector);
      } else if (type.equals(LuceneClient.FieldType.STRING.toString())) {
        from.addStringField(field, BytesRef.deepCopyOf(br));
      }
      if (store) {
        from.addStoredField(field, BytesRef.deepCopyOf(br));
      }
      for (String tofield : copyFields) {
        String toValue = new String(BytesRef.deepCopyOf(br).bytes, StandardCharsets.UTF_8);
        from = addField(from, tofield, toValue, fieldsInfo);
      }
    }

    return from;
  }

}
