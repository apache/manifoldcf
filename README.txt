# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

Apache ManifoldCF (formerly Apache Connectors Framework) is an effort undergoing
incubation at The Apache Software Foundation (ASF), sponsored by the Lucene PMC.
Incubation is required of all newly accepted projects until a further review indicates
that the infrastructure, communications, and decision making process have stabilized
in a manner consistent with other successful ASF projects. While incubation status is
not necessarily a reflection of the completeness or stability of the code, it does
indicate that the project has yet to be fully endorsed by the ASF.


Welcome to the Apache ManifoldCF project!
-----------------------------------------

Apache ManifoldCF is a multi-repository crawler framework, with multiple connectors,
under incubation.

For a complete description of the ManifoldCF project, team composition, source
code repositories, and other details, please see the ManifoldCF web site at
http://incubator.apache.org/connectors


Getting Started
---------------

See the "dist/example" directory for an example ManifoldCF setup.  Start the example
by going to that directory and typing "<path_to_java> -jar start.jar".  Full documentation
can also be found in "dist/doc".

Some Files Included In Apache ManifoldCF Distributions
----------------------------------------------------

dist/example
  The Apache ManifoldCF Quick Start example environment.

dist/doc/index.html
  The Apache ManifoldCF documentation root, which includes the Javadoc.

dist/doc/how-to-build-and-deploy.html
  The Apache ManifoldCF documentation page describing building and deployment.

framework
  The sources for the Apache ManifoldCF framework.
  
connectors
  The sources for the Apache ManifoldCF connectors.

site
  The sources for the Apache ManifoldCF documentation.
  
build.xml
  The root ant build script for Apache ManifoldCF.


Instructions for Building Apache ManifoldCF from Source
-----------------------------------------------------

1. Download the Java SE 5 JDK (Java Development Kit) from http://java.sun.com.
   You will need the JDK installed, and the %JAVA_HOME%\bin directory included
   on your command path.  To test this, issue a "java -version" command from your
   shell and verify that the Java version is 1.5.  You may also use later versions of
   Java SE, but you will not be able to build the documentation if that is the version
   you use.

2. Download the Apache Ant binary distribution (1.7.0 or greater) from http://ant.apache.org.
   You will need Ant installed and the %ANT_HOME%\bin directory included on your
   command path.  To test this, issue a "ant -version" command from your
   shell and verify that Ant is available.
   
3. In a shell, change to the root directory of the source (where you find the outermost
   build.xml file), and type "ant" for directions.

Licensing
---------

ManifoldCF is licensed under the
Apache License 2.0. See the files called LICENSE.txt and NOTICE.txt
for more information.

Cryptographic Software Notice
-----------------------------

This distribution may include software that has been designed for use
with cryptographic software. The country in which you currently reside
may have restrictions on the import, possession, use, and/or re-export
to another country, of encryption software. BEFORE using any encryption
software, please check your country's laws, regulations and policies
concerning the import, possession, or use, and re-export of encryption
software, to see if this is permitted. See <http://www.wassenaar.org/>
for more information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS), has classified this software as Export Commodity
Control Number (ECCN) 5D002.C.1, which includes information security
software using or performing cryptographic functions with asymmetric
algorithms. The form and manner of this Apache Software Foundation
distribution makes it eligible for export under the License Exception
ENC Technology Software Unrestricted (TSU) exception (see the BIS
Export Administration Regulations, Section 740.13) for both object
code and source code.

The following provides more details on the included software that
may be subject to export controls on cryptographic software:

  ManifoldCF interfaces with the
  Java Secure Socket Extension (JSSE) API to provide

    - HTTPS support

  ManifoldCF interfaces with the
  Java Cryptography Extension (JCE) API to provide

    - NTLM authentication

  ManifoldCF does not include any
  implementation of JSSE or JCE.

Contact
-------

  o For general information visit the main project site at
    http://incubator.apache.org/connectors


