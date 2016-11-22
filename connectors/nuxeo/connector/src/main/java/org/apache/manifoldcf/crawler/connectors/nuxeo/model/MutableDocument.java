/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

import java.util.Date;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class MutableDocument extends Document{

	public MutableDocument(){
		
	}
	
	//Setters
	public void setUid(String uid){
		this.uid = uid;
	}
	
	public void setTitle(String title){
		this.title = title;
	}
	
	public void setLastModified(Date lastModified){
		this.lastModified = lastModified;
	}


}
