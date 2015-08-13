package org.apache.manifoldcf.crawler.connectors.confluence.model;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Spaces extends ArrayList<Space> {

	private static Logger logger = LoggerFactory.getLogger(Spaces.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = -5334215263162816914L;

	
	public static Spaces fromJson(JSONArray jsonSpaces) {
		Spaces spaces = new Spaces();
		for(int i=0,len=jsonSpaces.length();i<len;i++) {
			try {
				JSONObject spaceJson = jsonSpaces.getJSONObject(i);
				Space space = Space.fromJson(spaceJson);
				spaces.add(space);
			} catch (JSONException e) {
				logger.debug("Error obtaining JSON item from spaces. Item {} is not a JSON Object", i);
				e.printStackTrace();
				continue;
			}
		}
		
		return spaces;
		
	}
}
