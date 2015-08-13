package org.apache.manifoldcf.crawler.connectors.confluence.model;

import org.apache.manifoldcf.crawler.connectors.confluence.model.builder.ConfluenceResourceBuilder;
import org.json.JSONObject;

/**
 * <p>
 * Label class
 * </p>
 * <p>
 * Represents a Confluence Label
 * </p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 */
public class Label extends ConfluenceResource{

	protected static final String KEY_LINKS = "_links";
	protected static final String KEY_ID = "id";
	protected static final String KEY_SELF = "self";
	protected static final String KEY_PREFIX = "prefix";
	protected static final String KEY_NAME = "name";

	protected String id;
	protected String prefix;
	protected String name;

	@SuppressWarnings("unused")
	private JSONObject delegated;

	public Label() {

	}

	public String getId() {
		return this.id;
	}

	public String getPrefix() {
		return this.prefix;
	}

	public String getName() {
		return this.name;
	}

	public static LabelBuilder builder() {
		return new LabelBuilder();
	}

	/**
	 * <p>
	 * LabelBuilder internal class
	 * </p>
	 * <p>
	 * Used to build Labels
	 * </p>
	 * 
	 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
	 *
	 */
	public static class LabelBuilder implements ConfluenceResourceBuilder<Label>{

		public Label fromJson(JSONObject jsonLabel) {
			return fromJson(jsonLabel, new Label());
		}

		public Label fromJson(JSONObject jsonPage, Label label) {

			label.id = jsonPage.optString(KEY_ID, "");
			label.prefix = jsonPage.optString(KEY_PREFIX, "");
			label.name = jsonPage.optString(KEY_NAME, "");

			label.delegated = jsonPage;

			return label;

		}

		@Override
		public Class<Label> getType() {
			return Label.class;
		}

	}
}
