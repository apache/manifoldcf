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
package org.apache.manifoldcf.authorities.mappers.regexp;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import org.apache.manifoldcf.authorities.system.Logging;
import org.apache.manifoldcf.authorities.system.ManifoldCF;

import java.io.*;
import java.util.*;

/** This is the regexp mapper implementation, which uses a regular expression to manipulate a user name.
*/
public class RegexpMapper extends org.apache.manifoldcf.authorities.mappers.BaseMappingConnector
{
  public static final String _rcsid = "@(#)$Id$";

  // Match map for username
  private MatchMap matchMap = null;

  /** Constructor.
  */
  public RegexpMapper()
  {
  }

  /** Close the connection.  Call this before discarding the mapping connection.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    matchMap = null;
    super.disconnect();
  }

  // All methods below this line will ONLY be called if a connect() call succeeded
  // on this instance!

  private MatchMap getSession()
    throws ManifoldCFException
  {
    if (matchMap == null)
      matchMap = new MatchMap(params.getParameter(RegexpParameters.userNameMapping));
    return matchMap;
  }

  /** Map an input user name to an output name.
  *@param userName is the name to map
  *@return the mapped user name
  */
  @Override
  public String mapUser(String userName)
    throws ManifoldCFException
  {
    MatchMap mm = getSession();
    
    String outputUserName = mm.translate(userName);
    
    if (Logging.mappingConnectors.isDebugEnabled())
      Logging.mappingConnectors.debug("RegexpMapper: Input user name '"+userName+"'; output user name '"+outputUserName+"'");
    
    return outputUserName;
  }

  // UI support methods.
  //
  // These support methods are involved in setting up authority connection configuration information. The configuration methods cannot assume that the
  // current authority object is connected.  That is why they receive a thread context argument.
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"RegexpMapper.UserMapping"));

    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfig()\n"+
"{\n"+
"  if (editconnection.usernameregexp.value != \"\" && !isRegularExpression(editconnection.usernameregexp.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"RegexpMapper.UserNameRegularExpressionMustBeValidRegularExpression") + "\");\n"+
"    editconnection.usernameregexp.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.usernameregexp.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"RegexpMapper.UserNameRegularExpressionCannotBeNull") + "\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"RegexpMapper.UserMapping") + "\");\n"+
"    editconnection.usernameregexp.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the authority connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String userNameMapping = parameters.getParameter(RegexpParameters.userNameMapping);
    if (userNameMapping == null)
      userNameMapping = "^(.*)\\\\@([A-Z|a-z|0-9|_|-]*)\\\\.(.*)$=$(2)\\\\$(1l)";
    MatchMap matchMap = new MatchMap(userNameMapping);

    String usernameRegexp = matchMap.getMatchString(0);
    String livelinkUserExpr = matchMap.getReplaceString(0);

    // The "User Mapping" tab
    if (tabName.equals(Messages.getString(locale,"RegexpMapper.UserMapping")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"RegexpMapper.UserNameRegularExpressionColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"40\" name=\"usernameregexp\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(usernameRegexp)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"RegexpMapper.UserExpressionColon") + "</nobr></td>\n"+
"    <td class=\"value\"><input type=\"text\" size=\"40\" name=\"livelinkuserexpr\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(livelinkUserExpr)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for "User Mapping" tab
      out.print(
"<input type=\"hidden\" name=\"usernameregexp\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(usernameRegexp)+"\"/>\n"+
"<input type=\"hidden\" name=\"livelinkuserexpr\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(livelinkUserExpr)+"\"/>\n"
      );
    }

  }
  
  /** Process a configuration post.
  * This method is called at the start of the authority connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    // User name parameters
    String usernameRegexp = variableContext.getParameter("usernameregexp");
    String livelinkUserExpr = variableContext.getParameter("livelinkuserexpr");
    if (usernameRegexp != null && livelinkUserExpr != null)
    {
      MatchMap matchMap = new MatchMap();
      matchMap.appendMatchPair(usernameRegexp,livelinkUserExpr);
      parameters.setParameter(RegexpParameters.userNameMapping,matchMap.toString());
    }

    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the authority connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    MatchMap matchMap = new MatchMap(parameters.getParameter(RegexpParameters.userNameMapping));

    String usernameRegexp = matchMap.getMatchString(0);
    String livelinkUserExpr = matchMap.getReplaceString(0);

    out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"RegexpMapper.UserNameRegularExpressionColon") + "</nobr></td>\n"+
"    <td class=\"value\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(usernameRegexp)+"</nobr></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"RegexpMapper.UserExpressionColon") + "</nobr></td>\n"+
"    <td class=\"value\"><nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(livelinkUserExpr)+"</nobr></td>\n"+
"  </tr>\n"+
"</table>\n"
    );

  }

}


