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
package org.apache.manifoldcf.connectorcommon.fuzzyml;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.*;

/** Test fuzzyml parser */
public class TestFuzzyML
{
  
  protected final static String fuzzyTestString = 
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
"<rss version=\"2.0\"\n"+
"	xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"\n"+
"	xmlns:wfw=\"http://wellformedweb.org/CommentAPI/\"\n"+
"	xmlns:dc=\"http://purl.org/dc/elements/1.1/\"\n"+
"	xmlns:atom=\"http://www.w3.org/2005/Atom\"\n"+
"	xmlns:sy=\"http://purl.org/rss/1.0/modules/syndication/\"\n"+
"	xmlns:slash=\"http://purl.org/rss/1.0/modules/slash/\"\n"+
"	>\n"+
"		<item>\n"+
"		<title>fme File Exchange Plattform – Austausch mit Externen leichtgemacht</title>\n"+
"		<link>http://blog.fme.de/allgemein/2013-07/fme-file-exchange-plattform-austausch-mit-externen-leichtgemacht</link>\n"+
"		<comments>http://blog.fme.de/allgemein/2013-07/fme-file-exchange-plattform-austausch-mit-externen-leichtgemacht#comments</comments>\n"+
"		<pubDate>Thu, 18 Jul 2013 07:39:03 +0000</pubDate>\n"+
"		<dc:creator>Jan Pfitzner</dc:creator>\n"+
"				<category><![CDATA[Allgemein]]></category>\n"+
"		<category><![CDATA[ECM mit Alfresco]]></category>\n"+
"		<category><![CDATA[Alfresco]]></category>\n"+
"		<category><![CDATA[Content Repository]]></category>\n"+
"		<category><![CDATA[File Exchange Plattform]]></category>\n"+
"		<category><![CDATA[File Sharing]]></category>\n"+
"		<category><![CDATA[Webtechnologien]]></category>\n"+
"\n"+
"		<guid isPermaLink=\"false\">http://blog.fme.de/?p=1806</guid>\n"+
"		<description><![CDATA[Der Austausch von Dokumenten mit Partnern, Agenturen oder auch Kunden ist aus Sicht der Unternehmens-IT immer noch ein oft problematisches Feld. Der Austausch von großen Dateimengen per Mail &#8220;bläht&#8221; die Postfächer unnötig auf und führt zu ineffizienten Prozessen (&#8220;welche Version ist noch aktuell?&#8221;). Die von den Endanwendern in Eigenverantwortung oft gewählte Cloud-Alternative – die Nutzung [...]]]></description>\n"+
"			<content:encoded><![CDATA[<p><strong>Der Austausch von Dokumenten mit Partnern, Agenturen oder auch Kunden ist aus Sicht der Unternehmens-IT immer noch ein oft problematisches Feld. Der Austausch von großen Dateimengen per Mail &#8220;bläht&#8221; die Postfächer unnötig auf und führt zu ineffizienten Prozessen (&#8220;welche Version ist noch aktuell?&#8221;). Die von den Endanwendern in Eigenverantwortung oft gewählte Cloud-Alternative – die Nutzung von Cloud-basierten File-Sharing-Diensten wie Dropbox, Google Drive &amp; Co – ist und bleibt nicht erst seit #Prism ein &#8220;Schreckgespenst&#8221; für viele CIOs. Die fme file Exchange Plattform bietet eine compliance- und integrationsfähige Alternative.</strong></p>\n"+
"<p>Die typischen Anforderungen an eine Dateiaustauschplattform lassen sich in der folgenden Form zusammenfassen:</p>\n"+
"<p><strong>Als Mitarbeiter des Unternehmens möchte ich…</strong></p>\n"+
"<ul>\n"+
"<li>mich mit meinem Standard-Unternehmens-Account anmelden können, am besten per SSO,</li>\n"+
"<li>Sammlungen von Dateien mit externen Benutzern austauschen,</li>\n"+
"<li>angeben können, welche Benutzer lesend oder schreibend auf die Dateien zugreifen können,</li>\n"+
"<li>Dokumente vom lokalen PC oder dem internen Dokumenten-Management-System hochladen/auswählen können,</li>\n"+
"<li>weitere Dateien zu einer Dateisammlung hinzufügen und vorhandene aktualisieren oder löschen können und</li>\n"+
"<li>die Benutzer automatisch benachrichtigen, wenn neue oder geänderte Dateien vorhanden sind.</li>\n"+
"</ul>\n"+
"<p><span id=\"more-1806\"></span></p>\n"+
"<p><strong>Als externer Benutzer möchte ich…</strong></p>\n"+
"<ul>\n"+
"<li>mich einfach im System registrieren können,</li>\n"+
"<li>vom System benachrichtigt werden, wenn ein anderer Benutzer neue Dateien für mich bereitgestellt hat und</li>\n"+
"<li>auf die für mich freigegeben Dateisammlungen einfach zugreifen können.</li>\n"+
"</ul>\n"+
"<p><strong>Als IT-Verantwortlicher möchte ich …</strong></p>\n"+
"<ul>\n"+
"<li>dass das System in der eigenen DMZ betrieben werden kann,</li>\n"+
"<li>dass jede Registrierung von einem Mitarbeiter meines Unternehmens – der Kontaktperson des Externen – freigeschaltet werden muss,</li>\n"+
"<li>eine Übersicht über alle registrierten Benutzer und deren Dateisammlungen haben und dort jederzeit einzelne Dateien oder ganze Sammlungen löschen können,</li>\n"+
"<li>dass freigebende Dateisammlungen nach einer konfigurierbaren Zeitspanne gelöscht werden und</li>\n"+
"<li>verhindern, dass registrierte externe Benutzer das System missbrauchen, indem sie Dateien ohne Beteiligung eines Mitarbeiters austauschen.</li>\n"+
"</ul>\n"+
"<p>Anhand dieser primären Anforderungen eines unserer Kunden haben wir auf Basis von Alfresco und einiger modernen Webtechnologien wie Bootstrap &amp; AngularJS  die fme File Exchange Plattform entwickelt:</p>\n"+
"<p style=\"text-align: center;\"><a href=\"http://blog.fme.de/wp-content/uploads/2013/07/File-Exchange-Plattform.png\" rel=\"lightbox[1806]\"><img class=\"aligncenter  wp-image-1819\" title=\"File Exchange Plattform\" src=\"http://blog.fme.de/wp-content/uploads/2013/07/File-Exchange-Plattform.png\" alt=\"\" width=\"405\" height=\"240\" /></a></p>\n"+
"<p>&nbsp;</p>\n"+
"<p>Alfresco dient in dieser Lösung vor allem als Content Repository inkl. der benötigten Basis -Funktionen wie Berechtigungen, Versionierung, Mailbenachrichtigungen, Dokumentenvorschau sowie Gruppen- &amp; Benutzerverwaltung. Außerdem wird der Registrierungsmechanismus für die externen Benutzer über die in Alfresco integrierte Workflow-Engine Activiti umgesetzt.</p>\n"+
"<p>Die Benutzungsoberfläche der Applikation ist in Form einer Single Page Webapplikation auf Basis der beiden Frameworks AngularJS (<a href=\"http://angularjs.org/\">http://angularjs.org/</a>) &amp; Bootstrap (<a href=\"http://twitter.github.io/bootstrap/\">http://twitter.github.io/bootstrap/</a>) implementiert.</p>\n"+
"<p>Die Kommunikation zwischen diesem JavaScript-Frontend und dem Alfresco-Backend ist mittels sog. Alfresco WebScripts umgesetzt. Das Alfresco WebScript-Framework ist eines der größten Vorteile von Alfresco und ermöglicht die Implementierung einer eigenen auf Anwendungsfall angepassten REST API.</p>\n"+
"<p>Aus der Sicht des Anwenders teilt sich die Applikation in die folgenden, beiden Komponenten:</p>\n"+
"<p><strong>New File Collection</strong></p>\n"+
"<p>Hier haben die Mitarbeiter des Unternehmens die Möglichkeit, eine neue Dateisammlung zum Austausch zu erstellen. Zur Erstellung einer neuen Dateisammlung sind lediglich die folgenden Informationen notwendig:</p>\n"+
"<ol>\n"+
"<li>Name der Dateisammlung.</li>\n"+
"<li>Berechtigte Benutzer – lesend oder schreibend</li>\n"+
"<li>Die Dateien, die hochgeladen werden sollen</li>\n"+
"</ol>\n"+
"<p>Um diesen Erstellungsprozess so intuitiv &amp; komfortabel wie möglich zu halten, wurde zur Benutzerauswahl ein Eingabefeld inkl. einer Autovervollständigung anhand der vorhandenen Benutzerkonten eingesetzt.</p>\n"+
"<p>Zum einfachen Hinzufügen von Dateien vom PC des Benutzers wird neben der üblichen Dateiauswahl auch ein direktes Drag&amp;Drop unterstützt.</p>\n"+
"<p>Während des Hochladevorgangs der Dateien ist der Benutzer durch den Einsatz von Fortschrittsbalken jederzeit über den aktuellen Fortschritt des Uploads informiert.</p>\n"+
"<p>Mit Abschluss des Hochladevorgangs werden die berechtigten Benutzer vom System per Mail über die neue Dateisammlung informiert.</p>\n"+
"<p style=\"text-align: center;\"><a href=\"http://blog.fme.de/wp-content/uploads/2013/07/New-File-Collection1.png\" rel=\"lightbox[1806]\"><img class=\"aligncenter  wp-image-1827\" title=\"New File Collection\" src=\"http://blog.fme.de/wp-content/uploads/2013/07/New-File-Collection1.png\" alt=\"\" width=\"456\" height=\"377\" /></a></p>\n"+
"<p>&nbsp;</p>\n"+
"<p><strong>My File Collections</strong></p>\n"+
"<p>Im Bereich „My File Collection” hat der angemeldete Benutzer einen direkten und einfachen Zugriff auf die Dateisammlungen, für die er berechtigt ist. Je nach vergebener Berechtigung kann der Benutzer die  enthaltenen Dateien herunterladen, aktualisieren &amp; löschen oder aber auch neue Dokumente hochladen und  Ordner erstellen.</p>\n"+
"<p>Zudem  kann der Ersteller einer Dateisammlung oder ein Administrator die Sammlung für weitere Benutzer freigeben bzw. Freigaben zurücknehmen und die automatische Ablauffrist einer Dateisammlung aktivieren bzw. deaktivieren.</p>\n"+
"<p>&nbsp;</p>\n"+
"<p style=\"text-align: center;\"><a href=\"http://blog.fme.de/wp-content/uploads/2013/07/My-File-Collections.png\" rel=\"lightbox[1806]\"><img class=\"aligncenter  wp-image-1828\" title=\"My File Collections\" src=\"http://blog.fme.de/wp-content/uploads/2013/07/My-File-Collections.png\" alt=\"\" width=\"492\" height=\"377\" /></a></p>\n"+
"<p>&nbsp;</p>\n"+
"<p><strong>Fazit</strong></p>\n"+
"<p>Die Kombination von Alfresco, AngularJS &amp; Bootstrap hat sich während der Lösungsentwicklung als wunderbar harmonierende und sehr produktive Kombination zur Entwicklung von eigenen dokumentenorientieren Webapplikationen erwiesen.</p>\n"+
"<p>Die Nutzung dieser erprobten Werkzeuge bringt zudem die Unterstützung von Webbrowsers mobiler Geräte nahezu zum Nulltarif mit. So ist die Bedienung der Anwendung mit einem iPad oder iPhone problemlos möglich und bspw.  das einfache Bereitstellen der Fotografien vom Flipchart aus dem gerade beendeten Workshop  schnell &amp; einfach erledigt.</p>\n"+
"<p>Die Lösung zeigt einmal mehr, dass es neben der Konfiguration &amp; Anpassung eines Standard-Clients immer auch eine zweite zu betrachtende Variante gibt:</p>\n"+
"<p>Die Implementierung eines eigenen Clients anhand von best-of-breed Technologien, wie in diesem Beispiel zur vollsten Kundenzufriedenheit gezeigt.</p>\n"+
"<p>&nbsp;</p>\n"+
"]]></content:encoded>\n"+
"			<wfw:commentRss>http://blog.fme.de/allgemein/2013-07/fme-file-exchange-plattform-austausch-mit-externen-leichtgemacht/feed</wfw:commentRss>\n"+
"		<slash:comments>0</slash:comments>\n"+
"		</item>\n"+
"		<item>\n"+
"		<title>migration-center vs. EMC&#8217;s EMA &#8211; the main differences</title>\n"+
"		<link>http://blog.fme.de/allgemein/2013-07/migration-center-vs-emcs-ema-the-main-differences</link>\n"+
"		<comments>http://blog.fme.de/allgemein/2013-07/migration-center-vs-emcs-ema-the-main-differences#comments</comments>\n"+
"		<pubDate>Thu, 04 Jul 2013 09:30:09 +0000</pubDate>\n"+
"		<dc:creator>fpiaszyk</dc:creator>\n"+
"				<category><![CDATA[Allgemein]]></category>\n"+
"		<category><![CDATA[ECM Consulting]]></category>\n"+
"		<category><![CDATA[EMC Documentum]]></category>\n"+
"		<category><![CDATA[Software Technology]]></category>\n"+
"		<category><![CDATA[D2]]></category>\n"+
"		<category><![CDATA[delta Migration]]></category>\n"+
"		<category><![CDATA[Documentum. xCP]]></category>\n"+
"		<category><![CDATA[Migration]]></category>\n"+
"		<category><![CDATA[migration-center]]></category>\n"+
"		<category><![CDATA[no downtime]]></category>\n"+
"\n"+
"		<guid isPermaLink=\"false\">http://blog.fme.de/?p=1793</guid>\n"+
"		<description><![CDATA[EMA is NOT an out-of-the-box product, it&#8217;s a tool set (framework) that ONLY the Documentum Professional Services Team uses. This tool is exclusively available through EMC IIG Services. Once the engagement is over, EMA leaves with the team and cannot be used for additional migrations. Partners and customers are not able to use EMA without [...]]]></description>\n"+
"			<content:encoded><![CDATA[<p><a href=\"http://blog.fme.de/wp-content/uploads/2013/07/product_06.jpg\" rel=\"lightbox[1793]\"><img class=\"alignright size-thumbnail wp-image-1802\" title=\"product_06\" src=\"http://blog.fme.de/wp-content/uploads/2013/07/product_06-150x150.jpg\" alt=\"\" width=\"150\" height=\"150\" /></a></p>\n"+
"<p>EMA is NOT an out-of-the-box product, it&#8217;s a tool set (framework) that ONLY the Documentum Professional Services Team uses. This tool is exclusively available through EMC IIG Services. Once the engagement is over, EMA leaves with the team and cannot be used for additional migrations. Partners and customers are not able to use EMA without IIG Consulting in a project because EMA bypasses the API (DFC).</p>\n"+
"<p>The main use case for EMA is the high speed cloning from Oracle based on-premise Documentum installations to MS SQL Server based off-premise (EMC onDemand) installations. For this approach a simple dump&amp;load is not feasible and a tool for cloning is needed. In addition EMC addressed some other use cases at EMC World 2013 like version upgrades (DCM to D2 life sciences and Webtop to D2 or xCP) and third-party migrations.</p>\n"+
"<p>&nbsp;</p>\n"+
"<p><strong>Speed vs. Business Requirements and methodology</strong></p>\n"+
"<p>Cloning or a 1:1 migration of all objects located in a repository without reorganization and clean-up has no additional business value, the result is just a new platform and/or version (garbage in, garbage out). With EMA, changes on business requirements can not be applied easily during the migration (e.g. new metadata, new object types, business logic etc.). The results of the actual migration can not be discussed with the business department before the content is imported in the target system. If needed, duplicates can not be dictated and managed. And furthermore it is not possible to apply changes during the project with just a few clicks of the mouse as you could with migration-center.</p>\n"+
"<p><span id=\"more-1793\"></span></p>\n"+
"<p>migration-center is designed for administrators to support them in all phases of a migration project and not just during the import. Over the last eight years migration-center has been developed together with our partners and customers into a product which integrates a full project methodology and all essential capabilities for the different steps during a project. Today migration-center is not only a product but rather a migration platform which covers numerous migration scenarios.</p>\n"+
"<p>&nbsp;</p>\n"+
"<p><strong>Summary</strong></p>\n"+
"<p>Unlike EMC&#8217;s EMA migration-center is an out-of-the box product and comes with a user-friendly user interface, full maintenance and support as well as regular updates. The product has been EMC certified since 2005 and is a proven migration platform for any type of platform independent content migration. migration-center has proven its quality in more than 130 projects around the world, especially within regulated environments. The product may be either leased or purchased by customers, partners or other preferred system integrators. migration-center is scalable because of the open architecture which is designed for large migrations without interrupting daily business operations. It reaches the maximum performance of the source and target systems without any restrictions. A large variety of algorithms and commands are available to meet all migration requirements without additional programming or scripting effort.</p>\n"+
"<p>migration-center&#8217;s abilities will make a positive difference in your business if you are challenged with migrating enterprise content. Convince yourself and let us demonstrate your data working within migration-center in just a couple of days before you lease or buy the whole product.</p>\n"+
"<p>&nbsp;</p>\n"+
"<p><strong>Benefits of using migration-center instead of EMA</strong></p>\n"+
"<p>•  No service engagement with EMC IIG Services required</p>\n"+
"<p>•  migration-center is a proven product since 2005 and was used in more than 130 projects around the world</p>\n"+
"<p>•  It is a full function, out-of-the-box software, fully documented, easy to deploy, with an excellent graphical user interface</p>\n"+
"<p>•  Product and migration project support available</p>\n"+
"<p>•  NO DOWNTIME &#8211; It works without interrupting any of your normal business operations because of the delta migration capability</p>\n"+
"<p>•  The software is designed to efficiently move and classify large volumes of documents into the chosen target repository (up to 1 million documents per day with just a single process)</p>\n"+
"<p>•  A large variety of algorithms and commands are available to create a very specific mapping and transformation logic, covering every migration requirement possible and all this without additional programming or scripting effort</p>\n"+
"<p>•  migration-center is able to apply the business logic of highly customized applications (e.g. D2, TBOs, server methods etc.)</p>\n"+
"<p>•  Complete rule simulation and error handling are provided, allowing in depth testing of transformation rules before committing content to the actual repository import</p>\n"+
"<p>•  The migration-center grants more than 55 out-of-the-box connections from various source to various target systems</p>\n"+
"<p>•  migration-center supports Documentum versions from 4i to D7</p>\n"+
"<p>•  migration-center reflects proven migration methodology in its step-by-step processes</p>\n"+
"<p>•  migration-center has been validated and approved by many companies in regulated environments, e.g. international pharmaceutical players</p>\n"+
"<p>•  The migration-center pricing model is simple and very flexible</p>\n"+
"<p>•  Through its enormous flexibility the migration-center may be deployed for a variety of migration scenarios</p>\n"+
"<p>•  With our international partners we offer customer product training as well as complete implementation by highly competent professionals</p>\n"+
"<p>•  migration-center is much more them a simple ETL tool</p>\n"+
"<p>•  migration-center offers a variety of proven pre-confugure services</p>\n"+
"<p>&nbsp;</p>\n"+
"<p>&nbsp;</p>\n"+
"<p>&nbsp;</p>\n"+
"<p>&nbsp;</p>\n"+
"<p>&nbsp;</p>\n"+
"]]></content:encoded>\n"+
"			<wfw:commentRss>http://blog.fme.de/allgemein/2013-07/migration-center-vs-emcs-ema-the-main-differences/feed</wfw:commentRss>\n"+
"		<slash:comments>0</slash:comments>\n"+
"		</item>\n"
;

