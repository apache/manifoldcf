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


package org.apache.manifoldcf.agents.output.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputNotifyActivity;
import org.apache.manifoldcf.agents.interfaces.IOutputRemoveActivity;
import org.apache.manifoldcf.agents.interfaces.OutputSpecification;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.agents.output.BaseOutputConnector;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.ui.util.Encoder;
import org.json.JSONException;

/**
 *
 * @author Christian Rieck, Comperio AS
 */
public class RabbitmqOutputConnector extends BaseOutputConnector {

    RabbitmqConfig rabbitconfig; 
    public static final String INGEST_ACTIVITY = "document ingest";
    public static final String REMOVE_ACTIVITY = "document deletion";


    private ConnectionFactory factory = new ConnectionFactory();
    private Connection connection = null;
    private Channel channel = null;

    @Override
    public void connect(ConfigParams configParams) {
        super.connect(configParams);
        rabbitconfig = new RabbitmqConfig(configParams);
    }

    public void outputConfigurationHeader(IThreadContext threadContext,
            IHTTPOutput out, Locale locale, ConfigParams parameters,
            List<String> tabsArray) throws ManifoldCFException, IOException {
        tabsArray.add("Settings");
    }

    public void outputConfigurationBody(IThreadContext threadContext,
            IHTTPOutput out, Locale locale, ConfigParams parameters,
            String tabName) throws ManifoldCFException, IOException {
    	// called when displaying editable configuration tables
    	// NPE when edited in use of rabbitconfig. need to parse? 
    	System.out.println("outputConfigurationBody called");
    	if  (rabbitconfig == null) {
    		rabbitconfig = new RabbitmqConfig(parameters);
    	}
        if (tabName.equals("Settings")) {
            out.print("<table class=\"displaytable\">\n"
                    + "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
                    + "  <tr>\n"
                    + "    <td class=\"description\"><nobr>Host</nobr></td><td class=\"value\">\n"
                    + "    <input type=\"text\" name=\"host\" value=\""+rabbitconfig.getHost()+"\"/>\n"
                    + "    </td>\n"
                    + "  </tr>\n"
                    + "  <tr>\n"
                    + "    <td class=\"description\"><nobr>Queue</nobr></td><td class=\"value\">\n"
                    + "    <input type=\"text\" name=\"queue\" value=\""+rabbitconfig.getQueueName()+"\"/>\n"
                    + "    </td>\n"
                    + "  </tr>\n"
                    + "  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
                    + "</table>");
        }
    }

    @Override
    public boolean checkDocumentIndexable(String outputDescription,
            File localFile) throws ManifoldCFException, ServiceInterruption {
       // System.out.println(outputDescription);
                
        return true;
    }

    @Override
    public void viewConfiguration(IThreadContext threadContext,
            IHTTPOutput out, Locale locale, ConfigParams parameters)
            throws ManifoldCFException, IOException {
    	// called when viewing output connector, simple enough
        System.out.println("viewConfiguration called");
        if (rabbitconfig == null) {
            rabbitconfig = new RabbitmqConfig(parameters);
        }
        String host = rabbitconfig.getHost();
        String queueName = rabbitconfig.getQueueName();
        out.print("<table class=\"displaytable\">  \n"
                + "	<tr> \n"
                + "		<td class=\"value\" colspan=\"3\">      \n"
                + "			<nobr>Directory = " + Encoder.bodyEscape(host) + "</nobr><br/>\n"
                + "		</td>\n"
                + "	</tr>\n"
                + "	<tr> \n"
                + "		<td class=\"value\" colspan=\"3\">      \n"
                + "			<nobr>Directory = " + Encoder.bodyEscape(queueName) + "</nobr><br/>\n"
                + "		</td>\n"
                + "	</tr>\n"
                + "</table>");
    }

    @Override
    public void viewSpecification(IHTTPOutput out, Locale locale,
            OutputSpecification os) throws ManifoldCFException, IOException {
        super.viewSpecification(out, locale, os);
    }

    @Override
    public String processConfigurationPost(IThreadContext threadContext,
            IPostParameters variableContext, ConfigParams parameters)
            throws ManifoldCFException {
    	// called each time the tab changes when creating a new output connector, at least
        RabbitmqConfig.contextToConfig(variableContext, parameters);
        return null;
    }


