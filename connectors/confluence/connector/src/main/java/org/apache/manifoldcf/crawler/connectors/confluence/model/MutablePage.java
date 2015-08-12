package org.apache.manifoldcf.crawler.connectors.confluence.model;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * MutablePage class
 * </p>
 * <p>
 * Represents a Confluence Page which is mutable unlike {@code Page} class which can be also initialized using the PageBuilder obtained from
 * <code>Page.builder()</code> method
 * </p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 */
public class MutablePage extends Page {

	public MutablePage() {

	}
	
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

	public void setContent(String content) {
		this.content = content;
	}

	public void setLabels(List<Label> labels) {
		this.labels = labels;
	}

}
