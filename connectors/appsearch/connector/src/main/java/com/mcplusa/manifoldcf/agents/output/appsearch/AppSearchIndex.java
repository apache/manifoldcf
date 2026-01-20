package com.mcplusa.manifoldcf.agents.output.appsearch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.client.HttpClient;
import org.apache.http.Consts;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.util.URLEncoder;
import org.apache.manifoldcf.crawler.system.Logging;

public class AppSearchIndex extends AppSearchConnection {

  /**
   * The allow attribute name
   */
  protected static final String allowAttributeName = "allow_token_";
  /**
   * The deny attribute name
   */
  protected static final String denyAttributeName = "deny_token_";
  /**
   * The no-security token
   */
  protected static final String noSecurityToken = "__nosecurity__";

  protected static final boolean useNullValue = false;

  private static final int APPSEARCH_MAX_FILESIZE = 100000;

  private static final SimpleDateFormat DATE_FORMATTER;

  static {
    String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    TimeZone UTC = TimeZone.getTimeZone("UTC");
    DATE_FORMATTER = new SimpleDateFormat(ISO_FORMAT, Locale.ROOT);
    DATE_FORMATTER.setTimeZone(UTC);
  }

  protected static String formatAsString(final Date dateValue) {
    return DATE_FORMATTER.format(dateValue);
  }

  public AppSearchIndex(HttpClient client, AppSearchConfig config) {
    super(config, client);
  }

