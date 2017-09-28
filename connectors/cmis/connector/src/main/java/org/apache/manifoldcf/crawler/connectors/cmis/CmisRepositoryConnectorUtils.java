/* $Id$ */

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.manifoldcf.crawler.connectors.cmis;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.bindings.spi.atompub.AbstractAtomPubService;
import org.apache.chemistry.opencmis.client.bindings.spi.atompub.AtomPubParser;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.enums.Cardinality;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.system.Logging;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 *
 * @author Piergiorgio Lucidi
 *
 */
public class CmisRepositoryConnectorUtils {

    private static final String LOAD_LINK_METHOD_NAME = "loadLink";
    private static final String FROM_TOKEN = "from";
    private static final String SEP = " ";
    private static final String SELECT_STAR_CLAUSE = "select *";
    private static final String OBJECT_ID_TERM = PropertyIds.OBJECT_ID + ",";
    private static final String SELECT_CLAUSE_TERM_SEP = ",";
    private static final String SELECT_PREFIX = "select ";
    private final static String TOKENIZER_SEP = ",\n\t";
    public static final String SLASH = "/";

    public static final String getDocumentURL(final Document document, final Session session)
            throws ManifoldCFException {
            	
    		String link = null;
        try {
            Method loadLink = AbstractAtomPubService.class.getDeclaredMethod(LOAD_LINK_METHOD_NAME,
                    new Class[]{String.class, String.class, String.class, String.class});

            loadLink.setAccessible(true);

            link = (String) loadLink.invoke(session.getBinding().getObjectService(), session.getRepositoryInfo().getId(),
                    document.getId(), AtomPubParser.LINK_REL_CONTENT, null);
        } catch (Exception e) {
            Logging.connectors.error(
                    "CMIS: Error during getting the content stream url: "
                    + e.getMessage(), e);
            throw new ManifoldCFException(e.getMessage(), e);
        }

        return link;
    }

    /**
     * Utility method to consider the objectId whenever it is not present in the select clause
     * @param cmisQuery
     * @return the cmisQuery with the cmis:objectId property added in the select clause
     */
    public static String getCmisQueryWithObjectId(String cmisQuery) {
        String cmisQueryResult = StringUtils.EMPTY;
        String selectClause = getSelectClause(cmisQuery);
        if (selectClause.equalsIgnoreCase(SELECT_STAR_CLAUSE)) {
            cmisQueryResult = cmisQuery;
        } else {
            //get the select term and add the cmis:objectId term or prefix.cmis:objectId
            StringTokenizer selectClauseTokenized = new StringTokenizer(selectClause.trim());
            boolean firstTermSelectClause = true;
            String selectTerm = StringUtils.EMPTY;
            String prefix = StringUtils.EMPTY;
            boolean foundObjIdClause = false;
            boolean foundPrefixClause = false;
            while (selectClauseTokenized.hasMoreElements()) {
                String term = selectClauseTokenized.nextToken();
                if (firstTermSelectClause) {
                    selectTerm = term;
                    firstTermSelectClause = false;
                } else {
                    if (term.contains(PropertyIds.OBJECT_ID)){
                        foundObjIdClause = true;
                        cmisQueryResult = cmisQuery;
                        break;
                    }
                    //if a term use a prefix table, get the prefix
                    if (!foundPrefixClause && term.contains(".")){
                        int i = term.indexOf(".");
                        prefix = term.substring(0,i);
                        foundPrefixClause = true;
                    }
                }
            }
            // if the cmis:objectId term is not found, add it
            if (!foundObjIdClause) {
                String toReplace = selectTerm + " ";
                if (foundPrefixClause) {
                    toReplace += prefix + "." + OBJECT_ID_TERM;
                } else {
                    toReplace += OBJECT_ID_TERM;
                }
                cmisQueryResult = StringUtils.replaceOnce(cmisQuery, selectTerm, toReplace);
            }
        }
        return cmisQueryResult;
    }

