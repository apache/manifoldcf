package org.apache.manifoldcf.crawler.connectors.confluence.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.manifoldcf.crawler.connectors.confluence.model.builder.ConfluenceResourceBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ConfluenceResponse<T extends ConfluenceResource> {

	private List<T> results;
	private int start;
	private int limit;
	private Boolean isLast;
	
	public ConfluenceResponse(List<T> results, int start, int limit, Boolean isLast) {
		this.results = results;
		this.start = start;
		this.limit = limit;
		this.isLast = isLast;
	}
	
	public List<T> getResults() {
		return this.results;
	}
	
	public int getStart() {
		return this.start;
	}
	
	public int getLimit() {
		return this.limit;
	}
	
	public Boolean isLast() {
		return isLast;
	}
	
	public static <T extends ConfluenceResource> ConfluenceResponse<T> fromJson(JSONObject response, ConfluenceResourceBuilder<T> builder) {
		List<T> resources = new ArrayList<T>();
		try {
			JSONArray jsonArray = response.getJSONArray("results");
			for(int i=0,size=jsonArray.length(); i<size;i++) {
				JSONObject jsonPage = jsonArray.getJSONObject(i);
				T resource = (T) builder.fromJson(jsonPage);
				resources.add(resource);
			}
			
			int limit = response.getInt("limit");
			int start = response.getInt("start");
			Boolean isLast = false;
			JSONObject links = response.getJSONObject("_links");
			if(links != null) {
				isLast = links.optString("next", "undefined").equalsIgnoreCase("undefined");
			}
			
			return new ConfluenceResponse<T>(resources, start, limit, isLast);
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return new ConfluenceResponse<T>(new ArrayList<T>(), 0,0,false);
	}
}
