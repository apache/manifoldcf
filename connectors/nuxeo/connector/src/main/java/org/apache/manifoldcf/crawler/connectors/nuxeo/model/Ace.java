/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class Ace {

	protected String name;
	protected boolean granted;
	protected String status;
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}
	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}
	/**
	 * @return the granted
	 */
	public boolean isGranted() {
		return granted;
	}
	/**
	 * @param granted the granted to set
	 */
	public void setGranted(boolean granted) {
		this.granted = granted;
	}
	/**
	 * @return the status
	 */
	public String getStatus() {
		return status;
	}
	/**
	 * @param status the status to set
	 */
	public void setStatus(String status) {
		this.status = status;
	}
	
	
}
