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
package org.apache.manifoldcf.core.tests;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.*;

/** This tester sets up a virtual browser and allows a sequence of testing to take place.  It's set
* up to allow this to be done in Java, even though the tester itself may well be running in Python.
* The eventual goal is to replace the Python browser emulator with a Java one, but we can't get there
* all in one goal.
*
* The paradigm used is one of a "virtual browser", which basically handles multiple windows and can
* emulate user activities, such as clicking a link or a button, filling in a field, etc.  Identification
* of each of these elements may have a language dependence, so I would anticipate that there would
* need to be a new set of tests for each localization we have.  Presumably it should be possible
* to come up with a structure at a level above this one in order to meet the goal of having the
* same test in a different language, so I'm not going to worry about that here.
*
* The tester works by basically accumulating a set of "instructions", and then firing them off at the
* end.  This set of instructions is then executed in an appropriate environment, and test feedback is
* returned.
*/
public class HTMLTester
{
  protected File currentTestFile = null;
  protected OutputStream currentOutputStream = null;
  protected BufferedWriter currentWriter = null;
  protected int variableCounter;
  protected int currentIndentLevel;
  protected String virtualBrowserVarName;
  
  /** Constructor.  Create a test sequence object.
  */
  public HTMLTester()
  {
  }
  
  /** Set up for all tests.  Basically this grabs the necessary stuff out of resources
  * and writes it to the current directory.
  */
  @Before
  public void setup()
    throws Exception
  {
    copyResource("VirtualBrowser.py");
    copyResource("Javascript.py");
    // Delete any test files hanging around from before
    new File("test.py").delete();
  }
  
