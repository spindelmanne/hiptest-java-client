package com.smartbear.hiptest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.methods.HttpGet;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Class that creates new scenarios in Hiptest.
 */
public class HiptestPublisher {

    private static final String BASE_URL = "https://app.hiptest.com/api/projects/%d";
    private static final String ATTRIBUTES = "attributes";

    private final String clientId;
    private final String accessToken;
    private final String userId;
    private final int projectId;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClientBuilder.create().build();

    public HiptestPublisher(String clientId, String accessToken, String userId, int projectId) {
        this.clientId = clientId;
        this.accessToken = accessToken;
        this.userId = userId;
        this.projectId = projectId;
    }

    public void publishGherkin(Reader gherkinInput) throws IOException {
        List<String> featureFileLines = IOUtils.readLines(gherkinInput);
        String currentScenarioName = null;
        List<String> steps = new ArrayList<>();
        for (String line : featureFileLines) {
            String trimmed = line.trim();
            if (currentScenarioName == null) {
                if (trimmed.startsWith("Scenario:")) {
                    currentScenarioName = trimmed.substring("Scenario:".length());
                }
                continue;
            }
            if (trimmed.isEmpty()) {
                createScenario(currentScenarioName, steps);
                currentScenarioName = null;
                steps.clear();
            } else {
                steps.add(trimmed);
            }
        }
        if (currentScenarioName != null) {
            createScenario(currentScenarioName, steps);
        }
    }

    public List<Map<String,Object>> getAllScenarios() throws Exception {
        String uri = getScenariosBasePath();
        HttpGet httpGet = new HttpGet(uri);
        addHeaders(httpGet);
        return (List<Map<String, Object>>) getJsonResponse(httpGet).get("data");
    }

    public Map createScenario(String name, List<String> steps) throws IOException {
        createActionWordsFrom(steps);
        String scenariosBasePath = getScenariosBasePath();
        Map scenario = createPayload("scenarios", mapOf("name", name));
        Map jsonResponse = postData(scenariosBasePath, scenario);
        String id = (String)((Map)jsonResponse.get("data")).get("id");
        return addSteps(name, id, steps);
    }

    private Map postData(String uri, Map payload) throws IOException {
        HttpPost post = new HttpPost(uri);
        addHeaders(post);
        post.setEntity(new StringEntity(mapper.writeValueAsString(payload)));
        return getJsonResponse(post);
    }

    public Map createTestRun(String name) throws IOException {
        Map<String, Object> testRun = createPayload("testruns", mapOf("name", name));
        return postData(getBaseUrl() + "/test_runs", testRun);
    }

    private void createActionWordsFrom(List<String> steps) throws IOException {
        HttpGet getActionWords = new HttpGet(getBaseUrl() + "/actionwords");
        addHeaders(getActionWords);
        List<Map> actionWordItems = (List<Map>)getJsonResponse(getActionWords).get("data");
        Set<String> existing = actionWordItems.stream().map(this::getActionWordName).collect(toSet());
        List<String> actionWords = new ArrayList<>(steps.stream().map(this::secondColumn).collect(toList()));
        actionWords.removeAll(existing);
        addActionWords(actionWords);
    }

    private String getBaseUrl() {
        return String.format(BASE_URL, projectId);
    }

    private String getActionWordName(Map map) {
        return (String)((Map)map.get(ATTRIBUTES)).get("name");
    }

    private String secondColumn(String s) {
        return splitOnFirstSpace(s)[1];
    }

    public void addActionWords(Collection<String> actionWords) throws IOException {
        String uri = getBaseUrl() + "/actionwords";
        for (String actionWord : actionWords) {
            HttpPost post = new HttpPost(uri);
            Map<String,String> attributes = new HashMap<>();
            attributes.put("type", "actionwords");
            attributes.put("name", actionWord);
            Map<String, Object> data = new HashMap<>();
            data.put(ATTRIBUTES, attributes);
            Map<String, Object> payload = new HashMap<>();
            payload.put("data", data);
            String string = mapper.writeValueAsString(payload);
            System.out.println(string);
            post.setEntity(new StringEntity(string));
            addHeaders(post);
            getJsonResponse(post);
        }
    }

    private Map addSteps(String scenarioName, String scenarioId, List<String> steps) throws IOException {
        String stepsString = buildStepsString(scenarioName, steps);
        Map scenarioWithSteps = createPayload("scenarios", mapOf("definition", stepsString));
        ((Map<String,Object>)scenarioWithSteps.get("data")).put("id", scenarioId);
        HttpPatch patch = new HttpPatch(getScenariosBasePath() + "/" + scenarioId);
        String json = mapper.writeValueAsString(scenarioWithSteps);
        System.out.println(json);
        patch.setEntity(new StringEntity(json));
        addHeaders(patch);
        return getJsonResponse(patch);
    }

    private String buildStepsString(String scenarioName, List<String> steps) {
        StringBuilder builder = new StringBuilder();
        builder.append("scenario '").append(scenarioName).append("' do\n");
        for (String step : steps) {
            builder.append("  call ");
            String[] typeAndAction = splitOnFirstSpace(step);
            builder.append(typeAndAction[0].toLowerCase()).append(" ");
            builder.append("'").append(typeAndAction[1]).append("'\n");
        }
        builder.append("end\n");
        return builder.toString();
    }

    private String[] splitOnFirstSpace(String step) {
        int spaceIndex = step.indexOf(' ');
        return new String[] { step.substring(0, spaceIndex).trim(), step.substring(spaceIndex + 1).trim()};
    }

    private Map createPayload(String itemType, Map<String, String> attributes) {
        Map<String,Object> scenario = new HashMap<>();
        Map<String,Object> data = new HashMap<>();
        data.put("type", itemType);
        data.put(ATTRIBUTES, attributes);
        scenario.put("data", data);
        return scenario;
    }

    private String getScenariosBasePath() {
        return getBaseUrl() + "/scenarios";
    }

    private Map getJsonResponse(HttpRequestBase httpGet) throws IOException {
        org.apache.http.HttpResponse response = httpClient.execute(httpGet);
        ResponseHandler<String> responseHandler = new BasicResponseHandler();
        String body = responseHandler.handleResponse(response);
        return mapper.readValue(body, Map.class);
    }

    private void addHeaders(HttpRequestBase httpGet) {
        httpGet.setHeader("Accept", "application/vnd.api+json; version=1");
        httpGet.setHeader("client", clientId);
        httpGet.setHeader("access-token", accessToken);
        httpGet.setHeader("uid", userId);
    }

    private Map<String, String> mapOf(String key, String value) {
        Map<String, String> returnValue = new HashMap<>();
        returnValue.put(key, value);
        return returnValue;
    }

}
