/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package jaxp;

import org.xml.sax.*;
import java.io.*;

/**
 * Class to obtain source data for tests.  Allows test data to be moved w/o
 * changing tests themselves.
 *
 * @author Edwin Goei
 */
public class InputData extends InputSource {
    static String dataPrefix = "tests/jaxp/data/";
    private String uri;

    /**
     * @param sourceId an identifier corresponding to input data and not
     *                 necessarily to a particular file
     */
    public InputData(String sourceId) throws Exception {
        super(dataPrefix + sourceId);
        uri = dataPrefix + sourceId;
    }

    public String toURIString() throws Exception {
        return new File(uri).toURL().toString();
    }
}
