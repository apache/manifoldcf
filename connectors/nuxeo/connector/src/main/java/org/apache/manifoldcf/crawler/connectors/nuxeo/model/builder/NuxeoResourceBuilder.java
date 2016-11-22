/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.model.builder;

import org.apache.manifoldcf.crawler.connectors.nuxeo.model.NuxeoResource;
import org.json.JSONObject;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public interface NuxeoResourceBuilder <T extends NuxeoResource> {
	
	T fromJson(JSONObject jsonDocument);
	
	T fromJson(JSONObject jsonDocument, T document);
	
	Class<T> getType();

}
