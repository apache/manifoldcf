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
package org.apache.manifoldcf.agents.transformation.forcedmetadata;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class FieldSource implements IDataSource {
  
  protected final static int CASE_EXACT = 0;
  protected final static int CASE_LOWER = 1;
  protected final static int CASE_UPPER = 2;
  
  protected final static Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  protected final static String[] EMPTY_STRING_ARRAY = new String[0];
  
  protected final FieldDataFactory rd;
  protected final String fieldName;
  protected final Pattern regExpPattern;
  protected final int groupNumber;
  protected final int caseSpecifier;

  protected String[] cachedValue;

  public FieldSource(final FieldDataFactory rd, final String fieldName, final String regExp, final String groupNumber)
    throws ManifoldCFException {
    this.rd = rd;
    this.fieldName = fieldName;
    if (regExp == null || regExp.length() == 0) {
      regExpPattern = null;
      this.groupNumber = 0;
      this.caseSpecifier = CASE_EXACT;
    } else {
      try {
        this.regExpPattern = Pattern.compile(regExp);
        if (groupNumber == null || groupNumber.length() == 0) {
          this.groupNumber = 0;
          this.caseSpecifier = CASE_EXACT;
        } else {
          final StringBuilder sb = new StringBuilder();
          int caseResult = CASE_EXACT;
          int i = 0;
          while (i < groupNumber.length()) {
            final char theChar = groupNumber.charAt(i++);
            if (theChar >= '0' && theChar <= '9')
              sb.append(theChar);
            else if (theChar == 'l')
              caseResult = CASE_LOWER;
            else if (theChar == 'u')
              caseResult = CASE_UPPER;
            else
              throw new ManifoldCFException("Regular expression group specifier '"+groupNumber+"' has illegal character '"+theChar+"'; should be a number, or number + l, or number + u");
          }
          if (sb.length() == 0)
            throw new ManifoldCFException("Regular expression group specifier '"+groupNumber+"' must include a number");
          this.caseSpecifier = caseResult;
          this.groupNumber = Integer.parseInt(sb.toString());
        }
      } catch (NumberFormatException e) {
        throw new ManifoldCFException("Regular expression group specifier '"+groupNumber+"': "+e.getMessage(),e);
      } catch (PatternSyntaxException e) {
        throw new ManifoldCFException("Regular expression '"+regExp+"': "+e.getMessage(),e);
      }
    }
  }
    
  @Override
  public int getSize()
    throws IOException, ManifoldCFException {
    return getRawForm().length;
  }
    
  @Override
  public Object[] getRawForm()
    throws IOException, ManifoldCFException {
    if (regExpPattern != null) {
      return calculateExtractedResult();
    }
    final Object[] rval = rd.getField(fieldName);
    if (rval == null)
      return EMPTY_OBJECT_ARRAY;
    return rval;
  }
    
  @Override
  public String[] getStringForm()
    throws IOException, ManifoldCFException {
    if (regExpPattern != null) {
      return calculateExtractedResult();
    }
    final String[] rval = rd.getFieldAsStrings(fieldName);
    if (rval == null)
      return EMPTY_STRING_ARRAY;
    return rval;
  }
  
  protected String[] calculateExtractedResult()
    throws IOException, ManifoldCFException {
    if (cachedValue == null) {
      final String[] resultSources = rd.getFieldAsStrings(fieldName);
      final List<String> resultList = new ArrayList<String>(resultSources.length);
      for (String x : resultSources) {
        final Matcher m = regExpPattern.matcher(x);
        if (m.find()) {
          String result = x.substring(m.start(groupNumber),m.end(groupNumber));
          switch (caseSpecifier) {
          case CASE_LOWER:
            result = result.toLowerCase(Locale.ROOT);
            break;
          case CASE_UPPER:
            result = result.toUpperCase(Locale.ROOT);
            break;
          case CASE_EXACT:
          default:
            break;
          }
          resultList.add(result);
        }
      }
      cachedValue = resultList.toArray(EMPTY_STRING_ARRAY);
    }
    return cachedValue;
  }
}
