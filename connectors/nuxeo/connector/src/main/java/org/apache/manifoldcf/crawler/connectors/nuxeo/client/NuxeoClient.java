/**
 * 
 */
package org.apache.manifoldcf.crawler.connectors.nuxeo.client;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.EntityUtils;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.connectorcommon.common.InterruptibleSocketFactory;
import org.apache.manifoldcf.connectorcommon.interfaces.KeystoreManagerFactory;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.util.URLEncoder;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.Acl;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.Document;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.MutableAcl;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.MutableDocument;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.NuxeoResource;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.NuxeoResponse;
import org.apache.manifoldcf.crawler.connectors.nuxeo.model.builder.NuxeoResourceBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class NuxeoClient {
	private static final String CONTENT_PATH = "site/api/v1/";
	private static final String CONTENT_QUERY = CONTENT_PATH + "query";
	private static final String CONTENT_UUID = CONTENT_PATH + "id";
	public static final String CONTENT_AUTHORITY = CONTENT_PATH + "user";
	public static final String CONTENT_TAG = CONTENT_PATH + "";

	private Logger logger = LoggerFactory.getLogger(NuxeoClient.class);

	private String protocol;
	private Integer port;
	private String host;
	private String path;
	private String username;
	private String password;

	private CloseableHttpClient httpClient;
	private HttpClientContext httpContext;

	public NuxeoClient(String protocol, String host, Integer port, String path, String username, String password)
			throws ManifoldCFException {

		this.protocol = protocol;
		this.host = host;
		this.port = port;
		this.path = path;
		this.username = username;
		this.password = password;

		connect();
	}

	private void connect() throws ManifoldCFException {
		int socketTimeout = 900000;
		int connectionTimeout = 60000;
		int inactivityTimeout = 2000;

		SSLSocketFactory httpsSocketFactory = KeystoreManagerFactory.getTrustingSecureSocketFactory();
		SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(
				new InterruptibleSocketFactory(httpsSocketFactory, connectionTimeout), NoopHostnameVerifier.INSTANCE);

		PoolingHttpClientConnectionManager poolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager(
				RegistryBuilder.<ConnectionSocketFactory> create()
						.register("http", PlainConnectionSocketFactory.getSocketFactory())
						.register("https", sslConnectionSocketFactory).build());

		poolingHttpClientConnectionManager.setDefaultMaxPerRoute(1);
		poolingHttpClientConnectionManager.setValidateAfterInactivity(inactivityTimeout);
		poolingHttpClientConnectionManager
				.setDefaultSocketConfig(SocketConfig.custom().setTcpNoDelay(true).setSoTimeout(socketTimeout).build());

		RequestConfig.Builder requestBuilder = RequestConfig.custom().setCircularRedirectsAllowed(true)
				.setSocketTimeout(socketTimeout).setExpectContinueEnabled(true).setConnectTimeout(connectionTimeout)
				.setConnectionRequestTimeout(socketTimeout);

		httpClient = HttpClients.custom().setConnectionManager(poolingHttpClientConnectionManager)
				.disableAutomaticRetries().setDefaultRequestConfig(requestBuilder.build())
				.setRequestExecutor(new HttpRequestExecutor(socketTimeout))
				.setRedirectStrategy(new DefaultRedirectStrategy()).build();
	}

	public Boolean check() throws Exception {
		HttpResponse response;

		try {
			if (httpClient == null)
				connect();

			String url = String.format("%s://%s:%s%s/%s?pageSize=1", protocol, host, port, path, CONTENT_QUERY);

			HttpGet httpGet = createGetRequest(url);
			response = httpClient.execute(httpGet);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200)
				throw new Exception("[Checking connection] Nuxeo server appears to be down");
			else
				return true;
		} catch (IOException e) {
			throw new Exception("Nuxeo apeears to be down", e);
		}
	}

	public boolean checkAuth() throws Exception {
		// HttpResponse response;

		// try {
		if (httpClient == null)
			connect();

		try {
			getUserAuthorities("Administrator");
			return true;
		} catch (IOException e) {
			throw new Exception("Nuxeo apeears to be down", e);
		}
	}

	/**
	 * @param url
	 * @return
	 */
	private HttpGet createGetRequest(String url) {
		String sanitizedUrl = sanitizedUrl(url);

		HttpGet httpGet = new HttpGet(sanitizedUrl);

		httpGet.addHeader("accepted", "application/json");

		if (useBasicAuthentication())
			httpGet.addHeader("Authorization", "Basic " + Base64.encodeBase64String(
					String.format("%s:%s", this.username, this.password).getBytes(Charset.forName("UTF-8"))));

		return httpGet;
	}

	/**
	 * @param url
	 * @return
	 */
	private String sanitizedUrl(String url) {
		int colonIndex = url.indexOf(":");

		String urlWithoutProtocol = url.startsWith("http") ? url.substring(colonIndex + 3) : url;
		String sanitizedUrl = urlWithoutProtocol.replace("\\/+", "/");
		return url.substring(0, colonIndex) + "://" + sanitizedUrl;
	}

	/**
	 * @return
	 */
	private boolean useBasicAuthentication() {
		return this.username != null && !"".equals(this.username) && this.password != null;
	}

	/**
	 * @param lastStart
	 * @param defaultSize
	 * @param object
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public NuxeoResponse<Document> getDocuments(List<String> domains, List<String> documentsType,
			String lastSeedVersion, int start, int limit, Object object) throws Exception {

		String url = null;

		String q = createQuery(lastSeedVersion, domains, documentsType);
		if (lastSeedVersion == null || lastSeedVersion.isEmpty())
			url = String.format("%s://%s:%s/%s/%s?%s&pageSize=%s&currentPageIndex=%s", protocol, host, port, path,
					CONTENT_QUERY, q, limit, start);
		else
			url = String.format("%s://%s:%s/%s/%s?%s&pageSize=%s&currentPageIndex=%s&queryParams=%s", protocol, host,
					port, path, CONTENT_QUERY, q, limit, start, lastSeedVersion);

		url = sanitizedUrl(url);

		return (NuxeoResponse<Document>) getNuxeoResource(url, Document.builder());
	}

	public String createQuery(String lastSeedVersion, List<String> domains, List<String> documentsType) {
		String query = "SELECT * FROM Document";

		if (!domains.isEmpty() || !documentsType.isEmpty() || (lastSeedVersion != null && !lastSeedVersion.isEmpty())) {
			query += " WHERE ";

			if (lastSeedVersion != null && !lastSeedVersion.isEmpty()) {
				query += "dc:modified > ?";
			}

			if (!domains.isEmpty()) {
				Iterator<String> itdom = domains.iterator();

				if (lastSeedVersion != null && !lastSeedVersion.isEmpty())
					query = String.format("%s %s", query, " AND ");

				query = String.format("%s ( ecm:path STARTSWITH '/%s'", query, itdom.next());

				while (itdom.hasNext()) {
					query = String.format("%s OR ecm:path STARTSWITH '/%s'", query, itdom.next());
				}

				query = String.format("%s)", query);
			}

			if (!documentsType.isEmpty()) {
				Iterator<String> itDocTy = documentsType.iterator();

				if ((lastSeedVersion != null && !lastSeedVersion.isEmpty()) || !domains.isEmpty())
					query = String.format("%s %s", query, " AND ");
				query = String.format("%s ( ecm:primaryType = '%s'", query, itDocTy.next());

				while (itDocTy.hasNext()) {
					query = String.format("%s OR ecm:primaryType = '%s'", query, itDocTy.next());
				}

				query = String.format("%s)", query);
			}

		}

		query = URLEncoder.encode(query);
		query = String.format("%s%s", "query=", query);
		return query;
	}

	/**
	 * @param url
	 * @param builder
	 * @return
	 */
	private NuxeoResponse<? extends NuxeoResource> getNuxeoResource(String url,
			NuxeoResourceBuilder<? extends Document> builder) throws Exception {

		try {
			HttpGet httpGet = createGetRequest(url);
			HttpResponse response = executeRequest(httpGet);

			NuxeoResponse<? extends NuxeoResource> nuxeoResponse = responseFromHttpEntity(response.getEntity(),
					builder);
			EntityUtils.consume(response.getEntity());
			return nuxeoResponse;
		} catch (IOException e) {
			throw new Exception("Nuxeo appears to be down", e);
		}
	}

	/**
	 * @param entity
	 * @param builder
	 * @return
	 */
	private <T extends NuxeoResource> NuxeoResponse<T> responseFromHttpEntity(HttpEntity entity,
			NuxeoResourceBuilder<T> builder) throws Exception {

		String stringEntity = EntityUtils.toString(entity);

		JSONObject responseObject;

		try {
			responseObject = new JSONObject(stringEntity);
			NuxeoResponse<T> response = NuxeoResponse.fromJson(responseObject, builder);

			return response;
		} catch (JSONException e) {
			throw new Exception();
		}
	}

	/**
	 * @param httpGet
	 * @return
	 */
	private HttpResponse executeRequest(HttpUriRequest request) throws Exception {

		HttpResponse response = httpClient.execute(request, httpContext);

		if (response.getStatusLine().getStatusCode() != 200) {
			throw new Exception("Nuxeo error. " + response.getStatusLine().getStatusCode() + " "
					+ response.getStatusLine().getReasonPhrase());
		}
		return response;
	}

	/**
	 * @param documentId
	 * @return
	 * @throws ServiceInterruption 
	 */
	public Document getDocument(String documentId) throws ServiceInterruption {

		String url = getPathDocument(documentId);

		url = sanitizedUrl(url);

		try {

			HttpGet httpGet = createGetRequest(url);
			httpGet.addHeader("X-NXDocumentProperties", "*");
			HttpResponse response = executeRequest(httpGet);
			HttpEntity entity = response.getEntity();
			MutableDocument mDocument = documentFromHttpEmpty(entity);
			EntityUtils.consume(entity);

			return mDocument;
		} catch (Exception e) {
			logger.debug("Failed documentId:" + documentId,e);
			long interruptionRetryTime = 5L * 60L * 1000L;
			String message = "Server appears down during seeding: " + e.getMessage();
			throw new ServiceInterruption(message, e, System.currentTimeMillis() + interruptionRetryTime,
					-1L, 3, true);
		}
	}

	public Acl getAcl(String documentId) {

		String url = getPathDocument(documentId);
		url += "/@acl";

		url = sanitizedUrl(url);

		try {

			HttpGet httpGet = createGetRequest(url);
			HttpResponse response = executeRequest(httpGet);
			HttpEntity entity = response.getEntity();
			MutableAcl mAcl = aclFromHttpEmpty(entity);
			EntityUtils.consume(entity);

			return mAcl;
		} catch (Exception e) {
		}

		return new Acl();
	}

	public String getPathDocument(String documentId) {
		return String.format("%s://%s:%s%s/%s/%s", protocol, host, port, path, CONTENT_UUID, documentId);
	}

	/**
	 * @param entity
	 * @return
	 */
	private MutableDocument documentFromHttpEmpty(HttpEntity entity) throws Exception {
		String stringEntity = EntityUtils.toString(entity);

		JSONObject responseObject;

		try {
			responseObject = new JSONObject(stringEntity);

			@SuppressWarnings("unchecked")
			MutableDocument mDocument = ((NuxeoResourceBuilder<MutableDocument>) MutableDocument.builder())
					.fromJson(responseObject, new MutableDocument());

			return mDocument;
		} catch (JSONException jsonException) {
			throw new Exception("Error parsing JSON document response data");
		}
	}

	private MutableAcl aclFromHttpEmpty(HttpEntity entity) throws Exception {
		String stringEntity = EntityUtils.toString(entity);

		JSONObject responseObject;

		try {
			responseObject = new JSONObject(stringEntity);

			@SuppressWarnings("unchecked")
			MutableAcl mAcl = ((NuxeoResourceBuilder<MutableAcl>) MutableAcl.builder()).fromJson(responseObject,
					new MutableAcl());

			return mAcl;
		} catch (JSONException jsonException) {
			throw new Exception("Error parsing JSON document response data");
		}
	}

	public void close() {
		if (httpClient != null) {
			try {
				httpClient.close();
			} catch (IOException ioException) {
				ioException.printStackTrace();
			}
		}

	}

	public List<String> getUserAuthorities(String username) throws Exception {
		List<String> authorities = new ArrayList<String>();

		String url = String.format("%s://%s:%s/%s/%s/%s", protocol, host, port, path, CONTENT_AUTHORITY, username);

		url = sanitizedUrl(url);

		HttpGet httpGet = createGetRequest(url);
		HttpResponse response = executeRequest(httpGet);
		HttpEntity entity = response.getEntity();
		String stringEntity = EntityUtils.toString(entity);
		EntityUtils.consume(entity);

		JSONObject user = new JSONObject(stringEntity);
		authorities.add(user.getString("id"));

		JSONObject properties = user.optJSONObject("properties");

		if (properties != null) {
			JSONArray groups = properties.optJSONArray("groups");

			for (int i = 0; i < groups.length(); i++) {
				authorities.add(groups.getString(i));
			}
		}

		return authorities;
	}

	/**
	 * @param uid
	 * @return
	 * @throws Exception
	 */
	public String[] getTags(String uid) throws Exception {
		List<String> tags = new ArrayList<>();

		String query = "select%20*%20from%20Tagging%20where%20relation%3Asource='" + uid + "'";
		String url = String.format("%s://%s:%s/%s/%s?query=%s", protocol, host, port, path, CONTENT_QUERY, query);
		url = sanitizedUrl(url);

		HttpGet httpGet = createGetRequest(url);
		HttpResponse response = executeRequest(httpGet);
		HttpEntity entity = response.getEntity();
		String stringEntity = EntityUtils.toString(entity);
		EntityUtils.consume(entity);

		JSONArray tagsObject = new JSONObject(stringEntity).getJSONArray("entries");

		for (int i = 0; i < tagsObject.length(); i++) {
			tags.add(tagsObject.getJSONObject(i).getString("title"));
		}

		return tags.toArray(new String[tags.size()]);
	}

}
