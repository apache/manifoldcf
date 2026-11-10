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

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.core.system.ManifoldCF;
import org.junit.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class DefaultAuthenticatorTest
{
  private static File tempConfigFile;

  @BeforeClass
  public static void setUp() throws Exception
  {
    tempConfigFile = File.createTempFile("properties", ".xml");
    tempConfigFile.deleteOnExit();
    String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<configuration>\n</configuration>\n";
    try (FileOutputStream fos = new FileOutputStream(tempConfigFile)) {
      fos.write(xmlContent.getBytes(StandardCharsets.UTF_8));
    }
    System.setProperty("org.apache.manifoldcf.configfile", tempConfigFile.getAbsolutePath());
    ManifoldCF.initializeEnvironment(ThreadContextFactory.make());
  }

  @AfterClass
  public static void tearDown() throws Exception
  {
    System.clearProperty("org.apache.manifoldcf.configfile");
    if (tempConfigFile != null && tempConfigFile.exists())
    {
      tempConfigFile.delete();
    }
  }

  @Test
  public void testDefaultAPILoginDoesNotAcceptEmptyCredentials() throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
    DefaultAuthenticator auth = new DefaultAuthenticator(tc);

    // Empty credentials must not be accepted
    assertFalse("Empty credentials should not be accepted", auth.verifyAPILogin("", ""));
    assertFalse("Null userId should not be accepted", auth.verifyAPILogin(null, ""));
    assertFalse("Null password should not be accepted", auth.verifyAPILogin("admin", null));

    // Default admin credentials should succeed
    assertTrue("Default admin credentials should be accepted", auth.verifyAPILogin("admin", "admin"));

    // Incorrect credentials must fail
    assertFalse("Wrong password should be rejected", auth.verifyAPILogin("admin", "wrongpassword"));
    assertFalse("Wrong username should be rejected", auth.verifyAPILogin("wronguser", "admin"));
  }

  @Test
  public void testDefaultUILogin() throws Exception
  {
    IThreadContext tc = ThreadContextFactory.make();
    DefaultAuthenticator auth = new DefaultAuthenticator(tc);

    assertFalse("Empty credentials should not be accepted for UI", auth.verifyUILogin("", ""));
    assertTrue("Default admin credentials should be accepted for UI", auth.verifyUILogin("admin", "admin"));
    assertFalse("Wrong password should be rejected for UI", auth.verifyUILogin("admin", "wrong"));
  }

  @Test
  public void testCustomAPILoginCredentialsViaSystemProperty() throws Exception
  {
    System.setProperty(DefaultAuthenticator.apiLoginUserNameProperty, "customApiUser");
    System.setProperty(DefaultAuthenticator.apiLoginPasswordProperty, "customApiPass");
    try
    {
      IThreadContext tc = ThreadContextFactory.make();
      DefaultAuthenticator auth = new DefaultAuthenticator(tc);

      assertFalse("Empty credentials should not be accepted", auth.verifyAPILogin("", ""));
      assertFalse("Default admin should no longer be accepted when custom is set", auth.verifyAPILogin("admin", "admin"));
      assertTrue("Custom credentials should be accepted", auth.verifyAPILogin("customApiUser", "customApiPass"));
      assertFalse("Wrong password for custom user should fail", auth.verifyAPILogin("customApiUser", "wrong"));
    }
    finally
    {
      System.clearProperty(DefaultAuthenticator.apiLoginUserNameProperty);
      System.clearProperty(DefaultAuthenticator.apiLoginPasswordProperty);
    }
  }
}
