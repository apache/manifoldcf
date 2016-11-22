/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.manifoldcf.crawler.connectors.nuxeo.model.builder.NuxeoResourceBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class NuxeoResponse<T extends NuxeoResource> {

	private List<T> results;
	private int start;
	private int limit;
	private Boolean isLast;

	public NuxeoResponse(List<T> resuts, int start, int limit, Boolean isLast) {
		this.results = resuts;
		this.start = start;
		this.limit = limit;
		this.isLast = isLast;
	}

	/**
	 * @return the results
	 */
	public List<T> getResults() {
		return results;
	}

	/**
	 * @return the start
	 */
	public int getStart() {
		return start;
	}

	/**
	 * @return the limit
	 */
	public int getLimit() {
		return limit;
	}

	/**
	 * @return the isLast
	 */
	public Boolean isLast() {
		return isLast;
	}

	public static <T extends NuxeoResource> NuxeoResponse<T> fromJson(JSONObject response,
			NuxeoResourceBuilder<T> builder) {
		List<T> resources = new ArrayList<T>();
		
		try{
			JSONArray jsonArray = response.getJSONArray("entries");
			
			for(int i=0,size=jsonArray.length();i< size;i++){
				JSONObject jsonDocument = jsonArray.getJSONObject(i);
				T resource= (T) builder.fromJson(jsonDocument);
				resources.add(resource);
			}
			int limit = response.getInt("pageSize");
			int start = response.getInt("currentPageIndex");
			String isNextPage = response.getString("isNextPageAvailable");
			Boolean isLast =  false;
			
			if(isNextPage.equalsIgnoreCase("false")){
				isLast = true;
			}
			
			
			
			return new NuxeoResponse<>(resources, start, limit, isLast);
			
		}catch(JSONException e){
			e.printStackTrace();
		}
		
		return new NuxeoResponse<T>(new ArrayList<T>(), 0, 0, false);
	}

}
