package org.apache.manifoldcf.crawler.connectors.confluence.model;

import org.apache.commons.lang.WordUtils;

/**
 * <p>PageType class</p>
 * <p>Represents the kind of pages we can have in Confluence</p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 *
 */
public enum PageType {

	PAGE, ATTACHMENT;
	
	public static PageType fromName(String type) {
		for(PageType pageType: values()) {
			if(pageType.name().equalsIgnoreCase(type)) {
				return pageType;
			}
		}
		
		return PageType.PAGE;
	}
	
	public String toString() {
		return WordUtils.capitalize(name().toLowerCase());
	}
}
