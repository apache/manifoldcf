package org.apache.manifoldcf.crawler.connectors.confluence.model;

import org.json.JSONObject;

public class Space {

	private static final String KEY_NAME = "name";
	private static final String KEY_KEY = "key";
	private static final String KEY_TYPE = "type";
	private static final String KEY_URL = "url";
	
	private String key;
	private String name;
	private String type;
	private String url;
	
	public Space() {
		
	}
	
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	
	public static Space fromJson(JSONObject spaceJson) {
		Space space = new Space();
		space.key = spaceJson.optString(KEY_KEY, "");
		space.name = spaceJson.optString(KEY_NAME, "");
		space.type = spaceJson.optString(KEY_TYPE, "");
		space.url = spaceJson.optString(KEY_URL, "");
		return space;
	}
	
}
