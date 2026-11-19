package com.mcplusa.manifoldcf.agents.output.appsearch.tests;

import org.junit.Test;
import static org.junit.Assert.*;

import com.mcplusa.manifoldcf.agents.output.appsearch.Utils;

public class UtilsTest {

    public UtilsTest() {
    }

    /**
     * Test of cleanFieldName method, of class Utils.
     */
    @Test
    public void testCleanFieldName() {
        assertEquals("twitterog", Utils.cleanFieldName("twitter:og"));
        assertEquals("twitterimage", Utils.cleanFieldName("Twitter:Image"));
        assertEquals("twitter_description", Utils.cleanFieldName("twitter description"));
        assertEquals("fieldwithnum123", Utils.cleanFieldName("field-with-num-123"));
        assertEquals("fields", Utils.cleanFieldName("<field\"s>"));
    }

    @Test
    public void testChunkSplit() {
        String value = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
        String[] str = Utils.chunkSplit(value, 200);

        assertEquals(3, str.length);
        assertEquals(200, str[0].length());
        assertEquals(200, str[1].length());
        assertEquals(44, str[2].length());
    }

    @Test
    public void testGetValidId() {
        String url = "http://localhost:8080/mcplusa/myportal/agents/portal/quoteenroll/digs%20-%20quoting%20%20enrollment%20(individual)/!ut/p/a1/rZHLTsMwEEV_hS6yjDx5OWZpdRFImzYCAYk3lZM6D5TYSWoqPh8HFu2GQhHejEeae-aOLmIoQ0zyY1tz3SrJu7lneLfdBtTxI1iRhzsMFEfrpZ_6AFFoBnIzAN88Cj_pXxBDrJR60A3KeS2kvimV1KZaMKhJ886C8U1pIeSkOtNM3Pz5QewO3IJG9WIGDGW7RzkB7hZFIWxyyx3bL8LAJo6L7QoELitMPAH7r4WXLefmpvBkOoqfiTHth6vYTRxIAT1eufMy8D74Z2DqXg2Mf5Fz-zqOjJq05nzeNcr-FpchuVOyTGpjkOvGbmWlUHYmQtmZCGWfoqF_6omHq83G5gUBL-iOa0oXiw9FOxLu/dl5/d5/L0lJS2FZcHBpbW1LYVlwcGltbVlwcGchIS9vSHd3QUFBSXdpRUFJSkRBQ1VZaUVJVTVCZ09DbFFBQUlBQVNvU0FyUnFBQURBQWF0QXdMTzlRQUFFQUJ3WWVBR0tTQUFDa0k1Z21HU3dTaXJTQUFDZ0s5ZzBIUS80SmlHcGhxRWFoR29ScUVhbEdwaC9aNl9PTzVBMTRHMEs4Ukg2MEE2R0xDNFA0MDBHNy9hZ2VudCBjb250ZW50JTBwb3J0YWwlMHF1b3RlZW5yb2xsJTBkaWdzIC0gcXVvdGluZyAgZW5yb2xsbWVudCAoaW5kaXZpZHVhbCkvZjQ0YmEyOWUtODQwOC00YjFlLTg4MzktMTFlMjI4NDgxYTVhL2RpZ3MgLSBxdW90aW5nICBlbnJvbGxtZW50IChpbmRpdmlkdWFsKQ";
        String id = Utils.getValidId(url);

        assertEquals(780, id.length());
        assertEquals(
                "http%3A%2F%2Flocalhost%3A8080%2Fmcplusa%2Fmyportal%2Fagents%2Fportal%2Fquoteenroll%2Fdigs%2520-%2520quoting%2520%2520enrollment%2520%28individual%29%2F%21ut%2Fp%2Fa1%2FrZHLTsMwEEV_hS6yjDx5OWZpdRFImzYCAYk3lZM6D5TYSWoqPh8HFu2GQhHejEeae-aOLmIoQ0zyY1tz3SrJu7lneLfdBtTxI1iRhzsMFEfrpZ_6AFFoBnIzAN88Cj_pXxBDrJR60A3KeS2kvimV1KZaMKhJ886C8U1pIeSkOtNM3Pz5QewO3IJG9WIGDGW7RzkB7hZFIWxyyx3bL8LAJo6L7QoELitMPAH7r4WXLefmpvBkOoqfiTHth6vYTRxIAT1eufMy8D74Z2DqXg2Mf5Fz-zqOjJq05nzeNcr-FpchuVOyTGpjkOvGbmWlUHYmQtmZCGWfoqF_6omHq83G5gUBL-iOa0oXiw9FOxLu%2Fdl5%2Fd5%2FL0lJS2FZcHBpbW1LYVlwcGltbVlwcGchIS9vSHd3QUFBSXdpRUFJSkRBQ1VZaUVJVTVCZ09DbFFBQUlBQVNvU0FyUnFBQURBQWF0QXdMTzlRQUFFQUJ3WWVBR0tTQUFDa0k1Z21HU3dTaXJTQUFDZ0s5ZzBIUS80SmlHcGhxRWFoR29ScUVhbEdwaC9aNl9PTzVBMTRHMEs4Ukg2MEE2R0xDNFA0MDBHNy9hZ2VudCBjb2",
                id);
    }

    @Test
    public void testProcessExpression() {
        String url = "http://localhost:8080/mcplusa/myportal/agents/portal/quoteenroll/digs%20-%20quoting%20%20enrollment%20(individual)/!ut/p/a1/rZHLTsMwEEV_hS";

        // Single group
        String pattern = "http://localhost:8080/(.*)/!ut.*";
        String replacementstring = "http://my-custom-domain.com/$(1)";
        String data = Utils.processExpression(pattern, replacementstring, url);
        assertEquals("http://my-custom-domain.com/mcplusa/myportal/agents/portal/quoteenroll/digs%20-%20quoting%20%20enrollment%20(individual)", data);

        // Multiple groups
        String patternGroups = "http://(.*):8080/(.*)/!ut.*";
        String replacementstringGroups = "http://$(1).com/$(1)/$(2)";
        String dataGroups = Utils.processExpression(patternGroups, replacementstringGroups, url);
        assertEquals("http://localhost.com/localhost/mcplusa/myportal/agents/portal/quoteenroll/digs%20-%20quoting%20%20enrollment%20(individual)", dataGroups);
    }
}
