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

/**
 * A simple Assertion class (a hack really ;-) to report the source line number
 * where an assertion fails.
 */


package dom.util;

import java.io.StringWriter;
import java.io.PrintWriter;

public class Assertion {

    public static boolean verify(boolean result) {
	return verify(result, null);
    }

    public static boolean verify(boolean result, String error) {
	if (!result) {
	    System.err.print("Assertion failed: ");
	    if (error != null) {
		System.err.print(error);
	    }
	    System.err.println();
	    System.err.println(getSourceLocation());
	}
	return result;
    }

    public static boolean equals(String s1, String s2) {
        boolean result = ((s1 != null && s1.equals(s2))
			  || (s1 == null && s2 == null));
	if (!result) {
	    verify(result);
	    System.err.println("  was: equals(" + s1 + ", \"" + s2 + "\")");
	}
	return result;
    }

    public static String getSourceLocation() {
	RuntimeException ex = new RuntimeException("assertion failed");
	StringWriter writer = new StringWriter();
	PrintWriter printer = new PrintWriter(writer);
	ex.printStackTrace(printer);
	String buf = writer.toString();
	// skip the first line as well as every line related to this class
	int index = buf.lastIndexOf("dom.util.Assertion.");
	index = buf.indexOf('\n', index);
	return buf.substring(index + 1);
    }
}
