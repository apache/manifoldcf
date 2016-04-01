/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.agents.transformation.opennlp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InvalidFormatException;

public class OpenNlpExtractorConfig
{
  
  // Specification nodes and values
  public static final String NODE_SMODEL_PATH = "SModelPath";
  public static final String NODE_TMODEL_PATH = "TModelPath";
  public static final String NODE_FINDERMODEL = "FinderModel";

  public static final String ATTRIBUTE_VALUE = "value";
  public static final String ATTRIBUTE_PARAMETERNAME = "parametername";
  public static final String ATTRIBUTE_MODELFILE = "modelfile";
    
  private final static Map<File,SentenceModel> sModels = new HashMap<>();
  private final static Map<File,TokenizerModel> tModels = new HashMap<>();
  private final static Map<File,TokenNameFinderModel> tnfModels = new HashMap<>();
    
  protected static SentenceModel loadSModel(final File path) throws InvalidFormatException, FileNotFoundException, IOException {
    synchronized (sModels) {
      SentenceModel sd = sModels.get(path);
      if (sd == null) {
        final InputStream is = new FileInputStream(path);
        try {
          sd = new SentenceModel(is);
        } finally {
          is.close();
        }
        sModels.put(path, sd);
      }
      return sd;
    }
  }

  protected static TokenizerModel loadTModel(final File path) throws InvalidFormatException, FileNotFoundException, IOException {
    synchronized (tModels) {
      TokenizerModel sd = tModels.get(path);
      if (sd == null) {
        final InputStream is = new FileInputStream(path);
        try {
          sd = new TokenizerModel(is);
        } finally {
          is.close();
        }
        tModels.put(path, sd);
      }
      return sd;
    }
  }
  
  protected static TokenNameFinderModel loadTnfModel(final File path) throws InvalidFormatException, FileNotFoundException, IOException {
    synchronized (tnfModels) {
      TokenNameFinderModel sd = tnfModels.get(path);
      if (sd == null) {
        final InputStream is = new FileInputStream(path);
        try {
          sd = new TokenNameFinderModel(is);
        } finally {
          is.close();
        }
        tnfModels.put(path, sd);
      }
      return sd;
    }
  }

  public static final SentenceDetector sentenceDetector(File path) throws InvalidFormatException, FileNotFoundException, IOException{
    return new SentenceDetectorME(loadSModel(path));
  }
    
  public static final Tokenizer tokenizer(File path) throws InvalidFormatException, FileNotFoundException, IOException{
    return new TokenizerME(loadTModel(path));
  }
    
  public static final NameFinderME finder(File path) throws InvalidFormatException, FileNotFoundException, IOException{
    return new NameFinderME(loadTnfModel(path));
  }
    

}
