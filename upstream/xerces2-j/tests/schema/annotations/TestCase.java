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

package schema.annotations;

import java.io.File;
import java.net.URL;

/**
 * @author Neil Delima, IBM
 * @version $Id$
 */
public class TestCase extends junit.framework.TestCase {

    public TestCase() {
    }

    public TestCase(String test) {
        super(test);
    }

    /**
     * 
     */
    protected String getResourceURL(String path) {
        // build the location URL of the document
        String packageDir = this.getClass().getPackage().getName().replace('.',
                File.separatorChar);
        String documentPath = packageDir + "/" + path;
        URL url = ClassLoader.getSystemResource(documentPath);
        if (url == null) {
            fail ("Couldn't find xml file for test: " + documentPath);
        }
        // = getClass().getClassLoader().getResource(path);
        
        // System.out.println(url.toExternalForm());
        return url.toExternalForm();
    }

    /**
     * 
     */
    protected String trim(String toTrim) {
        String replaced = toTrim.replace('\t', ' ');
        replaced =  replaced.replace('\n', ' ');
        replaced =  replaced.trim();

        int i = 0, j = 0;
        char[] src = replaced.toCharArray();
        char[] dest = new char[src.length]; 
        
        while (i < src.length) {
            if (src [i] != ' ' ) {
                dest[j] = src [i];
                j++;
            } 
            i++;
        }
        return String.copyValueOf(dest,0,j-1);
    }

    /**
     * 
     * @param args
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(TestCase.class);
    }

}
