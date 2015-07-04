package org.apache.manifoldcf.agents.output.lucene;

import java.io.File;
import java.util.Map;

import com.google.common.collect.Maps;

public class LuceneClientManager {

  private static Map<String,LuceneClient> clients = Maps.newHashMap();
  private static Map<String,String> versionStrings = Maps.newHashMap();

  private LuceneClientManager() { }

  public synchronized static LuceneClient getClient(
                      String path,
                      String charfilters, String tokenizers, String filters,
                      String analyzers, String fields,
                      String idField, String contentField) throws Exception
  {
    LuceneClient client = clients.get(path);

    if (client == null) {
      return newClient(path, charfilters, tokenizers, filters, analyzers, fields, idField, contentField);
    }

    if (client != null) {
      if (!client.isOpen()) {
        return newClient(path, charfilters, tokenizers, filters, analyzers, fields, idField, contentField);
      }
      String latestVersion = LuceneClient.createVersionString(
          new File(path).toPath(),
          LuceneClient.parseAsMap(charfilters), 
          LuceneClient.parseAsMap(tokenizers),
          LuceneClient.parseAsMap(filters),
          LuceneClient.parseAsMap(analyzers),
          LuceneClient.parseAsMap(fields),
          idField, contentField);
      String activeVersion = versionStrings.get(path);
      if (!activeVersion.equals(latestVersion)) {
        throw new IllegalStateException("The connection on this path is active. Can not update to the latest settings."
          + " Active settings:" + activeVersion
          + " Latest settings:" + latestVersion);
      }
    }
    return client;
  }

  private static LuceneClient newClient(
          String path,
          String charfilters, String tokenizers, String filters,
          String analyzers, String fields,
          String idField, String contentField) throws Exception
  {
    LuceneClient client =  new LuceneClient(new File(path).toPath(),
                           charfilters, tokenizers, filters, analyzers, fields,
                           idField, contentField);
    clients.put(path, client);
    versionStrings.put(path, client.versionString());
    return client;
  }

}