  @Test
  public void testTags()
    throws IOException, ManifoldCFException
  {
    org.apache.manifoldcf.core.system.Logging.misc = org.apache.log4j.Logger.getLogger("test");
    InputStream is = new ByteArrayInputStream("<href a=/hello/out/there/>".getBytes(StandardCharsets.UTF_8));
    Parser p = new Parser();
    TestParseState x = new TestParseState();
    p.parseWithCharsetDetection(null,is,x);
    Assert.assertTrue(x.lastTagName != null);
    Assert.assertTrue(x.lastTagName.equals("href"));
    Assert.assertTrue(x.lastTagAttributes.size() == 1);
    Assert.assertTrue(x.lastTagAttributes.get(0).getName().equals("a"));
    Assert.assertTrue(x.lastTagAttributes.get(0).getValue().equals("/hello/out/there/"));
  }
  
  protected static class TestParseState extends TagParseState
  {
    public String lastTagName = null;
    public List<AttrNameValue> lastTagAttributes = null;
    
    public TestParseState()
    {
      super();
    }
    
    @Override
    protected boolean noteTag(String tagName, List<AttrNameValue> attributes)
      throws ManifoldCFException
    {
      lastTagName = tagName;
      lastTagAttributes = attributes;
      return super.noteTag(tagName, attributes);
    }

  }

