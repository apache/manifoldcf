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
import java.util.Map;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

public class TikaParser {

  private final Parser parser;

  public TikaParser(final String tikaConfig)
    throws ManifoldCFException {
    if (tikaConfig == null || tikaConfig.length() == 0) {
      parser = new AutoDetectParser();
    } else {
      final InputStream is = new ByteArrayInputStream(tikaConfig.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      try {
        final TikaConfig conf = new TikaConfig(is);
        parser = new AutoDetectParser(conf);
      } catch (TikaException | IOException | SAXException e) {
        throw new ManifoldCFException(e.getMessage(), e);
      }
    }
  }
  
  /*
      Map<MediaType, Parser> parsers = ((AutoDetectParser) parser).getParsers();
      parsers.put(MediaType.APPLICATION_XML, new HtmlParser());
      ((AutoDetectParser) parser).setParsers(parsers);
  */

  public static ContentHandler newWriteOutBodyContentHandler(Writer w, int writeLimit) {
    final ContentHandler writeOutContentHandler = new WriteOutContentHandler(w, writeLimit);
    return new BodyContentHandler(writeOutContentHandler);
  }

  public void parse(InputStream stream, Metadata metadata, ContentHandler handler)
    throws IOException, SAXException, TikaException {
    ParseContext context = new ParseContext();
    context.set(Parser.class, parser);
    parser.parse(stream, handler, metadata, context);
  }

}
