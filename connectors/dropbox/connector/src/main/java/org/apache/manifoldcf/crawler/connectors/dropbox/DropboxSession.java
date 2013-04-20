/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.manifoldcf.crawler.connectors.dropbox;

import com.dropbox.client2.session.AppKeyPair;
import java.util.Map;
import com.dropbox.client2.session.WebAuthSession;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DeltaEntry;
import com.dropbox.client2.DropboxAPI.DropboxInputStream;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.jsonextract.JsonExtractionException;
import com.dropbox.client2.jsonextract.JsonList;
import com.dropbox.client2.jsonextract.JsonMap;
import com.dropbox.client2.jsonextract.JsonThing;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;
import com.dropbox.client2.session.WebAuthSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.json.simple.parser.ParseException;

/**
 *
 * @author andrew
 */
public class DropboxSession {

    private DropboxAPI<?> client;
    private String cursor = null;
    

    DropboxSession(Map<String, String> parameters) {
        AppKeyPair appKeyPair = new AppKeyPair(parameters.get(DropboxConfig.APP_KEY_PARAM), parameters.get(DropboxConfig.APP_SECRET_PARAM));
        WebAuthSession session = new WebAuthSession(appKeyPair, WebAuthSession.AccessType.DROPBOX);
        AccessTokenPair ac = new AccessTokenPair(parameters.get(DropboxConfig.KEY_PARAM), parameters.get(DropboxConfig.SECRET_PARAM));
        session.setAccessTokenPair(ac);
        client = new DropboxAPI<WebAuthSession>(session);
    }

    public Map<String, String> getRepositoryInfo() throws DropboxException {
        Map<String, String> info = new HashMap<String, String>();

        info.put("Country", client.accountInfo().country);
        info.put("Display Name", client.accountInfo().displayName);
        info.put("Referral Link", client.accountInfo().referralLink);
        info.put("Quota", String.valueOf(client.accountInfo().quota));
        info.put("Quota Normal", String.valueOf(client.accountInfo().quotaNormal));
        info.put("Quota Shared", String.valueOf(client.accountInfo().quotaShared));
        info.put("Uid", String.valueOf(client.accountInfo().uid));
        return info;
    }

    HashSet<String> getSeeds() throws DropboxException {
        HashSet<String> ids = new HashSet<String>();
        Boolean changed = false;
        while (true) {
            // Get /delta results from Dropbox
            DropboxAPI.DeltaPage<DropboxAPI.Entry> page = client.delta(cursor);

            if (page.reset) {
                changed = true;
            }
            // Apply the entries one by one.
            for (DeltaEntry<DropboxAPI.Entry> e : page.entries) {
                ids.add(e.lcPath);
                changed = true;
            }
            cursor = page.cursor;
            if (!page.hasMore) {
                break;
            }
        }
        return ids;
    }

    public DropboxAPI.Entry getObject(String id) throws DropboxException {
        DropboxAPI.Entry entry = null;
        try {
            entry = client.metadata(id, 25000, null, true, null);
        } catch (DropboxException e) {
            System.out.println("Something went wrong: " + e);
        }
        return entry;
    }

    public DropboxInputStream getDropboxInputStream(String id) throws DropboxException {
        return client.getFileStream(id, null);
    }
}