  private String generateDocJson(String documentURI, RepositoryDocument document, String documentContent,
      String contentAttributeName, Integer groupKey, int chunkNumber) {
    Gson gson = new Gson();
    JsonArray documents = new JsonArray();
    JsonObject doc = new JsonObject();

    Iterator<String> i = document.getFields();
    while (i.hasNext()) {
      String fieldName = i.next();
      String fieldNameClean = Utils.cleanFieldName(fieldName);
      Date[] dateFieldValues = document.getFieldAsDates(fieldName);
      if (dateFieldValues != null) {

        // Check if it is single or multi value
        if (dateFieldValues.length > 1) {
          doc.addProperty(fieldNameClean, gson.toJson(dateFieldValues));
        } else if (dateFieldValues.length == 1) {
          doc.addProperty(fieldNameClean, formatAsString(dateFieldValues[0]));
        }
      } else {
        String[] fieldValues;
        try {
          fieldValues = document.getFieldAsStrings(fieldName);

          // Check if it is single or multi value
          if (fieldValues.length > 1) {
            doc.addProperty(fieldNameClean, gson.toJson(fieldValues));
          } else if (fieldValues.length == 1) {
            doc.addProperty(fieldNameClean, fieldValues[0]);
          }
        } catch (IOException ex) {
          Logger.getLogger(AppSearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
    }

    // Standard document fields
    final Date createdDate = document.getCreatedDate();
    if (createdDate != null && config.getCreatedDateAttributeName() != null
        && config.getCreatedDateAttributeName().length() > 0) {
      doc.addProperty(config.getCreatedDateAttributeName(), formatAsString(createdDate));
    }

    final Date modifiedDate = document.getModifiedDate();
    if (modifiedDate != null && config.getModifiedDateAttributeName() != null
        && config.getModifiedDateAttributeName().length() > 0) {
      doc.addProperty(config.getModifiedDateAttributeName(), formatAsString(modifiedDate));
    }

    final Date indexingDate = document.getIndexingDate();
    if (indexingDate != null && config.getIndexingDateAttributeName() != null
        && config.getIndexingDateAttributeName().length() > 0) {
      doc.addProperty(config.getIndexingDateAttributeName(), formatAsString(indexingDate));
    }

    final String mimeType = document.getMimeType();
    if (mimeType != null && config.getMimeTypeAttributeName() != null
        && config.getMimeTypeAttributeName().length() > 0) {
      doc.addProperty(config.getMimeTypeAttributeName(), gson.toJson(mimeType));
    }

    if (config.getExtractorPattern() != null && !config.getExtractorPattern().isEmpty()
        && config.getReplacementString() != null && !config.getReplacementString().isEmpty()) {
      String originalUrl = documentURI;
      doc.addProperty("original_url", originalUrl);
      documentURI = Utils.processExpression(config.getExtractorPattern(), config.getReplacementString(), documentURI);
    }

    if (documentURI != null && config.getUriAttributeName() != null && config.getUriAttributeName().length() > 0) {
      doc.addProperty(config.getUriAttributeName(), documentURI);
    }

    // Content field
    if (contentAttributeName != null && documentContent != null) {
      doc.addProperty(contentAttributeName, documentContent);
    }

    if (groupKey != null) {
      doc.addProperty("groupkey", groupKey);
    }

    if (chunkNumber == 1) {
      doc.addProperty("isparent", true);
    }

    String idDocument = Utils.getValidId(documentURI);

    if (chunkNumber > 1) {
      doc.addProperty("parentid", idDocument);
      idDocument += "#chunk/" + chunkNumber;
    }

    doc.addProperty("id", idDocument);
    documents.add(doc);

    return gson.toJson(doc);
  }

  private boolean pushDocument(String reqBody) {
    try {
      StringBuffer url = getApiUrl("documents");
      HttpPost post = new HttpPost(url.toString());
      Logging.connectors.debug("HttPutUri: " + url.toString());

      post.setEntity(new StringEntity(reqBody, Consts.UTF_8));

      call(post);

      String error = checkJson(jsonException);
      if (getResult() == Result.OK && error == null) {
        setResult("OK", Result.OK, null);
        return true;
      }

      if (error != null) {
        setResult("JSONERROR", Result.ERROR, error);
        Logging.connectors.warn("AppSearch: Index failed: " + getResponse());
        return false;
      }

      // If result is not OK but no specific error, still fail
      setResult("JSONERROR", Result.ERROR, "Unknown error: " + getResponse());
      Logging.connectors.warn("AppSearch: Index failed: " + getResponse());
      return false;

    } catch (Exception ex) {
      setResult("JSONERROR", Result.ERROR, ex.getMessage());
      Logging.connectors.warn("AppSearch: Index failed: " + ex.getMessage());
      return false;
    }
  }

  /**
   * Do the indexing.
   *
   * @param documentURI
   * @param document
   * @param inputStream
   * @param acls
   * @param denyAcls
   * @param shareAcls
   * @param shareDenyAcls
   * @param parentAcls
   * @param parentDenyAcls
   * @return false to indicate that the document was rejected.
   * @throws org.apache.manifoldcf.core.interfaces.ManifoldCFException
   * @throws org.apache.manifoldcf.agents.interfaces.ServiceInterruption
   */
  public boolean execute(String documentURI, RepositoryDocument document, InputStream inputStream, String[] acls,
      String[] denyAcls, String[] shareAcls, String[] shareDenyAcls, String[] parentAcls, String[] parentDenyAcls)
      throws ManifoldCFException, ServiceInterruption {
    Integer groupKey = null;

    String documentContent = getDocumentContent(inputStream);
    String initialDocument = generateDocJson(documentURI, document, documentContent, config.getContentAttributeName(),
        null, 0);

    // push if the document is small
    if (initialDocument.getBytes().length < APPSEARCH_MAX_FILESIZE) {
      return pushDocument(initialDocument);
    }

    // Prepare to split the document and assign a group key
    groupKey = documentURI.hashCode();

    // Generate a doc without content to calculate the size
    String documentWithoutContent = generateDocJson(documentURI, document, null, null, null, 0);
    int documentSize = documentWithoutContent.getBytes().length;
    int FREE_BYTES_RECOMMENDED = 10000;
    int bytesChunks = (APPSEARCH_MAX_FILESIZE - documentSize) - FREE_BYTES_RECOMMENDED;
    Logging.connectors.debug("Document size: " + documentSize + ", will generate chunks of " + bytesChunks + " bytes");
    
    String[] contentChunk = Utils.chunkSplit(documentContent, bytesChunks);

    if (contentChunk == null) {
      Logging.connectors.warn("AppSearch: Failed to split document into chunks for URI: " + documentURI);
      setResult("CHUNKSPLITERROR", Result.ERROR, "Failed to split document content into chunks");
      return false;
    }

    Logging.connectors.debug("Number of chunks generated: " + contentChunk.length);
    for (int i = 0; i < contentChunk.length; i++) {
      String documentChunk = generateDocJson(documentURI, document, contentChunk[i], config.getContentAttributeName(),
          groupKey, i + 1);
      Logging.connectors.debug("Chunk " + (i + 1) + " size: " + documentChunk.getBytes().length);
      if (!pushDocument(documentChunk)) {
        Logging.connectors.warn("AppSearch: Failed to push chunk " + (i + 1) + " for URI: " + documentURI);
        return false;
      }
    }

    return true;
  }

  protected String getDocumentContent(InputStream inputStream) {
    StringBuilder sb = new StringBuilder();
    char[] buffer = new char[65536];
    try (Reader r = new InputStreamReader(inputStream, Consts.UTF_8)) {
      while (true) {
        try {
          int amt = r.read(buffer, 0, buffer.length);
          if (amt == -1) {
            break;
          }
          for (int j = 0; j < amt; j++) {
            final char x = buffer[j];
            if (x == '\n' || x == '\r' || x == '\t' || x == '\b' || x == '\f') {
              sb.append("");
            } else if (x < 32) {
              sb.append("\\u").append(String.format(Locale.ROOT, "%04x", (int) x));
            } else {
              if (x == '\"' || x == '\\' || x == '/') {
                sb.append('\\');
              }
              sb.append(x);
            }
          }
        } catch (IOException ex) {
          Logger.getLogger(AppSearchIndex.class.getName()).log(Level.SEVERE, null, ex);
          break; // TODO: Revisit this with a better solution [This plus while = true]
        }
      }
    } catch (IOException ex) {
      Logger.getLogger(AppSearchIndex.class.getName()).log(Level.SEVERE, null, ex);
    }

    return sb.toString();
  }

}
