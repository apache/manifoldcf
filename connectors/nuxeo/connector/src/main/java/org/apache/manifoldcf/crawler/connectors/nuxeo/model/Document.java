package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.manifoldcf.core.common.DateParser;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.builder.NuxeoResourceBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.Maps;

public class Document extends NuxeoResource {

	public static final String DELETED = "deleted";
	// KEYS
	protected static final String KEY_UID = "uid";
	protected static final String KEY_TITLE = "title";
	protected static final String KEY_LAST_MODIFIED = "lastModified";
	protected static final String KEY_STATE = "state";
	protected static final String KEY_TYPE = "type";
	protected static final String KEY_PATH = "path";
	protected static final String KEY_MEDIATYPE = "mediaType";
	protected static final String KEY_IS_CHECKED_OUT = "isCheckedOut";
	protected static final String KEY_PARENT_REF = "parentRef";
	protected static final String KEY_REPOSITORY = "repository";
	protected static final String KEY_ATTACHMENTS = "atachments";

	protected static final String KEY_PROPERTIES = "properties";

	protected static final String DC_PREFFIX = "dc:";
	protected static final String KEY_DC_DESCRIPTION = "description";
	protected static final String KEY_DC_LANGUAGE = "language";
	protected static final String KEY_DC_COVERAGE = "coverage";
	protected static final String KEY_DC_VALID = "valid";
	protected static final String KEY_DC_CREATOR = "creator";
	protected static final String KEY_DC_CREATED = "created";
	protected static final String KEY_DC_LAST_CONTRIBUTOR = "dc:lastContributor";
	protected static final String KEY_DC_RIGHTS = "rights";
	protected static final String KEY_DC_EXPIRED = "expired";
	protected static final String KEY_DC_ISSUED = "issued";
	protected static final String KEY_DC_NATURE = "nature";
	protected static final String KEY_DC_SUBJECTS = "subjects";
	protected static final String KEY_DC_CONTRIBUTORS = "contributors";
	protected static final String KEY_DC_SOURCE = "source";
	protected static final String KEY_DC_PUBLISHER = "publisher";

	protected static final String NOTE_PREFFIX = "note:";
	protected static final String NOTE_SAVE_PREFFIX = "note__";
	protected static final String KEY_NOTE_NOTE = "note";
	protected static final String KEY_NOTE_MYMETYPE = "mime_type";

	protected static final String ATT_PREFFIX = "files:";
	protected static final String ATT_MAIN_PREFFIX = "file:";
	protected static final String ATT_CONTENT = "content";
	protected static final String ATT_FILES = "files";

	protected static final String DOCUMENT_SIZE = "size";

	// Attributes
	protected String uid;
	protected String title;
	protected Date lastModified;
	protected String state;
	protected String mediatype = "text/html; charset=utf-8";
	protected long length;
	protected String content;
	protected String path;
	protected String type;
	protected Boolean isCheckedOut;
	protected String parentRef;
	protected String repository;

	protected String description;
	protected String language;
	protected String coverage;
	protected String valid;
	protected String creator;
	protected String created;
	protected String lastContributor;
	protected String rights;
	protected String expired;
	protected String issued;
	protected String nature;
	protected List<String> subjects = new ArrayList<>();
	protected List<String> contributors = new ArrayList<>();
	protected String source;
	protected String publisher;

	protected String note;
	protected String noteMimeType;

	protected List<Attachment> attachments = new ArrayList<>();

	@SuppressWarnings("unused")
	private JSONObject delegated;

	public Document() {

	}

	// Getters
	public String getUid() {
		return uid;
	}

	public String getTitle() {
		return title;
	}

	public Date getLastModified() {
		return lastModified;
	}

	public String getState() {
		return state;
	}

	public String getPath() {
		return path;
	}

	public String getType() {
		return type;
	}

	public String getMediatype() {
		return this.mediatype;
	}

	public Boolean getIsCheckedOut() {
		return isCheckedOut;
	}

	public String getParentRef() {
		return parentRef;
	}

	public String getRepository() {
		return repository;
	}

	public long getLenght() {
		return this.length;
	}

	public String getContent() {
		return content;
	}

	public String getDescription() {
		return description;
	}

	public String getLanguage() {
		return language;
	}

	public String getCoverage() {
		return coverage;
	}

	public String getValid() {
		return valid;
	}

	public String getCreator() {
		return creator;
	}

	public String getCreated() {
		return created;
	}

	public String getLastContributor() {
		return lastContributor;
	}

	public String getRights() {
		return rights;
	}

