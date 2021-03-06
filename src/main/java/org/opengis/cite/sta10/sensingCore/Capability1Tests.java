package org.opengis.cite.sta10.sensingCore;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.cite.sta10.SuiteAttribute;
import org.opengis.cite.sta10.SuiteFixtureListener;
import org.opengis.cite.sta10.util.ControlInformation;
import org.opengis.cite.sta10.util.EntityType;
import org.opengis.cite.sta10.util.Extension;
import org.opengis.cite.sta10.util.HTTPMethods;
import org.opengis.cite.sta10.util.ServiceURLBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.ISuite;
import org.testng.ITestContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Includes various tests of "A.1 Sensing Core" Conformance class.
 */
public class Capability1Tests {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Capability1Tests.class);
    /**
     * The root URL of the SensorThings service under the test
     */
    public String rootUri;//="http://localhost:8080/OGCSensorThings/v1.0";
    /**
     * The variable that defines to which recursive level the resource path
     * should be tested
     */
    private final int resourcePathLevel = 4;
    private boolean hasMultiDatastream = false;
    private boolean hasActuation = false;
    private final Set<Extension> extensions = EnumSet.noneOf(Extension.class);
    private final Set<EntityType> enabledEntityTypes = EnumSet.noneOf(EntityType.class);

    /**
     * This method will be run before starting the test for this conformance
     * class.
     *
     * @param testContext The test context to find out whether this class is
     *                    requested to test or not
     */
    @BeforeClass
    public void obtainTestSubject(ITestContext testContext) {
        ISuite suite = testContext.getSuite();
        Object obj = suite.getAttribute(SuiteAttribute.LEVEL.getName());
        if ((null != obj)) {
            Integer level = Integer.class.cast(obj);
            Assert.assertTrue(level > 0,
                    "Conformance level 1 will not be checked since ics = " + level);
        }

        rootUri = suite.getAttribute(SuiteAttribute.TEST_SUBJECT.getName()).toString();
        rootUri = rootUri.trim();
        if (rootUri.lastIndexOf('/') == rootUri.length() - 1) {
            rootUri = rootUri.substring(0, rootUri.length() - 1);
        }
        hasMultiDatastream = suite.getXmlSuite().getParameter(SuiteFixtureListener.KEY_HAS_MULTI_DATASTREAM) != null;
        hasActuation = suite.getXmlSuite().getParameter(SuiteFixtureListener.KEY_HAS_ACTUATION) != null;
        extensions.add(Extension.CORE);
        if (hasMultiDatastream) {
            extensions.add(Extension.MULTI_DATASTREAM);
        }
        if (hasActuation) {
            extensions.add(Extension.ACTUATION);
        }

        for (EntityType entityType : EntityType.values()) {
            if (!extensions.contains(entityType.getExtension())) {
                continue;
            }
            enabledEntityTypes.add(entityType);
        }

        TestEntityCreator.maybeCreateTestEntities(testContext);
    }

    /**
     * This method is testing GET entities. It should return 200. Then the
     * response entities are tested for control information, mandatory
     * properties, and mandatory related entities.
     */
    @Test(description = "GET Entities", groups = "level-1")
    public void readEntitiesAndCheckResponse() {
        for (EntityType entityType : enabledEntityTypes) {
            String response = getEntities(entityType);
            checkEntitiesAllAspectsForResponse(entityType, response);
        }
    }

    /**
     * This method is testing GET when requesting a nonexistent entity. The
     * response should be 404.
     */
    @Test(description = "GET nonexistent Entity", groups = "level-1")
    public void readNonexistentEntity() {
        for (EntityType entityType : enabledEntityTypes) {
            readNonexistentEntityWithEntityType(entityType);
        }
    }

    /**
     * This method is testing GET for a specific entity with its id. It checks
     * the control information, mandatory properties and mandatory related
     * entities for the response entity.
     */
    @Test(description = "GET Specific Entity", groups = "level-1")
    public void readEntityAndCheckResponse() {
        for (EntityType entityType : enabledEntityTypes) {
            String response = readEntityWithEntityType(entityType);
            checkEntityAllAspectsForResponse(entityType, response);
        }
    }

    /**
     * This method is testing GET for a property of an entity.
     */
    @Test(description = "GET Property of an Entity", groups = "level-1")
    public void readPropertyOfEntityAndCheckResponse() {
        for (EntityType entityType : enabledEntityTypes) {
            readPropertyOfEntityWithEntityType(entityType);
        }
    }

    /**
     * This helper method is testing property and property/$value for single
     * entity of a given entity type
     *
     * @param entityType Entity type from EntityType enum list
     */
    private void readPropertyOfEntityWithEntityType(EntityType entityType) {
        try {
            String response = getEntities(entityType);
            Object id = new JSONObject(response).getJSONArray("value").getJSONObject(0).get(ControlInformation.ID);
            for (EntityType.EntityProperty property : entityType.getProperties()) {
                checkGetPropertyOfEntity(entityType, id, property);
                checkGetPropertyValueOfEntity(entityType, id, property);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }
    }

    /**
     * This helper method sending GET request for requesting a property and
     * check the response is 200.
     *
     * @param entityType Entity type from EntityType enum list
     * @param id         The id of the entity
     * @param property   The property to get requested
     */
    private void checkGetPropertyOfEntity(EntityType entityType, Object id, EntityType.EntityProperty property) {
        try {
            Map<String, Object> responseMap = getEntity(entityType, id, property.name);
            int responseCode = Integer.parseInt(responseMap.get("response-code").toString());
            if (responseCode == 204) {
                // 204 is the proper response for NULL properties.
                return;
            }
            Assert.assertEquals(responseCode, 200, "Reading property \"" + property.name + "\" of the existing " + entityType.name() + " with id " + id + " failed.");
            String response = responseMap.get("response").toString();
            JSONObject entity = null;
            entity = new JSONObject(response);
            try {
                Assert.assertNotNull(entity.get(property.name), "Reading property \"" + property.name + "\"of \"" + entityType + "\" fails.");
            } catch (JSONException e) {
                Assert.fail("Reading property \"" + property.name + "\"of \"" + entityType + "\" fails.");
            }
            Assert.assertEquals(entity.length(), 1, "The response for getting property " + property.name + " of a " + entityType + " returns more properties!");
        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }
    }

    /**
     * This helper method sending GET request for requesting a property $value
     * and check the response is 200.
     *
     * @param entityType Entity type from EntityType enum list
     * @param id         The id of the entity
     * @param property   The property to get requested
     */
    private void checkGetPropertyValueOfEntity(EntityType entityType, Object id, EntityType.EntityProperty property) {
        Map<String, Object> responseMap = getEntity(entityType, id, property.name + "/$value");
        int responseCode = Integer.parseInt(responseMap.get("response-code").toString());
        if (responseCode != 200 && property.optional) {
            // The property is optional, and probably not present.
            return;
        }
        if (responseCode == 204) {
            // 204 is the proper response for NULL properties.
            return;
        }
        Assert.assertEquals(responseCode, 200, "Reading property value of \"" + property + "\" of the exitixting " + entityType.name() + " with id " + id + " failed.");
        String response = responseMap.get("response").toString();
        if ("object".equalsIgnoreCase(property.jsonType)) {
            Assert.assertEquals(response.indexOf("{"), 0, "Reading property value of \"" + property + "\" of \"" + entityType + "\" fails.");
        } else {
            Assert.assertEquals(response.indexOf("{"), -1, "Reading property value of \"" + property + "\" of \"" + entityType + "\" fails.");
        }
    }

    /**
     * This method is testing the resource paths based on specification to the
     * specified level.
     */
    @Test(description = "Check Resource Path", groups = "level-1")
    public void checkResourcePaths() {
        for (EntityType entityType : enabledEntityTypes) {
            readRelatedEntityOfEntityWithEntityType(entityType);
        }
    }

    /**
     * This helper method is the start point for testing resource path. It adds
     * the entity type to be tested to resource path chain and call the other
     * method to test the chain.
     *
     * @param entityType Entity type from EntityType enum list
     */
    private void readRelatedEntityOfEntityWithEntityType(EntityType entityType) {
        List<String> entityTypes = new ArrayList<>();
        List<Object> ids = new ArrayList<>();
        entityTypes.add(entityType.plural);
        readRelatedEntity(entityTypes, ids);
    }

    /**
     * This helper method is testing the chain to the specified level. It
     * confirms that the response is 200.
     *
     * @param entityTypes List of entity type from EntityType enum list for the
     *                    chain
     * @param ids         List of ids for the chain
     */
    private void readRelatedEntity(List<String> entityTypes, List<Object> ids) {
        if (entityTypes.size() > resourcePathLevel) {
            return;
        }
        String urlString = null;
        try {
            String headName = entityTypes.get(entityTypes.size() - 1);
            EntityType headEntity = EntityType.getForRelation(headName);
            boolean isPlural = EntityType.isPlural(headName);
            urlString = ServiceURLBuilder.buildURLString(rootUri, entityTypes, ids, null);
            Map<String, Object> responseMap = HTTPMethods.doGet(urlString);
            Assert.assertEquals(responseMap.get("response-code"), 200, "Reading relation of the entity failed: " + entityTypes.toString());
            String response = responseMap.get("response").toString();
            Object id;
            if (isPlural) {
                id = new JSONObject(response).getJSONArray("value").getJSONObject(0).get(ControlInformation.ID);
            } else {
                id = new JSONObject(response).get(ControlInformation.ID);
            }

            //check $ref
            urlString = ServiceURLBuilder.buildURLString(rootUri, entityTypes, ids, "$ref");
            responseMap = HTTPMethods.doGet(urlString);
            Assert.assertEquals(responseMap.get("response-code"), 200, "Reading relation of the entity failed: " + entityTypes.toString());
            response = responseMap.get("response").toString();
            checkAssociationLinks(response, entityTypes, ids);

            if (entityTypes.size() == resourcePathLevel) {
                return;
            }
            if (EntityType.isPlural(headName)) {
                ids.add(id);
            } else {
                ids.add(null);
            }
            for (String relation : headEntity.getRelations()) {
                entityTypes.add(relation);
                readRelatedEntity(entityTypes, ids);
                entityTypes.remove(entityTypes.size() - 1);
            }
            ids.remove(ids.size() - 1);
        } catch (JSONException e) {
            LOGGER.error("Failed to parse response for " + urlString, e);
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }

    }

    /**
     * This method is checking the response for the request of Association Link.
     * It confirms that it contains a list of selfLinks.
     *
     * @param response    The response for GET association link request
     * @param entityTypes List of entity type from EntityType enum list for the
     *                    chain
     * @param ids         List of ids for the chain
     */
    private void checkAssociationLinks(String response, List<String> entityTypes, List<Object> ids) {

        try {
            if (EntityType.isPlural(entityTypes.get(entityTypes.size() - 1))) {
                Assert.assertTrue(response.contains("value"), "The GET entities Association Link response does not match SensorThings API : missing \"value\" in response.: " + entityTypes.toString() + ids.toString());
                JSONArray value = new JSONObject(response).getJSONArray("value");
                int count = 0;
                for (int i = 0; i < value.length() && count < 2; i++) {
                    count++;
                    JSONObject obj = value.getJSONObject(i);
                    try {
                        Assert.assertNotNull(obj.get(ControlInformation.SELF_LINK), "The Association Link does not contain self-links.: " + entityTypes.toString() + ids.toString());
                    } catch (JSONException e) {
                        Assert.fail("The Association Link does not contain self-links.: " + entityTypes.toString() + ids.toString());
                    }
                    Assert.assertEquals(obj.length(), 1, "The Association Link contains properties other than self-link.: " + entityTypes.toString() + ids.toString());
                }
            } else {
                JSONObject obj = new JSONObject(response);
                try {
                    Assert.assertNotNull(obj.get(ControlInformation.SELF_LINK), "The Association Link does not contain self-links.: " + entityTypes.toString() + ids.toString());
                } catch (JSONException e) {
                    Assert.fail("The Association Link does not contain self-links.: " + entityTypes.toString() + ids.toString());
                }
                Assert.assertEquals(obj.length(), 1, "The Association Link contains properties other than self-link.: " + entityTypes.toString() + ids.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }
    }

    /**
     * This method is reading a specific entity and return it as a string.
     *
     * @param entityType Entity type from EntityType enum list
     * @return The entity response as a string
     */
    private String readEntityWithEntityType(EntityType entityType) {
        try {
            String response = getEntities(entityType);
            Object id = new JSONObject(response.toString()).getJSONArray("value").getJSONObject(0).get(ControlInformation.ID);
            Map<String, Object> responseMap = getEntity(entityType, id, null);
            int responseCode = Integer.parseInt(responseMap.get("response-code").toString());
            Assert.assertEquals(responseCode, 200, "Reading existing " + entityType.name() + " with id " + id + " failed.");
            response = responseMap.get("response").toString();
            return response;
        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
            return null;
        }
    }

    /**
     * This method is check the response of sending a GET request to
     * m=nonexistent entity is 404.
     *
     * @param entityType Entity type from EntityType enum list
     */
    private void readNonexistentEntityWithEntityType(EntityType entityType) {
        long id = Long.MAX_VALUE;
        int responseCode = Integer.parseInt(getEntity(entityType, id, null).get("response-code").toString());
        Assert.assertEquals(responseCode, 404, "Reading non-existing " + entityType.name() + " with id " + id + " failed.");
    }

    /**
     * This method is testing the root URL of the service under test. It
     * basically checks the first page.
     */
    @Test(description = "Check Service Root UI", groups = "level-1")
    public void checkServiceRootUri() {
        try {
            String response = getEntities(null);
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray entities = jsonResponse.getJSONArray("value");
            Map<String, Boolean> addedLinks = new HashMap<>();
            addedLinks.put("Things", false);
            addedLinks.put("Locations", false);
            addedLinks.put("HistoricalLocations", false);
            addedLinks.put("Datastreams", false);
            addedLinks.put("Sensors", false);
            addedLinks.put("Observations", false);
            addedLinks.put("ObservedProperties", false);
            addedLinks.put("FeaturesOfInterest", false);
            if (hasMultiDatastream) {
                addedLinks.put("MultiDatastreams", false);
            }
            if (hasActuation) {
                addedLinks.put("Actuators", false);
                addedLinks.put("TaskingCapabilities", false);
                addedLinks.put("Tasks", false);
            }
            for (int i = 0; i < entities.length(); i++) {
                JSONObject entity = entities.getJSONObject(i);
                if (!entity.has("name") || !entity.has("url")) {
                    Assert.fail("Service root URI component does not have proper JSON keys: name and value.");
                }
                String name = entity.getString("name");
                String nameUrl = entity.getString("url");
                addedLinks.put(name, true);
                if ("MultiDatastreams".equals(name)) {
                    // TODO: MultiDatastreams are not in the entity list yet.
                    Assert.assertEquals(nameUrl, rootUri + "/MultiDatastreams", "The URL for MultiDatastreams in Service Root URI is not compliant to SensorThings API.");
                } else {
                    try {
                        EntityType entityType = EntityType.getForRelation(name);
                        Assert.assertEquals(nameUrl, rootUri + "/" + entityType.plural, "The URL for " + entityType.plural + " in Service Root URI is not compliant to SensorThings API.");
                    } catch (IllegalArgumentException exc) {
                        Assert.fail("There is a component in Service Root URI response that is not in SensorThings API : " + name);
                    }
                }
            }
            for (String key : addedLinks.keySet()) {
                Assert.assertTrue(addedLinks.get(key), "The Service Root URI response does not contain " + key);
            }

        } catch (Exception e) {
            LOGGER.error("An Exception occurred during testing!", e);
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }
    }

    /**
     * This helper method is sending GET request to a collection of entities.
     *
     * @param entityType Entity type from EntityType enum list
     * @return The response of GET request in string format.
     */
    private String getEntities(EntityType entityType) {
        String urlString = rootUri;
        if (entityType != null) {
            urlString = ServiceURLBuilder.buildURLString(rootUri, entityType, null, null, null);
        }
        Map<String, Object> responseMap = HTTPMethods.doGet(urlString);
        String response = responseMap.get("response").toString();
        int responseCode = Integer.parseInt(responseMap.get("response-code").toString());
        Assert.assertEquals(responseCode, 200, "Error during getting entities: " + ((entityType != null) ? entityType.name() : "root URI"));
        if (entityType != null) {
            Assert.assertTrue(response.contains("value"), "The GET entities response for entity type \"" + entityType + "\" does not match SensorThings API : missing \"value\" in response.");
        } else { // GET Service Base URI
            Assert.assertTrue(response.contains("value"), "The GET entities response for service root URI does not match SensorThings API : missing \"value\" in response.");
        }
        return response;
    }

    /**
     * This helper method is sending Get request to a specific entity
     *
     * @param entityType Entity type from EntityType enum list
     * @param id         The if of the specific entity
     * @param property   The requested property of the entity
     * @return The response-code and response (body) of the request in Map
     * format.
     */
    private Map<String, Object> getEntity(EntityType entityType, Object id, String property) {
        if (id == null) {
            return null;
        }
        String urlString = ServiceURLBuilder.buildURLString(rootUri, entityType, id, null, property);
        return HTTPMethods.doGet(urlString);
    }

    /**
     * This helper method is the start point for checking the response for a
     * collection in all aspects.
     *
     * @param entityType Entity type from EntityType enum list
     * @param response   The response of the GET request to be checked
     */
    private void checkEntitiesAllAspectsForResponse(EntityType entityType, String response) {
        checkEntitiesControlInformation(response);
        checkEntitiesProperties(entityType, response);
        checkEntitiesRelations(entityType, response);
    }

    /**
     * This helper method is the start point for checking the response for a
     * specific entity in all aspects.
     *
     * @param entityType Entity type from EntityType enum list
     * @param response   The response of the GET request to be checked
     */
    private void checkEntityAllAspectsForResponse(EntityType entityType, String response) {
        checkEntityControlInformation(response);
        checkEntityProperties(entityType, response);
        checkEntityRelations(entityType, response);
    }

    /**
     * This helper method is checking the control information of the response
     * for a collection
     *
     * @param response The response of the GET request to be checked
     */
    private void checkEntitiesControlInformation(String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray entities = jsonResponse.getJSONArray("value");
            int count = 0;
            for (int i = 0; i < entities.length() && count < 2; i++) {
                count++;
                JSONObject entity = entities.getJSONObject(i);
                checkEntityControlInformation(entity);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }
    }

    /**
     * This helper method is checking the control information of the response
     * for a specific entity
     *
     * @param response The response of the GET request to be checked
     */
    private void checkEntityControlInformation(Object response) {
        try {
            JSONObject entity = new JSONObject(response.toString());
            try {
                Assert.assertNotNull(entity.get(ControlInformation.ID), "The entity does not have mandatory control information : " + ControlInformation.ID);
            } catch (JSONException e) {
                Assert.fail("The entity does not have mandatory control information : " + ControlInformation.ID);
            }
            try {
                Assert.assertNotNull(entity.get(ControlInformation.SELF_LINK), "The entity does not have mandatory control information : " + ControlInformation.SELF_LINK);
            } catch (JSONException e) {
                Assert.fail("The entity does not have mandatory control information : " + ControlInformation.SELF_LINK);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }
    }

    /**
     * This helper method is checking the mandatory properties of the response
     * for a collection
     *
     * @param entityType Entity type from EntityType enum list
     * @param response   The response of the GET request to be checked
     */
    private void checkEntitiesProperties(EntityType entityType, String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray entities = jsonResponse.getJSONArray("value");
            int count = 0;
            for (int i = 0; i < entities.length() && count < 2; i++) {
                count++;
                JSONObject entity = entities.getJSONObject(i);
                checkEntityProperties(entityType, entity);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }

    }

    /**
     * This helper method is checking the mandatory properties of the response
     * for a specific entity
     *
     * @param entityType Entity type from EntityType enum list
     * @param response   The response of the GET request to be checked
     */
    private void checkEntityProperties(EntityType entityType, Object response) {
        try {
            JSONObject entity = new JSONObject(response.toString());
            for (EntityType.EntityProperty property : entityType.getProperties()) {
                if (property.optional) {
                    continue;
                }
                try {
                    Assert.assertNotNull(entity.get(property.name), "Entity type \"" + entityType + "\" does not have mandatory property: \"" + property + "\".");
                } catch (JSONException e) {
                    Assert.fail("Entity type \"" + entityType + "\" does not have mandatory property: \"" + property + "\".");
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }

    }

    /**
     * This helper method is checking the mandatory relations of the response
     * for a collection
     *
     * @param entityType Entity type from EntityType enum list
     * @param response   The response of the GET request to be checked
     */
    private void checkEntitiesRelations(EntityType entityType, String response) {
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONArray entities = jsonResponse.getJSONArray("value");
            int count = 0;
            for (int i = 0; i < entities.length() && count < 2; i++) {
                count++;
                JSONObject entity = entities.getJSONObject(i);
                checkEntityRelations(entityType, entity);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }

    }

    /**
     * This helper method is checking the mandatory relations of the response
     * for a specific entity
     *
     * @param entityType Entity type from EntityType enum list
     * @param response   The response of the GET request to be checked
     */
    private void checkEntityRelations(EntityType entityType, Object response) {
        try {
            JSONObject entity = new JSONObject(response.toString());
            for (String relation : entityType.getRelations()) {
                try {
                    Assert.assertNotNull(entity.get(relation + ControlInformation.NAVIGATION_LINK), "Entity type \"" + entityType + "\" does not have mandatory relation: \"" + relation + "\".");
                } catch (JSONException e) {
                    Assert.fail("Entity type \"" + entityType + "\" does not have mandatory relation: \"" + relation + "\".");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Assert.fail("An Exception occurred during testing!:\n" + e.getMessage());
        }
    }

}
