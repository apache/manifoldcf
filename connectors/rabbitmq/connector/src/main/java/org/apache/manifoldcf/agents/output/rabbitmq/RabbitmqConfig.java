/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.manifoldcf.agents.output.rabbitmq;

import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IPostParameters;
import org.apache.manifoldcf.crawler.system.Logging;

/**
 *
 * @author Christian
 */
public class RabbitmqConfig {
    
    public static final String hostParameter = "host";
    public static final String queueParameter = "queue";
    public static final String durableParameter = "durable";
    public static final String autoDeleteParameter = "autodelete";
    public static final String exclusiveParameter = "exclusive";
    public static final String transactionParameter = "transaction";
  
    private String queueName = "manifoldcf";
    private String host = "localhost";
    private int port;
    private boolean durable = true;
    private boolean exclusive = false; 
    private boolean autoDelete = false; 
    private boolean useTransactions = false;
    
    
    RabbitmqConfig(ConfigParams configParams) {
        this.host = configParams.getParameter(hostParameter);
        this.queueName = configParams.getParameter(queueParameter);
        extractAutoDeleteParameter(configParams);
        extractExclusiveParameter(configParams);
        extractDurableParameter(configParams);
    }

    public final static void contextToConfig(IPostParameters variableContext,
      ConfigParams parameters)
  {
        String host = variableContext.getParameter(hostParameter);
        if (host != null) {
            parameters.setParameter(hostParameter, host);
        }
        String queue = variableContext.getParameter(queueParameter);
        if (queue != null) {
            parameters.setParameter(queueParameter, queue);
        }
        String _autodelete = variableContext.getParameter(autoDeleteParameter);
        if (_autodelete != null) {
            parameters.setParameter(autoDeleteParameter, _autodelete);
        }
        String _durable = variableContext.getParameter(durableParameter);
        if (_durable != null) {
            parameters.setParameter(durableParameter, _durable);
        }
        String _exclusive = variableContext.getParameter(_durable);
        if (_exclusive != null) {
            parameters.setParameter(exclusiveParameter, _exclusive);
        }
  }
    
    
    private void extractAutoDeleteParameter(ConfigParams variableContext) {
        String _active = variableContext.getParameter(autoDeleteParameter);
        if(_active != null) {
            this.autoDelete = Boolean.parseBoolean(_active);
            Logging.connectors.debug("Channel parameter active set to " + this.autoDelete);
        } else {
            this.autoDelete = false;
            Logging.connectors.debug("Channel parameter active parameter not set, defaults to " + this.autoDelete);
        }
    }

    private void extractDurableParameter(ConfigParams variableContext) {
        String _durable = variableContext.getParameter(durableParameter);
        if(_durable != null) {
            this.durable = Boolean.parseBoolean(_durable);
            Logging.connectors.debug("Channel parameter durable set to " + this.durable);
        } else {
            this.autoDelete = true;
            Logging.connectors.debug("Channel parameter durable parameter not set, defaults to " + this.durable);
        }
    }

    private void extractExclusiveParameter(ConfigParams variableContext) {
        String _exclusive = variableContext.getParameter(exclusiveParameter);
        if(_exclusive != null) {
            this.exclusive = Boolean.parseBoolean(_exclusive);
            Logging.connectors.debug("Channel parameter exclusive set to " + this.exclusive);
        } else {
            this.exclusive = false;
            Logging.connectors.debug("Channel parameter exclusive parameter not set, defaults to " + this.exclusive);
        }
    }

    
    public String getQueueName() {
        return queueName;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isDurable() {
        return durable;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public boolean isUseTransactions() {
        return useTransactions;
    }
}