	public String getExpired() {
		return expired;
	}

	public String getIssued() {
		return issued;
	}

	public String getNature() {
		return nature;
	}

	public List<String> getSubjects() {
		return subjects;
	}

	public List<String> getContributors() {
		return contributors;
	}

	public String getSource() {
		return source;
	}

	public String getPublisher() {
		return publisher;
	}

	public String getNote() {
		return note;
	}

	public String getNoteMimeType() {
		return noteMimeType;
	}

	public List<Attachment> getAttachments() {
		return attachments;
	}

	public boolean hasContent() {
		return this.length > 0 && this.content != null;
	}

	public InputStream getContentStream() {
		String contentStream = content != null ? content : "";
		return new ByteArrayInputStream(contentStream.getBytes(StandardCharsets.UTF_8));
	}

	public Map<String, Object> getMetadataAsMap() {
		Map<String, Object> docMetadata = Maps.newHashMap();

		docMetadata.put(KEY_UID, this.uid);
		docMetadata.put(KEY_TITLE, this.title);
		if (this.lastModified != null)
			docMetadata.put(KEY_LAST_MODIFIED, DateParser.formatISO8601Date(this.lastModified));
		docMetadata.put(KEY_STATE, this.state);
		docMetadata.put(KEY_MEDIATYPE, this.mediatype);
		docMetadata.put(KEY_TYPE, this.type);
		docMetadata.put(KEY_PATH, this.path);
		if (this.isCheckedOut != null)
			docMetadata.put(KEY_IS_CHECKED_OUT, this.isCheckedOut.toString());
		docMetadata.put(KEY_REPOSITORY, this.repository);
		docMetadata.put(KEY_PARENT_REF, this.parentRef);

		addIfNotEmpty(docMetadata, KEY_DC_DESCRIPTION, this.description);
		addIfNotEmpty(docMetadata, KEY_DC_LANGUAGE, this.language);
		addIfNotEmpty(docMetadata, KEY_DC_COVERAGE, this.coverage);
		addIfNotEmpty(docMetadata, KEY_DC_VALID, this.valid);
		addIfNotEmpty(docMetadata, KEY_DC_CREATOR, this.creator);
		addIfNotEmpty(docMetadata, KEY_DC_LAST_CONTRIBUTOR, this.lastContributor);
		addIfNotEmpty(docMetadata, KEY_DC_RIGHTS, this.rights);
		addIfNotEmpty(docMetadata, KEY_DC_EXPIRED, this.expired);
		addIfNotEmpty(docMetadata, KEY_DC_CREATED, this.created);
		addIfNotEmpty(docMetadata, KEY_DC_ISSUED, this.issued);
		addIfNotEmpty(docMetadata, KEY_DC_NATURE, this.nature);
		addIfNotEmpty(docMetadata, KEY_DC_SOURCE, this.source);
		addIfNotEmpty(docMetadata, KEY_DC_PUBLISHER, this.publisher);
		addIfNotEmpty(docMetadata, KEY_DC_SUBJECTS, this.subjects);
		addIfNotEmpty(docMetadata, KEY_DC_CONTRIBUTORS, this.contributors);
		addIfNotEmpty(docMetadata, NOTE_SAVE_PREFFIX + KEY_NOTE_NOTE, this.note);
		addIfNotEmpty(docMetadata, NOTE_SAVE_PREFFIX + KEY_NOTE_MYMETYPE, this.noteMimeType);

		return docMetadata;
	}

	public void addIfNotEmpty(Map<String, Object> docMetadata, String key, Object obj) {
		if (obj != null && ((obj instanceof String && !((String) obj).isEmpty()) || !(obj instanceof String))) {
			docMetadata.put(key, obj);
		}
	}

	public static NuxeoResourceBuilder<? extends Document> builder() {
		return new DocumentBuilder();
	}

	public static class DocumentBuilder implements NuxeoResourceBuilder<Document> {

		public Document fromJson(JSONObject jsonDocument) {
			return fromJson(jsonDocument, new Document());
		}

