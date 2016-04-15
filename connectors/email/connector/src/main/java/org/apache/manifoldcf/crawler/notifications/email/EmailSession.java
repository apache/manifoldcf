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
  protected final Properties properties;

  private Session session = null;

  /** Create a session */
  public EmailSession(final String server, final int port, final String username, final String password,
    Properties properties)
    throws MessagingException
  {
    this.server = server;
    this.port = port;
    this.username = username;
    this.password = password;
    this.properties = properties;

    properties.put("mail.smtp.host", server);
    properties.put("mail.smtp.port", new Integer(port).toString());

    if (properties.get("mail.smtp.connectiontimeout") == null) {
      properties.put("mail.smtp.connectiontimeout", new Integer(60000));
    }
    if (properties.get("mail.smtp.timeout") == null) {
      properties.put("mail.smtp.timeout", new Integer(60000));
    }
    if (properties.get("mail.smtp.writetimeout") == null) {
      properties.put("mail.smtp.writetimeout", new Integer(60000));
    }
    
    if (username != null && username.length() > 0) {
      properties.put("mail.smtp.auth", "true");
    }
    
    if (properties.get("mail.smtp.starttls.enable") == null) {
      properties.put("mail.smtp.starttls.enable", "true");
      //properties.put("mail.smtp.ssl.trust", true);
    }

    
    // Now, try to connect
    final Session thisSession = Session.getInstance(properties,
      new javax.mail.Authenticator() {
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(username, password);
        }
      });
  
    session = thisSession;
  }
  
  public void checkConnection()
    throws MessagingException
  {
    // Need something here...
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
    session = null;
  }
}