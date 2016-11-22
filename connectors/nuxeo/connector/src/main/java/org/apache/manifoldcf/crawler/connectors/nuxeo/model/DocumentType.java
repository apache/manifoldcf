/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public enum DocumentType {

	NOTE("note"),FILE("file");
	
	private String value;
	
	DocumentType(String value){
		this.value = value;
	}
	
	public String value(){
		return value;
	}
}