    /**
     * Remove a document using the connector. Note that the last
     * outputDescription is included, since it may be necessary for the
     * connector to use such information to know how to properly remove the
     * document.
     *
     * @param documentURI is the URI of the document. The URI is presumed to be
     * the unique identifier which the output data store will use to process and
     * serve the document. This URI is constructed by the repository connector
     * which fetches the document, and is thus universal across all output
     * connectors.
     * @param outputDescription is the last description string that was
     * constructed for this document by the getOutputDescription() method above.
     * @param activities is the handle to an object that the implementer of an
     * output connector may use to perform operations, such as logging
     * processing activity.
     */
    @Override
    public void removeDocument(String documentURI, String outputDescription, IOutputRemoveActivity activities)
            throws ManifoldCFException, ServiceInterruption {
        if (Logging.connectors.isDebugEnabled()) {
            Logging.connectors.debug("Deleting document: " + documentURI);
                }
            connectToRabbitInstance();
             
            OutboundDocument rawDocument = new OutboundDocument(documentURI);
            rawDocument.operation = OutboundDocument.Operation.DELETE;
            try {
            String bindingName = "";
            String json = jsonSerialize(rawDocument);
            byte[] bytes = json.getBytes();
            channel.basicPublish(bindingName, rabbitconfig.getQueueName(), MessageProperties.PERSISTENT_BASIC, bytes);
            activities.recordActivity(null, "deletion message sent", 0l, documentURI, "OK", "Deletion message sendt");
        } catch (Exception e) {
            Logging.connectors.error(
                    "Failed to push to rabbitmq (" + rabbitconfig.getHost() + "): ", e);
            activities.recordActivity(null, "Failed to push to rabbitmq", 0l, documentURI, "ERROR", e.getMessage());
        }
        
    }

    @Override
    public int addOrReplaceDocument(String documentURI,
            String outputDescription, RepositoryDocument document,
            String authorityNameString, IOutputAddActivity activities)
            throws ManifoldCFException, ServiceInterruption {
        if (Logging.connectors.isDebugEnabled()) {
            Logging.connectors.debug("New document: " + documentURI);
        }
        connectToRabbitInstance();
        if (sendDocument(document, activities, documentURI)) {
            return 1;
        }

        return 0;
    }

    private void connectToRabbitInstance() throws ManifoldCFException {
        factory.setHost(rabbitconfig.getHost());
        
        try {
            if (connection == null || !connection.isOpen()) {
                connection = factory.newConnection();
            }
            if (channel == null || !connection.isOpen()) {
                channel = connection.createChannel();
            }
            // TODO: problems with shutting down client correctly.
            //  com.rabbitmq.client.AlreadyClosedException: clean connection shutdown; reason: Attempt to use closed channel
            Map<java.lang.String,java.lang.Object> arguments = null;
            channel.queueDeclare(rabbitconfig.getQueueName(),
                    rabbitconfig.isDurable(),
                    rabbitconfig.isExclusive(),
                    rabbitconfig.isAutoDelete(), 
                    arguments);

        } catch (IOException e1) {
            Logging.connectors.error("Failed to initialize connection to rabbitmq, "+ e1.getMessage());
            // TODO Log to activities? 
            throw new ManifoldCFException("Failed to initialize connection to rabbitmq", e1);
        }
    }

    private boolean sendDocument(RepositoryDocument document, IOutputAddActivity activities, String documentURI) {
        try {
            String bindingName = "";
            byte[] bytes = convertToRabbitDocument(document);
            channel.basicPublish(bindingName, rabbitconfig.getQueueName(), MessageProperties.PERSISTENT_BASIC, bytes);
            activities.recordActivity(null, "document ingest", new Long(document.getBinaryLength()), documentURI, "OK", null);
        } catch (Exception e) {
            Logging.connectors.error(
                    "Failed to push to rabbitmq (" + rabbitconfig.getHost() + "): ", e);
            return true;
        }
        return false;
    }

    @Override
    public void noteJobComplete(IOutputNotifyActivity activities)
            throws ManifoldCFException, ServiceInterruption {
        
        try {
            // channel and connection will be null if no new documents was found
            // in the current job. addDocument() is not called and the 
            // initializations are not made.
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException e) {
            Logging.connectors.error("Could not close channel or connection to rabbitmq: " + e);
        } catch (NullPointerException npe) {
            Logging.connectors.error("Channel or connection was null", npe);
        } catch (Exception e) {
            Logging.connectors.error(e.getMessage(), e);
        }
    }

    
      /** Pre-determine whether a document's length is indexable by this connector.  This method is used by participating repository connectors
  * to help filter out documents that are too long to be indexable.
  *@param outputDescription is the document's output version.
  *@param length is the length of the document.
  *@return true if the file is indexable.
  */
  @Override
  public boolean checkLengthIndexable(String outputDescription, long length)
    throws ManifoldCFException, ServiceInterruption
  {
      // TODO: inspect outputDescription to parse of length, then check. 
      // Logging.connectors.error("Document length:, "+ length + " vs " + outputDescription);
    return true;
  }
    
    private byte[] convertToRabbitDocument(RepositoryDocument document) throws IOException, JSONException,
            ManifoldCFException {
        if (Logging.connectors.isDebugEnabled()) {
            Logging.connectors.debug("Atempting to serialize " + document.getFileName());
        }
        OutboundDocument rawDocument = new OutboundDocument(document);
        String json = jsonSerialize(rawDocument);
        return json.getBytes();
    }
    
    private String jsonSerialize(OutboundDocument outbound)  throws IOException, JSONException,
            ManifoldCFException{

        StringWriter sw = new StringWriter();
        BufferedWriter out = new BufferedWriter(sw);
        String jsons = outbound.writeTo(out);
        
        out.close();
        sw.close();
        return jsons;
    }
}
