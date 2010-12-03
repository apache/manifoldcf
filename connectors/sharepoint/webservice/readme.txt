# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

MCPermissions SharePoint Web Service
=============================

I.  SharePoint Web Services Model

The ManifoldCF SharePoint Connector only communicates with SharePoint
through web services.  SharePoint has a well-developed web services API,
along with a very convenient deployment model which allows additional,
custom web services to be easily deployed, even across entire SharePoint
server farms (their notion of a cluster).  The intention by Microsoft was clearly
to make it straightforward for third parties to develop and sell and/or distribute
custom web services which augment the capabilities of the SharePoint system.

SharePoint WebServices are usually written in C#.  SharePoint itself runs on the
.NET platform, so the language choice is not technically limited; however, I
believe that the primary internal API's for SharePoint are all in C#, which does
tend to push writers of SharePoint web services towards that language.


II.  SharePoint Hierarchy

SharePoint defines objects in a hierarchical manner, based on http or https urls
served by IIS.  SharePoint hooks into IIS by taking over an
IIS "website", and serving all paths for that port.  There is always a primary "root site",
which has an empty path, and there can be (optionally) a number of "virtual paths"
as well, each one of which functions like a root site in everything but name.
The path of a virtual path root typically has the form: "sites/xxx", but you can define
virtual paths with any prefix you want.

Beneath the primary root site and each virtual path root you can find subsites
(nested as deep as you like, so there are subsites of subsites etc.), libraries, lists,
and a bunch of other entities we don't really care about.  Documents are found only
in libraries (as far as we know to date).  Within a library you can also have folders,
which grant further structure to the library.

Here a simple example of a potential SharePoint url:

http://servername.domain:port/sites/virtualrootname1/subsitelevel1/subsitelevel2/library/folderlevel1/folderlevel2/document.xls

In this case, the url describes a document within a folder within an enclosing folder
within a library that is part of a subsite that is a child of a parent subsite that is the
child of a virtual path root.

SharePoint web service .aspx files are always deployed under the "_vti_bin" path
underneath each root site and subsite.


III.  SharePoint Security Model

SharePoint uses Active Directory security exclusively.  People with appropriate
rights can attach AD users or groups to various objects in SharePoint.  Rights are
always additive in SharePoint; there is no "deny" tokens.  In general, access rights are
inherited down the hierarchy, unless a user explicitly chooses to break the
inheritance and set access rights directly for a particular object (and potentially,
for its children too.)

A.  SharePoint 2.0 Security Model

In SharePoint 2.0, sometimes called SharePoint 2003, one can attach AD users or
groups to the following: The SharePoint website as a whole, sites (subsites too), and
libraries.  You cannot set security on folders or files ("items") directly, however.

B.  SharePoint 3.0 Security Model

In SharePoint 3.0, sometimes called SharePoint 2007, one can attach AD users or
groups to folders and files in, addition to the functionality provided in SharePoint 2.0.

IV.  SharePoint Connector security

In order for the SharePoint Connector to ingest documents with the proper security,
it must obtain the list of AD SIDs that apply to each document, and ingest those along
with the document.  This list must take into account SharePoint's notion of access
inheritance and override.

Luckily, SharePoint provides, out of the box, a web service that does precisely that for
the web site, sites, and libraries, called the "Permissions" web service.  You use this
service by providing an identifier (basically, a path) and a type ("web", "site", or "library"),
and the web service obtains the SIDs of the users and groups for you.  Each acl query
automatically handled the inheritance of SIDs properly also - so if you asked for the
SIDs for a library, it would take into account inheritance from sites and the global website.

This web service was sufficient for the SharePoint Connector's security queries in SharePoint 2.0,
because there was no ability in that release to set permissions directly on folders or files.
When SharePoint 3.0 was released, and new folder and item security levels were permitted,
one would have thought that Microsoft would have augmented the Permissions web
service accordingly.  Unfortunately, however, they did not.  No other out-of-the-box
web services which performed this necessary functionality were available either.  We did
inquire (through Magenic, MetaCarta's SharePoint contractor) whether Microsoft would be willing to
release an augmented Permissions service as a patch, but they declined to do so.

As a result, MetaCarta Inc. asked Magenic to provide a web service that did what was needed: give
access to item and folder level security information.  What they eventually provided
was a web service that looks identical to the Permissions web service, except that it
understands the types "folder" and "item" as well as the other types.  The MCPermissions
web service also performs the necessary inheritance so that the SharePoint Connector
could simply ask for the SIDs for an item, and it would get all the inherited SIDs, as appropriate.

This web service was granted to the Apache Software Foundation by MetaCarta, Inc. as part of
the entire ManifoldCF software grant.
