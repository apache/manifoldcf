/* $Id$ */

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
package org.apache.manifoldcf.less;


import com.github.sommeri.less4j.Less4jException;
import com.github.sommeri.less4j.LessCompiler;
import com.github.sommeri.less4j.LessSource;
import com.github.sommeri.less4j.core.DefaultLessCompiler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.*;

/**
 * Created by Kishore Kumar on 4/28/17.
 */
public class MCFLessCompiler
{
  final static String lessFolderPath = "./framework/crawler-ui/src/main/webapp/less";
  final static String cssFolderPath = "./framework/crawler-ui/src/main/webapp/css";
  boolean compress = false;

  private LessCompiler.Configuration createConfiguration(File cssOutut)
  {

    LessCompiler.Configuration configuration = new LessCompiler.Configuration();
    configuration.setCssResultLocation(new LessSource.FileSource(cssOutut));
    configuration.setCompressing(compress);

    return configuration;
  }

  public void compile(String inputLess)
  {
    String outputCSS = null;
    if (inputLess != null && !inputLess.isEmpty())
      outputCSS = FilenameUtils.removeExtension(inputLess) + ".css";
    compile(inputLess, outputCSS);
  }

  public void compile(String inputLess, String outputCSS)
  {
    compile(inputLess, outputCSS, false);
  }

  public void compile(String inputLess, String outputCSS, boolean compress)
  {
    this.compress = compress;
    LessCompiler compiler = new DefaultLessCompiler();
    try
    {
      LessCompiler.Configuration configuration = createConfiguration(new File(outputCSS));
      LessCompiler.CompilationResult compilationResult = compiler.compile(new LessSource.FileSource(new File(inputLess)), configuration);

      if (compilationResult.getWarnings().size() > 0)
      {
        for (LessCompiler.Problem warning : compilationResult.getWarnings())
        {
          System.err.println(warning);
        }
      }
      else
      {
        if (outputCSS != null && !outputCSS.isEmpty())
        {
          FileUtils.writeStringToFile(new File(outputCSS), compilationResult.getCss());
        }

        //Generate Source map if compressing
        if (compress)
        {
          String cssFileName = FilenameUtils.removeExtension(outputCSS);
          String mapFileName = cssFileName + ".map";
          FileUtils.writeStringToFile(new File(mapFileName), compilationResult.getSourceMap());
        }
      }
    }
    catch (Less4jException e)
    {
      e.printStackTrace();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }

  public static void main(String... args)
  {

    String outputLess = lessFolderPath + "/style.less";
    String outputCSS = cssFolderPath + "/style.css";
    boolean compress = false;

    MCFLessCompiler compiler = new MCFLessCompiler();

    switch (args.length)
    {
      case 1:
        outputLess = args[0];
        compiler.compile(outputLess);
        break;
      case 2:
        outputLess = args[0];
        outputCSS = args[1];
        compiler.compile(outputLess, outputCSS);
        break;
      case 3:
        outputLess = args[0];
        outputCSS = args[1];
        compress = Boolean.valueOf(args[2]);
        compiler.compile(outputLess, outputCSS, compress);
        break;
      default:
        compiler.compile(outputLess, outputCSS, compress);

    }

  }

}