  protected void copyResource(String resName)
    throws Exception
  {
    OutputStream os = new FileOutputStream(new File(resName));
    try
    {
      InputStream is = getClass().getResourceAsStream(resName);
      try
      {
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        BufferedReader br = new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));
        while (true)
        {
          String line = br.readLine();
          if (line == null)
            break;
          bw.write(line);
          bw.newLine();
        }
        bw.flush();
      }
      finally
      {
        is.close();
      }
    }
    finally
    {
      os.close();
    }
  }
  
  /** Clean up the files we created.
  */
  @After
  public void teardown()
    throws Exception
  {
    closeAll();
    new File("Javascript.py").delete();
    new File("VirtualBrowser.py").delete();
  }
  
  /** Test to test the tester.
  */
  @Test
  public void TesterTest()
    throws Exception
  {
    newTest(Locale.US);
    executeTest();
  }
  
  /** Close the current output.
  */
  protected void closeAll()
    throws Exception
  {
    if (currentWriter != null)
    {
      currentWriter.flush();
      currentWriter = null;
    }
    if (currentOutputStream != null)
    {
      currentOutputStream.close();
      currentOutputStream = null;
    }
  }
  
  /** Begin a new test.  Call this when we're ready to start building a new UI test.
  */
  public void newTest(Locale desiredLocale)
    throws Exception
  {
    currentTestFile = new File("test.py");
    currentOutputStream = new FileOutputStream(currentTestFile);
    currentWriter = new BufferedWriter(new OutputStreamWriter(currentOutputStream,"ASCII"));
    variableCounter = 0;
    currentIndentLevel = 0;
    virtualBrowserVarName = getNextVariableName();
    
    emitLine("import time");
    emitLine("import sys");
    emitLine("sys.path.append(\".\")");
    emitLine("import VirtualBrowser");
    emitLine("if __name__ == '__main__':");
    currentIndentLevel++;
    emitLine("print 'Starting test'");
    emitLine(virtualBrowserVarName + " = VirtualBrowser.VirtualBrowser("+quotePythonString(desiredLocale.toString().replace("_","-"))+")");
  }
  
  /** Execute the test.  The virtual browser will be called and will perform the sequence of
  * activity described by the test.  If at any point an error occurs, an appropriate exception
  * will be thrown, with sufficient description to (hopefully) permit the problem to be tracked down.
  */
  public void executeTest()
    throws Exception
  {
    emitLine("print 'Test complete'");
    closeAll();
    // Now, execute the python command.
    Process p = Runtime.getRuntime().exec(new String[]{"python","test.py"});
    // Read from streams
    StreamConnector mStdOut = new StreamConnector( p.getErrorStream(), "Stderr: ", System.err );  
    StreamConnector mErrOut = new StreamConnector( p.getInputStream(), "Stdout: ", System.out );  
    mStdOut.start();  
    mErrOut.start(); 
    int exitCode = p.waitFor();
    mStdOut.abort();
    mErrOut.abort();
    mStdOut.join();
    mErrOut.join();
    if (exitCode != 0)
      throw new Exception("UI test failed; error code: "+exitCode);
    // After successful execution, remove the test file.
    if (currentTestFile != null)
    {
      currentTestFile.delete();
      currentTestFile = null;
    }
  }
  
  /** Create a string description for use later in the test.
  *@param value is the intended value of the string description.
  *@return the string description.
  */
  public StringDescription createStringDescription(String value)
    throws Exception
  {
    String variableName = getNextVariableName();
    if (value != null)
      emitLine(variableName + " = " + quotePythonString(value));
    else
      emitLine(variableName + " = None");
    return new StringDescription(variableName);
  }

  /** Create a string description for use later in the test.
  *@param values are the intended values of the string description, concatenated together.
  *@return the string description.
  */
  public StringDescription createStringDescription(StringDescription[] values)
    throws Exception
  {
    String variableName = getNextVariableName();
    if (values.length == 0)
      emitLine(variableName + " = " + quotePythonString(""));
    else
    {
      StringBuilder sb = new StringBuilder(variableName);
      sb.append(" = ");
      for (int i = 0; i < values.length ; i++)
      {
        if (i > 0)
          sb.append(" + ");
        sb.append(values[i].getVarName());
      }
      emitLine(sb.toString());
    }
    return new StringDescription(variableName);
  }

  /** Print a value.
  */
  public void printValue(StringDescription value)
    throws Exception
  {
    emitLine("print >> sys.stderr, "+value.getVarName());
  }
  
  /** Begin a loop.
  */
  public Loop beginLoop(int maxSeconds)
    throws Exception
  {
    String variableName = getNextVariableName();
    emitLine(variableName+" = time.time() + "+maxSeconds);
    emitLine("while True:");
    currentIndentLevel++;
    return new Loop(variableName);
  }
  
    
  /** Open virtual browser window, and send it to a specified URL.
  *@param url is the desired URL.
  *@return the window handle.  Use this whenever a window argument is required later.
  */
  public Window openMainWindow(String url)
    throws Exception
  {
    emitLine(virtualBrowserVarName + ".load_main_window(" + quotePythonString(url) + ")");
    return findWindow(null);
  }
  
  /** Find a window of a specific name, or null for the main window.
  *@param windowName is the name of the window, or null.
  *@return the window handle.
  */
  public Window findWindow(StringDescription windowName)
    throws Exception
  {
    String windowVar = getNextVariableName();
    if (windowName != null)
      emitLine(windowVar + " = " + virtualBrowserVarName + ".find_window("+windowName.getVarName()+")");
    else
      emitLine(windowVar + " = " + virtualBrowserVarName + ".find_window("+quotePythonString("")+")");
    return new Window(windowVar);
  }

  /** Calculate the next variable name */
  protected String getNextVariableName()
  {
    String rval = "var"+variableCounter;
    variableCounter++;
    return rval;
  }
  
  /** Quote a python string */
  protected String quotePythonString(String value)
  {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0 ; i < value.length() ; i++)
    {
      char c = value.charAt(i);
      if (c == '"')
        sb.append("\\").append(c);
      else
        sb.append(c);
    }
    sb.append("\"");
    return sb.toString();
  }
  
  /** Emit a python line.
  */
  protected void emitLine(String line)
    throws IOException
  {
    // Append to file with current indent.
    StringBuilder fullLine = new StringBuilder();
    for (int i = 0 ; i < currentIndentLevel ; i++)
    {
      fullLine.append("    ");
    }
    fullLine.append(line);
    currentWriter.write(fullLine.toString());
    currentWriter.newLine();
  }
  
  /** Window handle */
  public class Window
  {
    protected String windowVar;
    
    /** Create a window instance.
    */
    public Window(String windowVar)
    {
      this.windowVar = windowVar;
    }
    
    /** Check if a pattern is present or not.
    *@return a StringDescription that in fact describes a boolean condition; true if present.
    */
    public StringDescription isPresent(StringDescription regularExpression)
      throws Exception
    {
      String varName = getNextVariableName();
      emitLine(varName + " = "+windowVar+".is_present("+regularExpression.getVarName()+")");
      return new StringDescription(varName);
    }

    /** Check if a pattern is present or not.
    *@return a StringDescription that in fact describes a boolean condition; true if not present.
    */
    public StringDescription isNotPresent(StringDescription regularExpression)
      throws Exception
    {
      String varName = getNextVariableName();
      emitLine(varName + " = not "+windowVar+".is_present("+regularExpression.getVarName()+")");
      return new StringDescription(varName);
    }
    
    /** Look for a specific match in the current page data, and return the value of the specified group.
    *@return a description of the string found.  This can be used later in other commands to assess
    *  correctness of the page, or allow form data to be filled in.
    */
    public StringDescription findMatch(StringDescription regularExpression, int group)
      throws Exception
    {
      String varName = getNextVariableName();
      emitLine(varName + " = "+windowVar+".find_match("+regularExpression.getVarName()+",group="+group+")");
      return new StringDescription(varName);
    }

    /** Same as findMatch, but strips out newlines before it looks.
    */
    public StringDescription findMatchNoNewlines(StringDescription regularExpression, int group)
      throws Exception
    {
      String varName = getNextVariableName();
      emitLine(varName + " = "+windowVar+".find_match_no_newlines("+regularExpression.getVarName()+",group="+group+")");
      return new StringDescription(varName);
    }

    /** If the match is not found, the test will error out.
    */
    public void checkMatch(StringDescription regularExpression)
      throws Exception
    {
      emitLine(windowVar+".find_match("+regularExpression.getVarName()+")");
    }
    
    /** If the match is found, the test will error out.
    */
    public void checkNoMatch(StringDescription regularExpression)
      throws Exception
    {
      emitLine(windowVar+".check_no_match("+regularExpression.getVarName()+")");
    }
    
    /** Find a link.
    */
    public Link findLink(StringDescription altText)
      throws Exception
    {
      String linkVarName = getNextVariableName();
      emitLine(linkVarName + " = " + windowVar + ".find_link("+altText.getVarName()+")");
      return new Link(linkVarName);
    }
    
    /** Find a form.
    */
    public Form findForm(StringDescription formName)
      throws Exception
    {
      String formVarName = getNextVariableName();
      emitLine(formVarName + " = " + windowVar + ".find_form("+formName.getVarName()+")");
      return new Form(formVarName);
    }

    /** Find a button.
    */
    public Button findButton(StringDescription altText)
      throws Exception
    {
      String buttonVarName = getNextVariableName();
      emitLine(buttonVarName + " = " + windowVar + ".find_button("+altText.getVarName()+")");
      return new Button(buttonVarName);
    }
    
    /** Close this window.
    */
    public void closeWindow()
      throws Exception
    {
      emitLine(windowVar+".close_window()");
    }
  }
  
  /** Loop object.
  */
  public class Loop
  {
    protected String loopVarName;
    
    public Loop(String loopVarName)
    {
      this.loopVarName = loopVarName;
    }
    
    /** Break on condition being true.
    */
    public void breakWhenTrue(StringDescription condition)
      throws Exception
    {
      emitLine("if "+condition.getVarName()+":");
      currentIndentLevel++;
      emitLine("break");
      currentIndentLevel--;
    }
    
    /** End the loop.
    */
    public void endLoop()
      throws Exception
    {
      emitLine("time.sleep(1)");
      emitLine("if time.time() >= "+loopVarName+":");
      currentIndentLevel++;
      emitLine("raise Exception('Loop timed out')");
      currentIndentLevel--;
      currentIndentLevel--;
    }
  }
  
  /** Object representative of a virtual browser link.
  */
  public class Link
  {
    protected String linkVarName;
    
    public Link(String linkVarName)
    {
      this.linkVarName = linkVarName;
    }
    
    /** Click the link */
    public void click()
      throws Exception
    {
      emitLine(linkVarName + ".click()");
    }
  }
  
  /** Object representative of a virtual browser form.
  */
  public class Form
  {
    protected String formVarName;
    
    public Form(String formVarName)
    {
      this.formVarName = formVarName;
    }
    
    /** Find a file browser element, by data variable name.
    */
    public FileBrowser findFileBrowser(StringDescription dataName)
      throws Exception
    {
      String fileBrowserVarName = getNextVariableName();
      emitLine(fileBrowserVarName + " = " + formVarName + ".find_filebrowser("+dataName.getVarName()+")");
      return new FileBrowser(fileBrowserVarName);
    }
    
    /** Find a checkbox element, by data variable name and value.
    */
    public Checkbox findCheckbox(StringDescription dataName, StringDescription value)
      throws Exception
    {
      String checkboxVarName = getNextVariableName();
      emitLine(checkboxVarName + " = " + formVarName + ".find_checkbox("+dataName.getVarName()+","+value.getVarName()+")");
      return new Checkbox(checkboxVarName);
    }
    
    /** Find a radio button by variable name and value.
    */
    public Radiobutton findRadiobutton(StringDescription dataName, StringDescription value)
      throws Exception
    {
      String radiobuttonVarName = getNextVariableName();
      emitLine(radiobuttonVarName + " = " + formVarName + ".find_radiobutton("+dataName.getVarName()+","+value.getVarName()+")");
      return new Radiobutton(radiobuttonVarName);
    }
    
    /** Find a select box by data variable name.
    */
    public Selectbox findSelectbox(StringDescription dataName)
      throws Exception
    {
      String selectboxVarName = getNextVariableName();
      emitLine(selectboxVarName + " = " + formVarName + ".find_selectbox("+dataName.getVarName()+")");
      return new Selectbox(selectboxVarName);
    }
    
    /** Find a textarea/password field by data variable name.
    */
    public Textarea findTextarea(StringDescription dataName)
      throws Exception
    {
      String textareaVarName = getNextVariableName();
      emitLine(textareaVarName + " = " + formVarName + ".find_textarea("+dataName.getVarName()+")");
      return new Textarea(textareaVarName);
    }
  }
  
  /** Object representative of a file browser.
  */
  public class FileBrowser
  {
    protected String fileBrowserVarName;
    
    public FileBrowser(String fileBrowserVarName)
    {
      this.fileBrowserVarName = fileBrowserVarName;
    }
    
    public void setFile(StringDescription fileName, StringDescription contentType)
      throws Exception
    {
      emitLine(fileBrowserVarName + ".set_file("+fileName.getVarName()+","+contentType.getVarName()+")");
    }
  }
  
  /** Object representative of a checkbox.
  */
  public class Checkbox
  {
    protected String checkBoxVarName;
    
    public Checkbox(String checkBoxVarName)
    {
      this.checkBoxVarName = checkBoxVarName;
    }
    
    /** Select this checkbox */
    public void select()
      throws Exception
    {
      emitLine(checkBoxVarName + ".select()");
    }
    
    /** Deselect this checkbox */
    public void deselect()
      throws Exception
    {
      emitLine(checkBoxVarName + ".deselect()");
    }
  }
  
  /** Object representative of a radio button.
  */
  public class Radiobutton
  {
    protected String radioButtonVarName;
    
    public Radiobutton(String radioButtonVarName)
    {
      this.radioButtonVarName = radioButtonVarName;
    }
    
    /** Select this radio button */
    public void select()
      throws Exception
    {
      emitLine(radioButtonVarName + ".select()");
    }
  }
  
  /** Object representative of  a select box.
  */
  public class Selectbox
  {
    protected String selectBoxVarName;
    
    public Selectbox(String selectBoxVarName)
    {
      this.selectBoxVarName = selectBoxVarName;
    }

    /** Select a value (without CTRL button).
    * This works like a browser in that selecting in this way turns
    * off all other current selections. */
    public void selectValue(StringDescription selectedValue)
      throws Exception
    {
      emitLine(selectBoxVarName + ".select_value(" + selectedValue.getVarName() + ")");
    }

    /** Select a value using a regular expression (without CTRL button) */
    public void selectValueRegexp(StringDescription selectedValueRegexp)
      throws Exception
    {
      emitLine(selectBoxVarName + ".select_value_regexp(" + selectedValueRegexp.getVarName() + ")");
    }

    /** CTRL-select a value.
    * For multiselect boxes, this adds a new selection to those already
    * chosen.  For non-multi boxes, it works just like select_value. */
    public void multiSelectValue(StringDescription selectedValue)
      throws Exception
    {
      emitLine(selectBoxVarName + ".multi_select_value(" + selectedValue.getVarName() + ")");
    }
    
    
  }
  
  /** Object representative of a text area.
  */
  public class Textarea
  {
    protected String textAreaVarName;
    
    public Textarea(String textAreaVarName)
    {
      this.textAreaVarName = textAreaVarName;
    }
    
    /** Set the value.
    */
    public void setValue(StringDescription textValue)
      throws Exception
    {
      emitLine(textAreaVarName + ".set_value(" + textValue.getVarName() + ")");
    }
  }
  
  /** Object representative of a virtual browser button.
  */
  public class Button
  {
    protected String buttonVarName;
    
    public Button(String buttonVarName)
    {
      this.buttonVarName = buttonVarName;
    }
    
    public void click()
      throws Exception
    {
      emitLine(buttonVarName + ".click()");
    }
  }
  
  /** String description.  An instance of this class represents a string that will be located as the
  * browser emulator functions.  It can be used at various places as the test description is built.
  */
  public class StringDescription
  {
    protected String variableName;
    
    public StringDescription(String variableName)
    {
      this.variableName = variableName;
    }
    
    public String getVarName()
    {
      return variableName;
    }
  }
  
  /** Connector thread that allows for exec */
  protected static class StreamConnector extends Thread
  {
    protected InputStream inputStream;
    protected OutputStream outputStream;
    protected String prefix;
    protected boolean abortSignal;
    
    public StreamConnector(InputStream inputStream, String prefix, OutputStream outputStream)
    {
      this.inputStream = inputStream;
      this.prefix = prefix;
      this.outputStream = outputStream;
      abortSignal = false;
    }
    
    public void abort()
    {
      abortSignal = true;
    }
    
    public void run()
    {
      try
      {
        byte[] buffer = new byte[63356];
        while (true)
        {
          int amt = inputStream.read(buffer);
          if (amt == -1)
          {
            if (abortSignal)
              break;
            Thread.yield();
            continue;
          }
          outputStream.write(buffer,0,amt);
        }
      }
      catch (IOException e)
      {
        e.printStackTrace(System.err);
      }
    }
  }
  
}
