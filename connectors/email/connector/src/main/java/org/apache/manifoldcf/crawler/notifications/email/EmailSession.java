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

package org.apache.manifoldcf.crawler.notifications.email;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;

/** This class represents a raw email session, without any protection
* from threads waiting on sockets, etc.
*/
public class EmailSession
{
  protected final String server;
  protected final int port;
  protected final String username;
  protected final String password;
  protected final String protocol;
  protected final Properties properties;

  private Session session = null;
  private Store store = null;

  /** Create a session */
  public EmailSession(String server, int port, String username, String password,
    String protocol, Properties properties)
    throws MessagingException
  {
    this.server = server;
    this.port = port;
    this.username = username;
    this.password = password;
    this.protocol = protocol;
    this.properties = properties;
    
    // Now, try to connect
    Session thisSession = Session.getDefaultInstance(properties, null);
    Store thisStore = thisSession.getStore(protocol);
    thisStore.connect(server, port, username, password);

    session = thisSession;
    store = thisStore;
  }
  
  public void checkConnection()
    throws MessagingException
  {
    if (store != null)
    {
      if (store.getDefaultFolder() == null) {
        throw new MessagingException("Error checking the connection: No default folder.");
      }
    }
  }

  public void send(List<String> to, String from, String subject, String body)
    throws MessagingException
  {
    // Create a default MimeMessage object.
    MimeMessage message = new MimeMessage(session);

    // Set From: header field of the header.
    message.setFrom(new InternetAddress(from));

    // Set To: header field of the header.
    for (String toValue : to) {
      message.addRecipient(Message.RecipientType.TO, new InternetAddress(toValue));
    }

    // Set Subject: header field
    message.setSubject(subject);

    // Now set the actual message
    message.setText(body);

    // Send message
    Transport.send(message);
  }
  
  public void close()
    throws MessagingException
  {
    if (store != null)
    {
      store.close();
      store = null;
    }
    session = null;
  }
}