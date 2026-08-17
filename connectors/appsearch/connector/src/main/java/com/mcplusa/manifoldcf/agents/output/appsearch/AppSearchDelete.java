package com.mcplusa.manifoldcf.agents.output.appsearch;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.apache.http.Consts;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.crawler.system.Logging;

public class AppSearchDelete extends AppSearchConnection {

  public AppSearchDelete(HttpClient client, AppSearchConfig config) {
    super(config, client);
  }

  class CustomHttpDelete extends HttpPost {

    public CustomHttpDelete(String url) {
      super(url);
    }

    @Override
    public String getMethod() {
      return "DELETE";
    }
  }

  public void execute(String documentURI)
	  throws ManifoldCFException, ServiceInterruption {
    String newURI = documentURI;

    if (config.getExtractorPattern() != null && !config.getExtractorPattern().isEmpty()
        && config.getReplacementString() != null && !config.getReplacementString().isEmpty()) {
      newURI = Utils.processExpression(config.getExtractorPattern(), config.getReplacementString(), documentURI);
    }

    String idField = Utils.getValidId(newURI);
    StringBuffer url = getApiUrl("documents");
    CustomHttpDelete method = new CustomHttpDelete(url.toString());

    Gson gson = new Gson();
    JsonArray documents = new JsonArray();
    documents.add(idField);

    int amountOfChunks = getAmountDocumentChunks(idField);
    if (amountOfChunks > 1) {
      for (int i = 1; i < amountOfChunks; i++) {
        documents.add(idField + "#chunk/" + (i + 1));
      }
    }

    String body = gson.toJson(documents);
    method.setEntity(new StringEntity(body, Consts.UTF_8));

    call(method);
    String error = checkJson(jsonException);
    if (getResult() == Result.OK && error == null) {
      setResult("OK", Result.OK, null);
      return;
    }

    setResult("JSONERROR", Result.ERROR, error);
    try {
        Logging.initializeLoggers();
      Logging.connectors.warn("AppSearch: Delete failed: " + getResponse());
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
    }
  }

  private int getAmountDocumentChunks(String documentId) {
    try {
      StringBuffer url = getApiUrl("search");
      HttpPost method = new HttpPost(url.toString());

      Gson gson = new Gson();
      JsonObject searchQuery = new JsonObject();
      searchQuery.addProperty("query", documentId);
      
      JsonObject resultFields = new JsonObject();
      JsonObject title = new JsonObject();
      JsonObject raw = new JsonObject();
      title.add("raw", raw);
      resultFields.add("title", title);
      searchQuery.add("result_fields", resultFields);
      
      JsonObject page = new JsonObject();
      page.addProperty("size", 100);
      searchQuery.add("page", page);
      
      method.setEntity(new StringEntity(gson.toJson(searchQuery), Consts.UTF_8));

      call(method);

      String error = checkJson(jsonException);
      if (getResult() == Result.OK && error == null) {
        JsonObject response = gson.fromJson(getResponse(), JsonObject.class);

        if (response != null) {
          JsonArray results = response.get("results").getAsJsonArray();
          if (results != null && results.size() > 1) {
            return results.size();
          }
        }

        return 1;
      }
      setResult("JSONERROR", Result.ERROR, error);
      Logging.connectors.warn("AppSearch: Commit failed: " + getResponse());

    } catch (ManifoldCFException ex) {
      System.out.println(ex.getMessage());
      return 0;
    } catch (ServiceInterruption ex) {
      System.out.println(ex.getMessage());
      return 0;
    }
    return 0;
  }

  @Override
  protected boolean handleResultCode(int code, String response)
	  throws ManifoldCFException, ServiceInterruption {
    if (code == 404) {
      setResult("OK", Result.OK, null);
      return true;
    }
    return super.handleResultCode(code, response);
  }
}
