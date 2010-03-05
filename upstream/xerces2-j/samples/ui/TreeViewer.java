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
package ui;

/*
 *
 * command line invocation wrapper for TreeViewer
 *
 * @version
 */

public class TreeViewer {

    public TreeViewer() {
        System.out.println("TreeViewer is no longer an instantiable class.  Please use XMLTreeView instead.");
        throw new RuntimeException();
    }

    public TreeViewer(String title, String filename) {
        System.out.println("TreeViewer is no longer an instantiable class.  Please use XMLTreeView instead.");
        throw new RuntimeException();
    }

    public static void main(String[] argv) {
        try {
            Class.forName("javax.swing.JFrame");
        } catch (ClassNotFoundException cnfe) {
            System.out.println("Couldn't load class javax.swing.JFrame.");
            System.out.println("This sample now uses Swing version 1.1.  Couldn't find the Swing 1.1 classes, please check your CLASSPATH settings.");
            System.exit(1);
        }

        TreeView.main(argv);
    }

}

