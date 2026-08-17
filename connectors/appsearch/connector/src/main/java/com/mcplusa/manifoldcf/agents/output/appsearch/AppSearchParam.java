package com.mcplusa.manifoldcf.agents.output.appsearch;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.apache.manifoldcf.core.interfaces.IHTTPOutput;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.connectorcommon.interfaces.IKeystoreManager;
import org.apache.manifoldcf.connectorcommon.interfaces.KeystoreManagerFactory;

import com.mcplusa.manifoldcf.agents.output.appsearch.AppSearchParam.ParameterEnum;

/**
 * Parameters data for the appsearch output connector.
 */
public class AppSearchParam extends HashMap<ParameterEnum, String> {

  /**
   * Parameters constants
   */
  public enum ParameterEnum {
    SERVERLOCATION("http://localhost:3002"),
    ENGINENAME("myengine"),
    SERVERKEYSTORE(""),
    SECRETAPIKEY(""),
    USEMAPPERATTACHMENTS("false"),
    CONTENTATTRIBUTENAME("content"),
    URIATTRIBUTENAME("url"),
    CREATEDDATEATTRIBUTENAME("created"),
    MODIFIEDDATEATTRIBUTENAME("last_modified"),
    INDEXINGDATEATTRIBUTENAME("indexed"),
    MIMETYPEATTRIBUTENAME("mime_type"),
    EXTRACTORPATTERN(""),
    REPLACEMENTSTRING(""),
    FIELDLIST("");

    final protected String defaultValue;

    private ParameterEnum(String defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

  private static final long serialVersionUID = -1593234685772720029L;

  protected AppSearchParam(ParameterEnum[] params) {
    super(params.length);
  }

  final public Map<String, Object> buildMap(IHTTPOutput out) throws ManifoldCFException {
    Map<String, Object> rval = new HashMap<>();
    for (Map.Entry<ParameterEnum, String> entry : this.entrySet()) {
      final String key = entry.getKey().name();
      final boolean isPassword = key.endsWith("PASSWORD");
      final boolean isKeystore = key.endsWith("KEYSTORE");
      if (isPassword) {
	// Do not put passwords in plain text in forms
	rval.put(key, out.mapPasswordToKey(entry.getValue()));
      } else if (isKeystore) {
	String keystore = entry.getValue();
	IKeystoreManager localKeystore;
	if (keystore == null || keystore.length() == 0) {
	  localKeystore = KeystoreManagerFactory.make("");
	} else {
	  localKeystore = KeystoreManagerFactory.make("", keystore);
	}

	List<Map<String, String>> certificates = new ArrayList<>();

	String[] contents = localKeystore.getContents();
	for (String alias : contents) {
	  String description = localKeystore.getDescription(alias);
	  if (description.length() > 128) {
	    description = description.substring(0, 125) + "...";
	  }
	  Map<String, String> certificate = new HashMap<>();
	  certificate.put("ALIAS", alias);
	  certificate.put("DESCRIPTION", description);
	  certificates.add(certificate);
	}
	rval.put(key, keystore);
	rval.put(key + "_LIST", certificates);
      } else {
	rval.put(key, entry.getValue());
      }
    }
    return rval;
  }

}
