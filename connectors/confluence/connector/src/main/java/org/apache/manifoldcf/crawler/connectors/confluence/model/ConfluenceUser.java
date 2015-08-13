package org.apache.manifoldcf.crawler.connectors.confluence.model;

import java.util.List;

/**
 * <p>ConfluenceUser class</p>
 * <p>Represents a Confluence user</p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 *
 */
public class ConfluenceUser {
	  private final String username;
	  private final List<String> authorities;

	  public ConfluenceUser(String username, List<String> authorities) {
	    this.username = username;
	    this.authorities = authorities;
	  }

	  public String getUsername() {
	    return username;
	  }

	  public List<String> getAuthorities() {
	    return authorities;
	  }
	}
