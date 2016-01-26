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
	private static enum MODEL{
		SENTENCE, TOKENIZER, PEOPLE, LOCATIONS, ORGANIZATIONS;
	}
	
	// Specification nodes and values
    public static final String NODE_SMODEL_PATH = "SModelPath";
    public static final String NODE_TMODEL_PATH = "TModelPath";
    public static final String NODE_PMODEL_PATH = "PModelPath";
    public static final String NODE_LMODEL_PATH = "LModelPath";
    public static final String NODE_OMODEL_PATH = "OModelPath";

    public static final String ATTRIBUTE_VALUE = "value";
    
    private static SentenceModel sModel = null;
    private static TokenizerModel tModel = null;
    private static TokenNameFinderModel pModel = null;
    private static TokenNameFinderModel lModel = null;
    private static TokenNameFinderModel oModel = null;
    
    private static synchronized void initializeModel(MODEL m, String path) throws InvalidFormatException, FileNotFoundException, IOException{
    	if(sModel == null && m == MODEL.SENTENCE)
    		sModel = new SentenceModel(new FileInputStream(path));
    	if(tModel == null && m == MODEL.TOKENIZER)
    		tModel = new TokenizerModel(new FileInputStream(path));
    	if(pModel == null && m == MODEL.PEOPLE)
    		pModel = new TokenNameFinderModel(new FileInputStream(path));
    	if(lModel == null && m == MODEL.LOCATIONS)
    		lModel = new TokenNameFinderModel(new FileInputStream(path));
    	if(oModel == null && m == MODEL.ORGANIZATIONS)
    		oModel = new TokenNameFinderModel(new FileInputStream(path));
    }
    
    public static final SentenceDetector sentenceDetector(String path) throws InvalidFormatException, FileNotFoundException, IOException{
    	if(sModel == null)
    		initializeModel(MODEL.SENTENCE, path);
        return new SentenceDetectorME(sModel);
    }
    
    public static final Tokenizer tokenizer(String path) throws InvalidFormatException, FileNotFoundException, IOException{
    	if(tModel == null)
    		initializeModel(MODEL.TOKENIZER, path);
        return new TokenizerME(tModel);
    }
    
    public static final NameFinderME peopleFinder(String path) throws InvalidFormatException, FileNotFoundException, IOException{
    	if(pModel == null)
    		initializeModel(MODEL.PEOPLE, path);
        return new NameFinderME(pModel);
    }
    
    public static final NameFinderME locationFinder(String path) throws InvalidFormatException, FileNotFoundException, IOException{
    	if(lModel == null)
    		initializeModel(MODEL.LOCATIONS, path);
        return new NameFinderME(lModel);
    }
    
    public static final NameFinderME organizationFinder(String path) throws InvalidFormatException, FileNotFoundException, IOException{
    	if(oModel == null)
    		initializeModel(MODEL.ORGANIZATIONS, path);
        return new NameFinderME(oModel);
    }


    

}