  /**
   * Utility method to understand if a property must be indexed or not
   * @param cmisQuery
   * @param propertyId
   * @return TRUE if the property is included in the select clause of the query, otherwise it will return FALSE
   */
  public static boolean existsInSelectClause(String cmisQuery, String propertyId) {
        String selectClause = getSelectClause(cmisQuery);
        if (selectClause.toLowerCase(Locale.ROOT).startsWith(SELECT_STAR_CLAUSE)) {
            return true;
        } else {
            StringTokenizer cmisQueryTokenized = new StringTokenizer(cmisQuery.trim());
            while (cmisQueryTokenized.hasMoreElements()) {
                String term = cmisQueryTokenized.nextToken();
                if (!term.equalsIgnoreCase(FROM_TOKEN)) {
                    if (term.equalsIgnoreCase(propertyId)) {
                        return true;
                    } else if (StringUtils.contains(term, SELECT_CLAUSE_TERM_SEP)) {
                        //in this case means that we have: select cmis:objectId,cmis:name from ...
                        StringTokenizer termsTokenized = new StringTokenizer(term, SELECT_CLAUSE_TERM_SEP);
                        while (termsTokenized.hasMoreElements()) {
                            String termTokenized = termsTokenized.nextToken().trim();
                            if (termTokenized.equalsIgnoreCase(propertyId)) {
                                return true;
                            }
                        }
                    }
                } else {
                    break;
                }
            }
            return false;
        }
    }

    /**
     * @param props : list properties of a document
     * @param rd : object that contains the properties to pass to connector
     * @param cmisQuery : cmis query
     */
    public static void addValuesOfProperties(Document document, RepositoryDocument rd, String cmisQuery) {
    		if(document.getPaths() != null) {
    			List<String> sourcePath = document.getPaths();
    			rd.setSourcePath(sourcePath);
    		}
    		
    		List<Property<?>> props = document.getProperties();
        Map<String, String> cmisQueryColumns = CmisRepositoryConnectorUtils.getSelectMap(cmisQuery);
        boolean isWildcardQuery = CmisRepositoryConnectorUtils.isWildcardQuery(cmisQuery);
        addValuesOfProperty(props, isWildcardQuery, cmisQueryColumns, rd);
    }

    /**
     * @param props : list properties of a document
     * @param isWildcardQuery : if the query select is of type '*'
     * @param cmisQueryColumns : selectors query
     * @param rd : object that contains the properties to pass to connector
     */
    public static void addValuesOfProperty(final List<Property<?>> props, final boolean isWildcardQuery, final Map<String, String> cmisQueryColumns, RepositoryDocument rd) {

        for (Property<?> property : props) {
            String propertyId = property.getId();
            if (isWildcardQuery || cmisQueryColumns.containsKey(propertyId)) {
                try {
                    addPropertyValue(property, rd);
                } catch (Exception e) {
                    Logging.connectors.error("Error when adding property[" + propertyId + "] msg=[" + e.getMessage() + "]", e);
                }

            }
        }
    }