		public Document fromJson(JSONObject jsonDocument, Document document) {

			try {
				String uid = jsonDocument.getString(KEY_UID);
				String title = jsonDocument.getString(KEY_TITLE);
				Date lastModified = DateParser.parseISO8601Date(jsonDocument.optString(KEY_LAST_MODIFIED, ""));
				String state = jsonDocument.optString(KEY_STATE, "");
				String path = jsonDocument.optString(KEY_PATH, "");
				String type = jsonDocument.optString(KEY_TYPE, "");
				Boolean isCheckedOut = jsonDocument.optBoolean(KEY_IS_CHECKED_OUT);
				String repository = jsonDocument.optString(KEY_REPOSITORY, "");
				String parentRef = jsonDocument.optString(KEY_PARENT_REF, "");

				document.uid = uid;
				document.title = title;
				document.lastModified = lastModified;
				document.state = state;
				document.path = path;
				document.type = type;
				document.isCheckedOut = isCheckedOut;
				document.repository = repository;
				document.parentRef = parentRef;

				document.length = (document.content != null) ? document.content.getBytes().length : 0;

				JSONObject properties = (JSONObject) jsonDocument.opt(KEY_PROPERTIES);

				if (properties != null) {

					document.description = properties.optString(DC_PREFFIX + KEY_DC_DESCRIPTION);
					document.language = properties.optString(DC_PREFFIX + KEY_DC_LANGUAGE);
					document.coverage = properties.optString(DC_PREFFIX + KEY_DC_COVERAGE);
					document.valid = properties.optString(DC_PREFFIX + KEY_DC_VALID);
					document.creator = properties.optString(DC_PREFFIX + KEY_DC_CREATOR);
					document.lastContributor = properties.optString(DC_PREFFIX + KEY_DC_LAST_CONTRIBUTOR);
					document.rights = properties.optString(DC_PREFFIX + KEY_DC_RIGHTS);
					document.expired = properties.optString(DC_PREFFIX + KEY_DC_EXPIRED);
					document.created = properties.optString(DC_PREFFIX + KEY_DC_CREATED);
					document.issued = properties.optString(DC_PREFFIX + KEY_DC_ISSUED);
					document.nature = properties.optString(DC_PREFFIX + KEY_DC_NATURE);
					document.source = properties.optString(DC_PREFFIX + KEY_DC_SOURCE);
					document.publisher = properties.optString(DC_PREFFIX + KEY_DC_PUBLISHER);

					JSONArray subjectsArray = properties.optJSONArray(DC_PREFFIX + KEY_DC_SUBJECTS);

					if (subjectsArray != null)
						for (int i = 0; i < subjectsArray.length(); i++) {
							if (subjectsArray.optString(i) != null)
								document.subjects.add(subjectsArray.getString(i));
						}

					JSONArray contributorsArray = properties.optJSONArray(DC_PREFFIX + KEY_DC_CONTRIBUTORS);

					if (contributorsArray != null)
						for (int i = 0; i < contributorsArray.length(); i++) {
							if (contributorsArray.optString(i) != null)
								document.contributors.add(contributorsArray.getString(i));
						}

					if (document.type.equalsIgnoreCase(DocumentType.NOTE.value())) {
						document.note = properties.optString(NOTE_PREFFIX + KEY_NOTE_NOTE);
						document.noteMimeType = properties.optString(NOTE_PREFFIX + KEY_NOTE_MYMETYPE);
					}

					JSONObject mainFile = properties.optJSONObject(ATT_MAIN_PREFFIX + ATT_CONTENT);

					if (mainFile != null) {
						Attachment att = new Attachment();

						att.name = mainFile.optString(Attachment.ATT_KEY_NAME);
						att.mime_type = mainFile.optString(Attachment.ATT_KEY_MIME_TYPE);
						att.url = mainFile.optString(Attachment.ATT_KEY_URL);
						att.length = mainFile.optLong(Attachment.ATT_KEY_LENGTH);

						document.attachments.add(att);
					}

					JSONArray files = properties.optJSONArray(ATT_PREFFIX + ATT_FILES);

					if (files != null)
						for (int i = 0; i < files.length(); i++) {
							if (files.optJSONObject(i) != null) {
								Attachment att = new Attachment();

								JSONObject fileObj = files.optJSONObject(i);
								JSONObject file = fileObj.getJSONObject(Attachment.ATT_KEY_FILE);

								att.name = file.optString(Attachment.ATT_KEY_NAME);
								att.mime_type = file.optString(Attachment.ATT_KEY_MIME_TYPE);
								att.url = file.optString(Attachment.ATT_KEY_URL);
								att.length = file.optLong(Attachment.ATT_KEY_LENGTH);

								document.attachments.add(att);
							}

						}
				}

				document.delegated = jsonDocument;

				return document;

			} catch (JSONException e) {
				e.printStackTrace();
			}

			return new Document();

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.apache.manifoldcf.crawler.connectors.nuxeo.model.builder.
		 * NuxeoResourceBuilder#getType()
		 */
		@Override
		public Class<Document> getType() {
			return Document.class;
		}

	}
}
