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

package xni.parser;

import org.apache.xerces.parsers.AbstractSAXParser;

/**
 * This parser class implements a SAX parser that can parse simple
 * comma-separated value (CSV) files.
 * 
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class CSVParser
    extends AbstractSAXParser {

    //
    // Constructors
    //

    /** Constructs a SAX like parser using the CSV configuration. */
    public CSVParser() {
        super(new CSVConfiguration());
    } // <init>()

} // class CSVParser
