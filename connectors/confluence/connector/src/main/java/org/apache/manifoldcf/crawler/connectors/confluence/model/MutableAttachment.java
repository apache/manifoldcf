package org.apache.manifoldcf.crawler.connectors.confluence.model;

import java.io.InputStream;
import java.util.Date;

/**
 * <p>
 * Mutable Attachment class
 * </p>
 * <p>
 * Represents a Confluence Attachment which can be mutated
 * </p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 */
public class MutableAttachment extends Attachment {

	public void setId(String id) {
		this.id = id;
	}
	
	public void setSpace(String space) {
		this.space = space;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setUrlContext(String urlContext) {
		this.urlContext = urlContext;
	}
	
	public void setUrl(String url) {
		this.url = url;
	}
	
	public void setWebUrl(String webUrl) {
		this.webUrl = webUrl;
	}
	
	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}
	
	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}
	
	public void setType(PageType type) {
		this.type = type;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public void setVersion(int version) {
		this.version = version;
	}
	
	public void setCreator(String creator) {
		this.creator = creator;
	}
	
	public void setCreatorUsername(String creatorUsername) {
		this.creatorUsername = creatorUsername;
	}
	
	public void setLastModifier(String lastModifier) {
		this.lastModifier = lastModifier;
	}
	
	public void setLastModifierUsername(String lastModifierUsername) {
		this.lastModifierUsername = lastModifierUsername;
	}
	
	public void setMediaType(String mediaType) {
		this.mediaType = mediaType;
	}
	
	public void setLength(long length) {
		this.length = length;
	}
	
	public void setDownloadUrl(String downloadUrl) {
		this.downloadUrl = downloadUrl;
	}
	
	public void setContentStream(InputStream contentStream) {
		this.contentStream = contentStream;
	}	

}
