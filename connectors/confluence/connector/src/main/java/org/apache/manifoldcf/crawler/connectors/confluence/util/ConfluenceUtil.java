package org.apache.manifoldcf.crawler.connectors.confluence.util;

/**
 * <p>Utility class for Confluence connectors</p>
 * 
 * @author Antonio David Perez Morales <adperezmorales@gmail.com>
 *
 */
public class ConfluenceUtil {

	private static final String ATTACHMENT_ID_PREFIX = "att";
	
	/**
	 * <p>Generates a repository document identifier for the specific attachment and page to be used for Repository Documents for attachment pages</p>
	 * @param attachmentId
	 * @param pageId
	 * @return a generated 
	 */
	public static String generateRepositoryDocumentIdentifier(String attachmentId, String pageId) {
		StringBuilder sb = new StringBuilder();
		sb.append(attachmentId).append("-").append(pageId);
		return sb.toString();
	}
	
	/**
	 * <p>Checks if the given id is an attachment or not</p>
	 * @param id
	 * @return a {@code Boolean} indicating if the id is related to an attachment or not 
	 */
	public static Boolean isAttachment(String id) {
		return id.startsWith(ATTACHMENT_ID_PREFIX);
	}
	
	/**
	 * <p>Gets the attachment id and page id from a repository document id</p>
	 * @param id the repository document id
	 * @return an Array containing the attachment and page ids where index 0 is the attachment id and index 1 is the page id
	 */
	public static String[] getAttachmentAndPageId(String id) {
		return id.split("-");
	}
}
