package com.mcplusa.manifoldcf.agents.output.appsearch;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.HttpClient;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.junit.Test;

public class AppSearchIndexTest {

  @Test
  public void testGetDocumentContent() throws FileNotFoundException {
    ConfigParams params = new ConfigParams();
    AppSearchConfig config = new AppSearchConfig(params);
    HttpClient client = null;
    AppSearchIndex index = new AppSearchIndex(client, config);

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("document-content.txt").getFile());
    String content = index.getDocumentContent(new FileInputStream(file));

    Pattern pattern = Pattern.compile("\\n\\r\\t\\b\\f");
    Matcher matcher = pattern.matcher(content); 

    if(matcher.find()) {
      fail("Content should not contains escape sequences");
    }
  }
}
