package org.apache.manifoldcf.agents.output.lucene;

import java.util.Map;

import com.google.common.collect.Maps;

public class LuceneClientManager {

  private static Map<String,LuceneClient> clients = Maps.newHashMap();

  private LuceneClientManager() { }

  public synchronized static LuceneClient getClient(
                      String path, String processID,
                      String charfilters, String tokenizers, String filters,
                      String analyzers, String fields,
                      String idField, String contentField,
                      Long maxDocumentLength) throws Exception
  {
    String paramPath;
    if (!LuceneClient.useHdfs(path)) {
      paramPath = path;
    } else {
      paramPath =  (processID.equals("")) ? path : path + "/" + processID;
    }

    LuceneClient client = clients.get(paramPath);

    if (client == null) {
      return newClient(paramPath, charfilters, tokenizers, filters, analyzers, fields, idField, contentField, maxDocumentLength);
    }

    if (client != null) {
      if (!client.isOpen()) {
        return newClient(paramPath, charfilters, tokenizers, filters, analyzers, fields, idField, contentField, maxDocumentLength);
      }
      String latestVersion = LuceneClient.createVersionString(
          paramPath,
          LuceneClient.parseAsMap(charfilters), 
          LuceneClient.parseAsMap(tokenizers),
          LuceneClient.parseAsMap(filters),
          LuceneClient.parseAsMap(analyzers),
          LuceneClient.parseAsMap(fields),
          idField, contentField, maxDocumentLength);
      String activeVersion = client.versionString();
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
          String idField, String contentField,
          Long maxDocumentLength) throws Exception
  {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    LuceneClient client;
    try {
      Thread.currentThread().setContextClassLoader(LuceneClientManager.class.getClassLoader());
      client = new LuceneClient(path,
        charfilters, tokenizers, filters, analyzers, fields,
        idField, contentField, maxDocumentLength);
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
    clients.put(path, client);
    return client;
  }

}