  @Test
  public void testFailure()
    throws IOException, ManifoldCFException
  {
    org.apache.manifoldcf.core.system.Logging.misc = org.apache.log4j.Logger.getLogger("test");
    InputStream is = new ByteArrayInputStream(fuzzyTestString.getBytes(StandardCharsets.UTF_8));
    Parser p = new Parser();
    // Parse the document.  This will cause various things to occur, within the instantiated XMLParsingContext class.
    XMLFuzzyHierarchicalParseState x = new XMLFuzzyHierarchicalParseState();
    x.setContext(new TestParsingContext(x));
    try
    {
      // Believe it or not, there are no parsing errors we can get back now.
      p.parseWithCharsetDetection(null,is,x);
    }
    finally
    {
      x.cleanup();
    }
  }

  protected static class TestParsingContext extends XMLParsingContext
  {
    protected String thisTag = null;
    
    public TestParsingContext(XMLFuzzyHierarchicalParseState theStream)
    {
      super(theStream);
    }
    
    public TestParsingContext(XMLFuzzyHierarchicalParseState theStream, String namespace, String localname, String qname, Map<String,String> theseAttributes)
    {
      super(theStream,namespace,localname,qname,theseAttributes);
    }
    
    @Override
    protected XMLParsingContext beginTag(String namespace, String localName, String qName, Map<String,String> atts)
      throws ManifoldCFException
    {
      thisTag = qName;
      return new TestParsingContext(theStream,namespace,localName,qName,atts);
    }

    @Override
    protected void endTag()
      throws ManifoldCFException
    {
      super.endTag();
    }

  }
  
}
