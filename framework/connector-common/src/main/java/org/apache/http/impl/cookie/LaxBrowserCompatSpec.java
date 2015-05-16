/* $Id$ */

/**`
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

package org.apache.http.impl.cookie;

import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.MalformedCookieException;

/** Class to override browser compatibility to make it not check cookie paths.  See CONNECTORS-97.
* The class must be in the package described because it requires a protected constructor from the
* class it extends.
*/
public class LaxBrowserCompatSpec extends RFC6265LaxSpec
{

  public LaxBrowserCompatSpec()
  {
    super(new BasicPathHandler()
    {
      @Override
      public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException
      {
        // No validation
      }
    },
    new BasicDomainHandler(),
    new LaxMaxAgeHandler(),
    new BasicSecureHandler(),
    new LaxExpiresHandler());
  }
    
}
