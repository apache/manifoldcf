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
package org.apache.manifoldcf.core.auth;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.core.interfaces.IAuth;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.system.Logging;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.LockManagerFactory;

public class LdapAuthenticator implements IAuth {

  private static final String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
  
  private static final String PROVIDER_URL_PROPERTY = "org.apache.manifoldcf.login.ldap.providerurl";
  private static final String SECURITY_AUTHENTICATION_TYPE = "org.apache.manifoldcf.login.ldap.securityauthenticationtype";
  private static final String SECURITY_PRINCIPLE = "org.apache.manifoldcf.login.ldap.securityprincipal";
  private static final String CONTEXT_SEARCH_QUERY = "org.apache.manifoldcf.login.ldap.contextsearchquery";
  private static final String SEARCH_ATTRIBUTE = "org.apache.manifoldcf.login.ldap.searchattribute";

  protected final String securityPrincipal;
  protected final String securityAuthenticationType;
  protected final String providerURLProperty;
  protected final String contextSearchQuery;
  protected final String searchAttribute;
  
  /** Constructor */
  public LdapAuthenticator(final IThreadContext threadContext)
    throws ManifoldCFException {
    securityPrincipal = LockManagerFactory.getStringProperty(threadContext,SECURITY_PRINCIPLE,"");
    securityAuthenticationType = LockManagerFactory.getStringProperty(threadContext,SECURITY_AUTHENTICATION_TYPE,"simple");
    providerURLProperty = LockManagerFactory.getStringProperty(threadContext,PROVIDER_URL_PROPERTY,"");
    contextSearchQuery = LockManagerFactory.getStringProperty(threadContext,CONTEXT_SEARCH_QUERY,"");
    searchAttribute = LockManagerFactory.getStringProperty(threadContext,SEARCH_ATTRIBUTE,"uid");
  }
  
  /**
   * @param userID
   * @param password
   * @return
   */
  private Hashtable<String, String> buildEnvironment(String userID,
      String password) {

    Hashtable<String, String> environment = new Hashtable<String, String>();

    environment.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);

    environment.put(Context.PROVIDER_URL,
        providerURLProperty);

    environment.put(Context.SECURITY_AUTHENTICATION,
        securityAuthenticationType);
    environment.put(
        Context.SECURITY_PRINCIPAL,
        substituteUser(securityPrincipal, userID));
    environment.put(Context.SECURITY_CREDENTIALS, password);

    return environment;
  }

  /**
   * @param source
   * @param substitution
   * @return
   */
  private static String substituteUser(String source, String substitution) {
    return source.replace("$(userID)", substitution);
  }

  /**
   * @param userId
   * @param password
   * @return
   */
  @Override
  public boolean verifyUILogin(final String userId, final String password)
    throws ManifoldCFException {
    return verifyLogin(userId, password);
  }
  
  @Override
  public boolean verifyAPILogin(final String userId, final String password)
    throws ManifoldCFException {
    return verifyLogin(userId, password);
  }

  protected boolean verifyLogin(final String userId, final String password)
    throws ManifoldCFException {

    boolean authenticated = false;

    if (StringUtils.isNotEmpty(userId) && StringUtils.isNotEmpty(password)) {

      try {

        Logging.misc
            .info("Authentication attempt for user = " + userId);

        // Create initial context
        DirContext ctx = new InitialDirContext(buildEnvironment(userId,
            password));

        NamingEnumeration results = null;
        try {

          SearchControls controls = new SearchControls();
          controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

          results = ctx
              .search("",
                  substituteUser(contextSearchQuery,
                      userId), controls);
          // is the user in the group?
          while (results.hasMore()) {
            SearchResult searchResult = (SearchResult) results
                .next();

            if (userId.equals(searchResult.getAttributes()
                .get(searchAttribute)
                .get())) {

              Logging.misc.info("Successfully authenticated : "
                  + userId);

              authenticated = true;
              break;
            }
          }

        } catch (Exception e) {
          Logging.misc.error("User not authenticated = " + userId
              + " exception = " + e.getMessage(), e);
          throw new ManifoldCFException("User not authenticated: "+e.getMessage(),e);
        } finally {

          if (results != null) {
            try {
              results.close();
            } catch (Exception e) {
              // do nothing
            }
          }
          if (ctx != null) {
            try {
              ctx.close();
            } catch (Exception e) {
              // do nothing
            }
          }
        }

      } catch (NamingException e) {
        Logging.misc.error("Exception authenticating user = " + userId
            + " exception = " + e.getMessage(), e);
        throw new ManifoldCFException("Exception authenticating user: "+e.getMessage(),e);
      }
    }
    return authenticated;
  }
  
  /** Check user capability */
  public boolean checkCapability(final String userId, final int capability)
    throws ManifoldCFException {
    // No current ability to distinguish roles
    return true;
  }

}