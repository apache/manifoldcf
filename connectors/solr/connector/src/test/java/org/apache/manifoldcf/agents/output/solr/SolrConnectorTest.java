
package org.apache.manifoldcf.agents.output.solr;

import junit.framework.TestCase;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;

/**
 * @Author: Alessandro Benedetti
 * Date: 18/12/2013
 */
public class SolrConnectorTest extends TestCase{

    SolrConnector connectorToTest;
    HttpPoster poster;
    RepositoryDocument document;
    IOutputAddActivity act;

    @Override
    public void setUp() throws Exception {
        connectorToTest=spy(new SolrConnector());
        poster=mock(HttpPoster.class);
        document=mock(RepositoryDocument.class);
        act=mock(IOutputAddActivity.class);
        when(poster.indexPost(anyString(),eq(document),anyMap(),anyMap(),anyBoolean(),anyString(),eq(act))).thenReturn(true);

    }

    /**
     * Test the AddOrReplaceDocument with an example test string in input
     * @throws ManifoldCFException
     * @throws ServiceInterruption
     */
    public void testAddOrReplaceDocument() throws ManifoldCFException, ServiceInterruption {
        Map<String, List<String>> expectedSourceTargets = getMappingsMap();
        Map<String,List<String>> expectedStreamParams = getStreamTypeMap();

        connectorToTest.poster=poster;
        String outputDescription = "1+stream.type=text/plain=+3+cm:description=cm_description_s=+cm:name=cm_name_s=+cm:title=cm_title_s=+1+keepAllMetadata=true=+";
        connectorToTest.addOrReplaceDocument("Document Id", outputDescription,document,"",act);
        verify(poster).indexPost(eq("Document Id"),eq(document),eq(expectedStreamParams),eq(expectedSourceTargets),eq(true),eq(""),eq(act));
    }

    /**
     * returns the expected mappings map for the input string in test
     * @return
     */
    private Map<String, List<String>> getMappingsMap() {
        Map<String, List<String>> sourceTargets = new HashMap<String, List<String>>();
        List<String> firstList=new ArrayList<String>();
        firstList.add("cm_description_s");
        List<String> secondList=new ArrayList<String>();
        secondList.add("cm_name_s");
        List<String> thirdList=new ArrayList<String>();
        thirdList.add("cm_title_s");

        sourceTargets.put("cm:description",firstList);
        sourceTargets.put("cm:name",secondList);
        sourceTargets.put("cm:title",thirdList);
        return sourceTargets;
    }

    /**
     * returns the expected mappings map for the input string in test
     * @return
     */
    private Map<String, List<String>> getStreamTypeMap() {
        Map<String, List<String>> sourceTargets = new HashMap<String, List<String>>();
        List<String> firstList=new ArrayList<String>();
        firstList.add("text/plain");
        sourceTargets.put("stream.type",firstList);
        return sourceTargets;
    }
}