    /**
     * @param property : the property
     * @param propertyDefinitionType : definition of the type of property
     * @param rd : object to which we add the association property -> value
     * @throws Exception
     *
     */
    private static void addPropertyValue(Property<?> property, RepositoryDocument rd) throws Exception {

        DateTimeFormatter format = ISODateTimeFormat.dateTime();
        PropertyDefinition<?> propertyDefinitionType = property.getDefinition();
        PropertyType propertyType = propertyDefinitionType.getPropertyType();
        boolean isMultiValue = (propertyDefinitionType.getCardinality() == Cardinality.MULTI);
        String currentProperty = property.getId();

        switch (propertyType) {

            case STRING:
            case ID:
            case URI:
            case HTML:
                List<String> listValues = (List<String>) property.getValues();
                if (!CollectionUtils.isEmpty(listValues)) {
                    if (isMultiValue) {
                        for (String htmlPropertyValue : listValues) {
                            rd.addField(currentProperty, htmlPropertyValue);
                        }
                    } else {
                        String stringValue = (String) listValues.get(0);
                        if (StringUtils.isNotEmpty(stringValue)) {
                            rd.addField(currentProperty, stringValue);
                        }
                    }
                }
                break;

            case BOOLEAN:
                List<Boolean> booleanPropertyValues = (List<Boolean>) property.getValues();
                if (!CollectionUtils.isEmpty(booleanPropertyValues)) {
                    if (isMultiValue) {
                        for (Boolean booleanPropertyValue : booleanPropertyValues) {
                            rd.addField(currentProperty, booleanPropertyValue.toString());
                        }
                    } else {
                        Boolean booleanValue = (Boolean) booleanPropertyValues.get(0);
                        if (booleanValue != null) {
                            rd.addField(currentProperty, booleanValue.toString());
                        }
                    }
                }
                break;

            case INTEGER:
                List<BigInteger> integerPropertyValues = (List<BigInteger>) property.getValues();
                if (!CollectionUtils.isEmpty(integerPropertyValues)) {
                    if (isMultiValue) {
                        for (BigInteger integerPropertyValue : integerPropertyValues) {
                            rd.addField(currentProperty, integerPropertyValue.toString());
                        }
                    } else {
                        BigInteger integerValue = (BigInteger) integerPropertyValues.get(0);
                        if (integerValue != null) {
                            rd.addField(currentProperty, integerValue.toString());
                        }
                    }
                }
                break;

            case DECIMAL:
                List<BigDecimal> decimalPropertyValues = (List<BigDecimal>) property.getValues();
                if (!CollectionUtils.isEmpty(decimalPropertyValues)) {
                    if (isMultiValue) {
                        for (BigDecimal decimalPropertyValue : decimalPropertyValues) {
                            rd.addField(currentProperty, decimalPropertyValue.toString());
                        }
                    } else {
                        BigDecimal decimalValue = (BigDecimal) decimalPropertyValues.get(0);
                        if (decimalValue != null) {
                            rd.addField(currentProperty, decimalValue.toString());
                        }
                    }
                }
                break;

            case DATETIME:
                List<GregorianCalendar> datePropertyValues = (List<GregorianCalendar>) property.getValues();
                if (!CollectionUtils.isEmpty(datePropertyValues)) {
                    if (isMultiValue) {
                        for (GregorianCalendar datePropertyValue : datePropertyValues) {
                            rd.addField(currentProperty, format.print(datePropertyValue.getTimeInMillis()));
                        }
                    } else {
                        GregorianCalendar dateValue = (GregorianCalendar) datePropertyValues.get(0);
                        if (dateValue != null) {
                            rd.addField(currentProperty, format.print(dateValue.getTimeInMillis()));
                        }
                    }
                }
                break;

            default:
                break;
        }
    }

    private static String getSelectClause(String cmisQuery) {
        StringTokenizer cmisQueryTokenized = new StringTokenizer(cmisQuery.trim());
        String selectClause = StringUtils.EMPTY;
        boolean firstTerm = true;
        while (cmisQueryTokenized.hasMoreElements()) {
            String term = cmisQueryTokenized.nextToken();
            if (!term.equalsIgnoreCase(FROM_TOKEN)) {
                if (firstTerm) {
                    selectClause += term;
                    firstTerm = false;
                } else {
                    selectClause += SEP + term;
                }

            } else {
                break;
            }
        }
        return selectClause;
    }


    //create a map with the field term and the alias (if present)
    public static Map<String, String> getSelectMap(String cmisQuery) {
        Map<String, String> cmisQueryColumns = new HashMap<>();
        String selectClause = getSelectClause(cmisQuery.trim());

        StringTokenizer cmisQueryTokenized = new StringTokenizer(selectClause.substring(SELECT_PREFIX.length()), TOKENIZER_SEP);
        while (cmisQueryTokenized.hasMoreElements()) {
            String term = cmisQueryTokenized.nextToken();
            ColumnSet column = getColumnName(term);
            cmisQueryColumns.put(column.getName(), column.getAlias());
        }
        return cmisQueryColumns;
    }

    //get a columset object given a term of the select clause
    private static ColumnSet getColumnName(String orig) {
        final String sep = " as ";
        final int sepLen = sep.length();
        String justColumnName = null;
        String alias = null;

        if (orig == null) {
            return null;
        }

        justColumnName = orig.trim();

        int idx = orig.indexOf(sep);
        if (idx < 0) {
            idx = orig.indexOf(sep.toUpperCase(Locale.ROOT));
        }

        if (idx > 1) {
            alias = orig.substring(idx + sepLen).trim();
            justColumnName = orig.substring(0, idx).trim();
        }

        // Now we identify the column name and the prefix as alias if it's null,
        idx = justColumnName.indexOf(".");
        if(idx > 0){
            justColumnName = justColumnName.substring(idx + 1);
        }

        if (alias == null) {
            alias = justColumnName;
        }

        return new ColumnSet(justColumnName, alias);
    }

    //check if the query is a select *
    public static boolean isWildcardQuery(String selectClause) {
        return selectClause.toLowerCase(Locale.ROOT).startsWith(SELECT_STAR_CLAUSE);
    }
}