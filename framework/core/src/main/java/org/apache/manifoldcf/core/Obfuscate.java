/* $Id: Obfuscate.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core;

import java.io.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.*;

public class Obfuscate
{
  public static final String _rcsid = "@(#)$Id: Obfuscate.java 988245 2010-08-23 18:39:35Z kwright $";

  private Obfuscate()
  {
  }


  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.err.println("Usage: Obfuscate <string>");
      System.exit(1);
    }

    String string = args[0];
    try
    {
      String ob = ManifoldCF.obfuscate(string);
      UTF8Stdout.println(ob);
      //System.err.println("("+ManifoldCF.deobfuscate(ob)+")");
    }
    catch (ManifoldCFException e)
    {
      e.printStackTrace();
      System.exit(1);
    }
  }

}
