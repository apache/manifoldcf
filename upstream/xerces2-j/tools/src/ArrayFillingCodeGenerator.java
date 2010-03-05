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
 
package org.apache.xerces.util;

import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * This class can be used to generate the code for 
 * an efficient initialization for a given array.
 * 
 * @author Michael Glavassevich, IBM
 * 
 * @version $Id$
 */
public class ArrayFillingCodeGenerator {

    /**
     * <p>Generates code for an efficient initialization for
     * a given byte array.</p>
     * 
     * @param arrayName the name of the array to be generated
     * @param array the array for which code will be generated
     * @param out the stream where the code will be written
     */
    public static void generateByteArray(String arrayName, 
                                         byte[] array, 
                                         OutputStream out) {

        PrintWriter writer = new PrintWriter(out);
        int cursor = 0;
        int i;
        byte last = 0;
        for (i = 0; i < array.length; ++i) {
            if (last == array[i]) {
                continue;
            }
            if (i - cursor > 1 && last != (byte) 0) {
                writer.print("Arrays.fill(" + arrayName + ", " + cursor + ", " + i + ", (byte) " + last + " );");
                writer.println(" // Fill " + (i - cursor) + " of value (byte) " + last);
                writer.flush();
            }
            else if (i - cursor == 1 && array[cursor] != (byte) 0) {
                writer.println(arrayName + "[" + cursor + "] = " + array[cursor] + ";");
                writer.flush();
            }
            last = array[i];
            cursor = i;
        }
        if (i - cursor > 1 && last != (byte) 0) {
            writer.print("Arrays.fill(" + arrayName + ", " + cursor + ", " + i + ", (byte) " + last + " );");
            writer.println(" // Fill " + (i - cursor) + " of value (byte) " + last);
            writer.flush();
        }
        else if (i - cursor == 1 && array[cursor] != (byte) 0) {
            writer.println(arrayName + "[" + cursor + "] = " + array[cursor] + ";");
            writer.flush();
        }
        writer.flush();
        writer.close();
    }
}
