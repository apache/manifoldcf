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
package org.apache.manifoldcf.crawler.connectors.rss;


/** Constants for the RSS connector configuration.
*/
public class RSSConfig
{
  public static final String _rcsid = "@(#)$Id$";


  // Constants describing the configuration structure.  This structure describes the "how" of
  // fetching page data - e.g. bandwidth and fetch constraints, adherance to robots conventions,
  // etc.
  // For the throttling part of the connector, the scheduler handles average fetch-rate limits.
  // The per-connection configuration describes the maximum number of connections per some user-defined criteria, as
  // well as bandwidth maximums and fetch rate absolute maximums.
  //

  // Parameters
  
  /** Robots usage parameter */
  public static final String PARAMETER_ROBOTSUSAGE = "Robots usage";
  /** Email parameter */
  public static final String PARAMETER_EMAIL = "Email address";
  /** Max kilobytes per second per server */
  public static final String PARAMETER_BANDWIDTH = "KB per second";
  /** Max simultaneous open connections per server */
  public static final String PARAMETER_MAXOPEN = "Max server connections";
  /** Max fetches per minute per server */
  public static final String PARAMETER_MAXFETCHES = "Max fetches per minute";
  /** The throttle group name */
  public static final String PARAMETER_THROTTLEGROUP = "Throttle group";
  /** Proxy host name */
  public static final String PARAMETER_PROXYHOST = "Proxy host";
  /** Proxy port */
  public static final String PARAMETER_PROXYPORT = "Proxy port";
  /** Proxy auth domain */
  public static final String PARAMETER_PROXYAUTHDOMAIN = "Proxy authentication domain";
  /** Proxy auth username */
  public static final String PARAMETER_PROXYAUTHUSERNAME = "Proxy authentication user name";
  /** Proxy auth password */
  public static final String PARAMETER_PROXYAUTHPASSWORD = "Proxy authentication password";

  // Constants used in the document specification part of the configuration structure.
  // This describes the "what" of the job.

  /** Feed specification node.  Has one attribute, 'url'. */
  public static final String NODE_FEED = "feed";
  /** Map entry specification node.  Has two attributes: 'match' and 'map'. */
  public static final String NODE_MAP = "map";
  /** Feed timeout.  Attribute = 'value' */
  public static final String NODE_FEEDTIMEOUT = "feedtimeout";
  /** Feed rescan time.  Attribute = 'value' */
  public static final String NODE_FEEDRESCAN = "feedrescan";
  /** Min feed rescan time.  Attribute = 'value' */
  public static final String NODE_MINFEEDRESCAN = "minfeedrescan";
  /** Bad feed rescan time.  Attribute = 'value' */
  public static final String NODE_BADFEEDRESCAN = "badfeedrescan";
  /** Access node (forced ACLs).  Attribute is 'token' */
  public static final String NODE_ACCESS = "access";
  /** Dechromed mode.  Attribute is 'mode' */
  public static final String NODE_DECHROMEDMODE = "dechromedmode";
  /** Chromed mode.  Attribute is 'mode' */
  public static final String NODE_CHROMEDMODE = "chromedmode";
  /** Url normalization specification; attrs are 'regexp', 'description', 'reorder',
  * 'javasessionremoval', 'aspsessionremoval', 'bvsessionremoval', 'phpsessionremoval' */
  public static final String NODE_URLSPEC = "urlspec";
  /** Exclude regexps node.  The value of this node contains the regexps that
  * if any one matches, causes the URL to be excluded from the crawl.  These
  * regexps are newline separated, and # starts a comment.  */
  public static final String NODE_EXCLUDES = "excludes";
  
  // Attributes
  
  /** Url attribute */
  public static final String ATTR_URL = "url";
  /** Value attribute */
  public static final String ATTR_VALUE = "value";
  /** Name attribute */
  public static final String ATTR_NAME = "name";
  /** Token attribute */
  public static final String ATTR_TOKEN = "token";
  /** Mode attribute */
  public static final String ATTR_MODE = "mode";
  /** Regexp attribute */
  public static final String ATTR_REGEXP = "regexp";
  /** Description attribute */
  public static final String ATTR_DESCRIPTION = "description";
  /** Reorder attribute */
  public static final String ATTR_REORDER = "reorder";
  /** Javasessionremoval attribute */
  public static final String ATTR_JAVASESSIONREMOVAL = "javasessionremoval";
  /** Aspsessionremoval attribute */
  public static final String ATTR_ASPSESSIONREMOVAL = "aspsessionremoval";
  /** Phpsessionremoval attribute */
  public static final String ATTR_PHPSESSIONREMOVAL = "phpsessionremoval";
  /** Bvsessionremoval attribute */
  public static final String ATTR_BVSESSIONREMOVAL = "bvsessionremoval";
  /** Match attribute */
  public static final String ATTR_MATCH = "match";
  /** Map attribute */
  public static final String ATTR_MAP = "map";
  
  // Values
  
  // Robots usage values
  /** All */
  public static final String VALUE_ALL = "all";
  /** None */
  public static final String VALUE_NONE = "none";
  /** Data */
  public static final String VALUE_DATA = "data";
  
  // Dechromedmode mode values
  /** None */
  //public static final String VALUE_NONE = "none";
  /** Description */
  public static final String VALUE_DESCRIPTION = "description";
  /** Content */
  public static final String VALUE_CONTENT = "content";
  
  // Chromedmode mode values
  /** Use */
  public static final String VALUE_USE = "use";
  /** Skip */
  public static final String VALUE_SKIP = "skip";
  /** Metadata */
  public static final String VALUE_METADATA = "metadata";
  
  // Yes/no
  /** No */
  public static final String VALUE_NO = "no";
  /** Yes */
  public static final String VALUE_YES = "yes";
  
}


