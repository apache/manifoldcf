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
package org.apache.manifoldcf.agents.transformation.tika;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

public class TikaParser {

  private final TikaLoader loader;
  private final Parser parser;

  public TikaParser(final String tikaConfig)
    throws ManifoldCFException {
    final ClassLoader classLoader = getClass().getClassLoader();
    try {
      if (tikaConfig == null || tikaConfig.isBlank()) {
        loader = TikaLoader.loadDefault(classLoader);
      } else {
        final String trimmed = tikaConfig.trim();
        if (trimmed.startsWith("<")) {
          throw new ManifoldCFException(
            "Invalid Tika configuration: Apache Tika 4.x uses JSON configuration format instead of legacy XML tika-config.xml");
        }
        try (InputStream is = new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8))) {
          final TikaJsonConfig conf = TikaJsonConfig.load(is);
          loader = TikaLoader.load(conf, classLoader);
        }
      }
      parser = loader.loadAutoDetectParser();
    } catch (ManifoldCFException e) {
      throw e;
    } catch (Exception e) {
      throw new ManifoldCFException(e.getMessage(), e);
    }
  }

  public static ContentHandler newWriteOutBodyContentHandler(Writer w, int writeLimit) {
    final ContentHandler writeOutContentHandler = new WriteOutContentHandler(w, writeLimit);
    return new BodyContentHandler(writeOutContentHandler);
  }

  public void parse(InputStream stream, Metadata metadata, ContentHandler handler)
    throws IOException, SAXException, TikaException {
    ParseContext context = loader.loadParseContext();
    context.set(Parser.class, parser);
    try (TikaInputStream tis = TikaInputStream.get(stream, metadata)) {
      parser.parse(tis, handler, metadata, context);
    }
  }

}
