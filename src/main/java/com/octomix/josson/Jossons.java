/*
 * Copyright 2020 Octomix Software Technology Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.octomix.josson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.octomix.josson.commons.StringUtils;
import com.octomix.josson.exception.NoValuePresentException;
import com.octomix.josson.exception.UnresolvedDatasetException;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.octomix.josson.JossonCore.*;
import static com.octomix.josson.PatternMatcher.*;
import static com.octomix.josson.commons.StringEscapeUtils.unescapeXml;

public class Jossons {

    private final Map<String, Josson> datasets = new HashMap<>();

    private enum JoinOperator {
        INNER_JOIN_ONE(">=<"),
        LEFT_JOIN_ONE("<=<"),
        RIGHT_JOIN_ONE(">=>"),
        LEFT_JOIN_MANY("<=<<"),
        RIGHT_JOIN_MANY(">>=>");

        final String symbol;

        JoinOperator(String symbol) {
            this.symbol = symbol;
        }

        private static JoinOperator fromSymbol(String symbol) {
            for (JoinOperator operator : values()) {
                if (operator.symbol.equals(symbol)) {
                    return operator;
                }
            }
            return null;
        }
    }

    /**
     * <p>Create a Jossons object with given Jackson ObjectNode.
     * Each element of the ObjectNode will become a member of the default dataset mapping.</p>
     *
     * @param datasets the default datasets
     * @return The new Jossons object
     * @throws IllegalArgumentException if {@code datasets} is not a Jackson ObjectNode
     */
    public static Jossons create(JsonNode datasets) {
        if (datasets != null && datasets.getNodeType() != JsonNodeType.OBJECT) {
            throw new IllegalArgumentException("Argument is not an object node");
        }
        Jossons jossons = new Jossons();
        if (datasets != null) {
            datasets.fields().forEachRemaining(entry ->
                    jossons.datasets.put(entry.getKey(), Josson.create(entry.getValue())));
        }
        return jossons;
    }

    /**
     * <p>Create a Jossons object with given JSON content string that deserialized to a Jackson ObjectNode.
     * Each element of the ObjectNode will become a member of the default dataset mapping.</p>
     *
     * @param json the string content for building the JSON tree
     * @return The new Jossons object
     * @throws JsonProcessingException if underlying input contains invalid content
     */
    public static Jossons fromJsonString(String json) throws JsonProcessingException {
        return create(Josson.readJsonNode(json));
    }

    /**
     * <p>Create a Jossons object with given text-based dataset mapping.
     * Each element of the mapping will become a member of the default dataset mapping.</p>
     *
     * @param mapping the text-based dataset mapping
     * @return The new Jossons object
     */
    public static Jossons fromMap(Map<String, String> mapping) {
        Jossons jossons = new Jossons();
        if (mapping != null) {
            mapping.forEach((key, value) ->
                    jossons.datasets.put(key, Josson.create(TextNode.valueOf(value))));
        }
        return jossons;
    }

    /**
     * <p>Create a Jossons object with given integer-based dataset mapping.
     * Each element of the mapping will become a member of the default dataset mapping.</p>
     *
     * @param mapping the integer-based dataset mapping
     * @return The new Jossons object
     */
    public static Jossons fromMapOfInt(Map<String, Integer> mapping) {
        Jossons jossons = new Jossons();
        if (mapping != null) {
            mapping.forEach((key, value) ->
                    jossons.datasets.put(key, Josson.create(IntNode.valueOf(value))));
        }
        return jossons;
    }

    /**
     * Put dataset to the dataset mapping.
     *
     * @param key the mapping key
     * @param value the mapping Josson object as the data value
     * @return {@code this}
     */
    public Jossons putDataset(String key, Josson value) {
        datasets.put(key, value);
        return this;
    }

    public Map<String, Josson> getDatasets() {
        return datasets;
    }

    private boolean buildDataset(String name, String query, Function<String, String> dictionaryFinder,
                                 BiFunction<String, String, Josson> dataFinder, ResolverProgress progress) {
        Josson dataset = null;
        String[] tokens = matchDbQuery(query);
        if (tokens != null) {
            progress.addResolvingFrom(name, query);
            String collectionName = (tokens[0].isEmpty() ? name : tokens[0]) + tokens[1];
            dataset = dataFinder.apply(collectionName, tokens[2]);
        } else {
            try {
                dataset = joinDatasets(name, query, dictionaryFinder, dataFinder, progress);
                if (dataset == null) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                progress.addStep("Join operation failed - " + e.getMessage());
            }
        }
        progress.addResolvedDataset(name, dataset);
        putDataset(name, dataset);
        return true;
    }

    private Josson joinDatasets(String name, String query, Function<String, String> dictionaryFinder,
                                BiFunction<String, String, Josson> dataFinder, ResolverProgress progress) {
        List<String[]> conditions = decomposeConditions(query);
        if (conditions.size() < 2) {
            return null;
        }
        String[] leftQuery = matchJoinOperation(conditions.get(0)[1]);
        if (leftQuery == null) {
            return null;
        }
        JoinOperator operator = JoinOperator.fromSymbol(conditions.get(1)[0]);
        if (operator == null) {
            return null;
        }
        progress.addResolvingFrom(name, query);
        if (conditions.size() > 2) {
            throw new IllegalArgumentException("too many arguments");
        }
        String[] rightQuery = matchJoinOperation(conditions.get(1)[1]);
        String[] leftKeys = leftQuery[1].split(",");
        String[] rightKeys = rightQuery == null ? null : rightQuery[1].split(",");
        if (anyIsBlank(leftKeys) || anyIsBlank(rightKeys)) {
            throw new IllegalArgumentException("missing join key");
        }
        if (leftKeys.length != rightKeys.length) {
            throw new IllegalArgumentException("mismatch key count");
        }
        JsonNode leftNode = evaluateQueryWithResolverLoop(leftQuery[0], dictionaryFinder, dataFinder, progress);
        if (leftNode == null) {
            throw new IllegalArgumentException("unresolvable left side");
        }
        if (!leftNode.isContainerNode()) {
            throw new IllegalArgumentException("left side is not a container node");
        }
        JsonNode rightNode = evaluateQueryWithResolverLoop(rightQuery[0], dictionaryFinder, dataFinder, progress);
        if (rightNode == null) {
            throw new IllegalArgumentException("unresolvable right side");
        }
        if (!rightNode.isContainerNode()) {
            throw new IllegalArgumentException("right side is not a container node");
        }
        String leftArrayName = null;
        String rightArrayName = null;
        int pos = leftKeys[0].indexOf(':');
        if (pos >= 0) {
            leftArrayName = leftKeys[0].substring(0, pos).trim();
            leftKeys[0] = leftKeys[0].substring(pos + 1);
        }
        pos = rightKeys[0].indexOf(':');
        if (pos >= 0) {
            rightArrayName = rightKeys[0].substring(0, pos).trim();
            rightKeys[0] = rightKeys[0].substring(pos + 1);
        }
        switch (operator) {
            case LEFT_JOIN_MANY:
                if (rightArrayName == null) {
                    rightArrayName = getLastElementName(rightQuery[0]);
                } else {
                    checkElementName(rightArrayName);
                }
                break;
            case RIGHT_JOIN_MANY:
                if (leftArrayName == null) {
                    leftArrayName = getLastElementName(leftQuery[0]);
                } else {
                    checkElementName(leftArrayName);
                }
                break;
        }
        JsonNode joinedNode = joinNodes(leftNode, leftKeys, leftArrayName, operator, rightNode, rightKeys, rightArrayName);
        if (joinedNode == null) {
            throw new IllegalArgumentException("invalid data");
        }
        return Josson.create(joinedNode);
    }

    /**
     * <p>XML template version of {@code fillInPlaceholder()}.
     * Unescapes placeholder before the resolution process of that placeholder.
     * Useful to merge docx template document.</p>
     *
     * @param template the XML template to be executed
     * @return The merged text content
     * @throws NoValuePresentException if any placeholder was unable to resolve
     * @see #fillInPlaceholder
     */
    public String fillInXmlPlaceholder(String template) throws NoValuePresentException {
        if (StringUtils.isBlank(template)) {
            return template;
        }
        return fillInPlaceholderLoop(template, true);
    }

    /**
     * <p>Uses the stored dataset mapping to merge and fill all placeholders in a template.
     * Placeholder is start with "{{" and end with "}}" as the quote marks.</p>
     * <p>Any unresolvable placeholder will raise {@code NoValuePresentException} with the incomplete merged text content.
     * All unresolvable placeholders are quoted with "**" to replace the original "{{" and "}}".</p>
     *
     * @param template the template to be executed
     * @return The merged text content
     * @throws NoValuePresentException if any placeholder was unable to resolve
     */
    public String fillInPlaceholder(String template) throws NoValuePresentException {
        if (StringUtils.isBlank(template)) {
            return template;
        }
        return fillInPlaceholderLoop(template, false);
    }

    private String fillInPlaceholderLoop(String template, boolean isXml) throws NoValuePresentException {
        Set<String> unresolvedDatasets = new HashSet<>();
        Set<String> unresolvedPlaceholders = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        int last = template.length() - 1;
        int offset = 0;
        int placeholderAt = -1;
        boolean textAdded = false;
        for (int i = 0; i < last; i++) {
            if (template.charAt(i) == '{') {
                if (template.charAt(i + 1) == '{') {
                    i++;
                    while (template.charAt(i + 1) == '{' && i < last) {
                        i++;
                    }
                    placeholderAt = i - 1;
                    sb.append(template, offset, placeholderAt);
                    offset = placeholderAt;
                }
            } else if (placeholderAt >= 0 && template.charAt(i) == '}' && template.charAt(i + 1) == '}') {
                String query = template.substring(placeholderAt + 2, i);
                if (isXml) {
                    StringBuilder rebuild = new StringBuilder();
                    for (String token : separateXmlTags(query)) {
                        if (token.charAt(0) == '<') {
                            sb.append(token);
                        } else {
                            rebuild.append(token);
                        }
                    }
                    query = unescapeXml(rebuild.toString());
                }
                try {
                    query = StringUtils.strip(query);
                    JsonNode node = evaluateQuery(query);
                    if (node != null && node.isValueNode()) {
                        sb.append(node.asText());
                        textAdded = true; // Remember even if it is an empty string
                    } else if (node != null && node.isArray()) {
                        sb.append(node);
                    } else {
                        unresolvedPlaceholders.add(query);
                        putDataset(query, null);
                        sb.append("**").append(query).append("**");
                    }
                } catch (UnresolvedDatasetException e) {
                    unresolvedDatasets.add(e.getDatasetName());
                    sb.append("{{").append(query).append("}}");
                }
                placeholderAt = -1;
                offset = ++i + 1;
            }
        }
        if (sb.length() == 0 && !textAdded) {
            return template;
        }
        if (placeholderAt >= 0) {
            unresolvedPlaceholders.add("Lack of closing tag: " +
                    StringUtils.abbreviate(template.substring(placeholderAt), 0, 20));
            sb.append("**").append(template, placeholderAt + 2, template.length());
        } else {
            sb.append(template, offset, template.length());
        }
        template = sb.toString();
        if (!unresolvedDatasets.isEmpty() || !unresolvedPlaceholders.isEmpty()) {
            throw new NoValuePresentException(unresolvedDatasets, unresolvedPlaceholders, template);
        }
        return fillInPlaceholderLoop(template, isXml);
    }

    /**
     * <p>XML template version of {@code fillInPlaceholderWithResolver()}.
     * Unescapes placeholder before the resolution process of that placeholder.
     * Useful to merge docx template document.</p>
     *
     * @param template the template to be executed
     * @param dictionaryFinder a callback function to return solution query statement
     * @param dataFinder a callback function to process database query statement
     * @param progress a {@code ResolverProgress} object to log the resolver progress steps
     * @return The merged text content
     * @throws NoValuePresentException if any placeholder was unable to resolve
     * @see #fillInPlaceholderWithResolver
     */
    public String fillInXmlPlaceholderWithResolver(String template, Function<String, String> dictionaryFinder,
                                                   BiFunction<String, String, Josson> dataFinder,
                                                   ResolverProgress progress) throws NoValuePresentException {
        if (StringUtils.isBlank(template)) {
            return template;
        }
        try {
            return fillInPlaceholderWithResolver(template, dictionaryFinder, dataFinder, true, progress);
        } finally {
            if (progress.isAutoMarkEnd()) {
                progress.markEnd();
            }
        }
    }

    /**
     * <p>Uses the stored dataset mapping and with the help of on demand callback dataset resolver
     * to merge and fill all placeholders in a template.
     * Placeholder is start with "{{" and end with "}}" as the quote marks.</p>
     * <p>An unresolved dataset will trigger {@code dictionaryFinder} callback that shall return either:
     * (1) a Jossons query that can retrieve data from the stored dataset mapping; or
     * (2) a join operation query to merge two datasets from the stored dataset mapping; or
     * (3) a database query statement that will further trigger {@code dataFinder} callback.</p>
     * <p>Any unresolvable placeholder will raise {@code NoValuePresentException} with the incomplete merged text content.
     * All unresolvable placeholders are quoted with "**" to replace the original "{{" and "}}".</p>
     *
     * @param template the template to be executed
     * @param dictionaryFinder a callback function to return solution query statement
     * @param dataFinder a callback function to process database query statement
     * @param progress a {@code ResolverProgress} object to log the resolver progress steps
     * @return The merged text content
     * @throws NoValuePresentException if any placeholder was unable to resolve
     */
    public String fillInPlaceholderWithResolver(String template, Function<String, String> dictionaryFinder,
                                                BiFunction<String, String, Josson> dataFinder,
                                                ResolverProgress progress) throws NoValuePresentException {
        if (StringUtils.isBlank(template)) {
            return template;
        }
        try {
            return fillInPlaceholderWithResolver(template, dictionaryFinder, dataFinder, false, progress);
        } finally {
            if (progress.isAutoMarkEnd()) {
                progress.markEnd();
            }
        }
    }

    private String fillInPlaceholderWithResolver(String template, Function<String, String> dictionaryFinder,
                                                 BiFunction<String, String, Josson> dataFinder, boolean isXml,
                                                 ResolverProgress progress) throws NoValuePresentException {
        Set<String> unresolvablePlaceholders = new HashSet<>();
        Set<String> unresolvedDatasetNames = new HashSet<>();
        List<String> checkInfiniteLoop = new ArrayList<>();
        for (;;progress.nextRound()) {
            try {
                if (!unresolvedDatasetNames.isEmpty()) {
                    throw new NoValuePresentException(new HashSet<>(unresolvedDatasetNames), null);
                }
                template = fillInPlaceholderLoop(template, isXml);
                break;
            } catch (NoValuePresentException e) {
                if (e.getPlaceholders() == null) {
                    unresolvedDatasetNames.clear();
                } else {
                    unresolvablePlaceholders.addAll(e.getPlaceholders());
                    template = e.getContent();
                }
                Map<String, String> namedQueries = new HashMap<>();
                e.getDatasetNames().forEach(name -> {
                    checkInfiniteLoop.add(name);
                    int half = checkInfiniteLoop.size() / 2;
                    int i = checkInfiniteLoop.size() - 2;
                    for (int j = i; j >= half; j--) {
                        if (checkInfiniteLoop.get(j).equals(name)) {
                            for (int k = j - 1; i >= j; i--, k--) {
                                if (!checkInfiniteLoop.get(k).equals(checkInfiniteLoop.get(i))) {
                                    break;
                                }
                            }
                            if (i < j) {
                                unresolvablePlaceholders.add(name);
                                putDataset(name, null);
                                return;
                            }
                            break;
                        }
                    }
                    String findQuery = dictionaryFinder.apply(name);
                    if (findQuery == null) {
                        putDataset(name, null);
                        return;
                    }
                    try {
                        findQuery = fillInPlaceholderLoop(findQuery, false);
                        if (!buildDataset(name, findQuery, dictionaryFinder, dataFinder, progress)) {
                            namedQueries.put(name, findQuery);
                            unresolvedDatasetNames.remove(name);
                        }
                    } catch (NoValuePresentException ex) {
                        if (ex.getPlaceholders().isEmpty()) {
                            ex.getDatasetNames().stream()
                                    .filter(s -> !namedQueries.containsKey(s))
                                    .forEach(unresolvedDatasetNames::add);
                        } else {
                            unresolvablePlaceholders.addAll(ex.getPlaceholders());
                            unresolvablePlaceholders.add(name);
                            putDataset(name, null);
                        }
                    }
                });
                if (!namedQueries.isEmpty()) {
                    progress.addStep("Resolving " + namedQueries);
                    namedQueries.forEach((name, findQuery) -> {
                        try {
                            JsonNode node = evaluateQuery(findQuery);
                            if (node == null) {
                                unresolvablePlaceholders.add(name);
                                putDataset(name, null);
                            } else {
                                putDataset(name, Josson.create(node));
                                unresolvedDatasetNames.remove(name);
                                progress.addResolvedNode(name, node);
                            }
                        } catch (UnresolvedDatasetException ex) {
                            unresolvedDatasetNames.add(ex.getDatasetName());
                        }
                    });
                }
            }
        }
        if (!unresolvablePlaceholders.isEmpty()) {
            progress.addStep("Unresolvable placeholders " + unresolvablePlaceholders);
            throw new NoValuePresentException(null, unresolvablePlaceholders, template);
        }
        return template;
    }

    /**
     * <p>Uses the stored dataset mapping and with the help of on demand callback dataset resolver to retrieve data.</p>
     * <p>An unresolved dataset will trigger {@code dictionaryFinder} callback that shall return either:
     * (1) a Jossons query that can retrieve data from the stored dataset mapping; or
     * (2) a join operation query to merge two datasets from the stored dataset mapping; or
     * (3) a database query statement that will further trigger {@code dataFinder} callback.</p>
     *
     * @param query the Jossons query
     * @param dictionaryFinder a callback function to return solution query statement
     * @param dataFinder a callback function to process database query statement
     * @param progress a {@code ResolverProgress} object to log the resolver progress steps
     * @return The resulting Jackson JsonNode
     */
    public JsonNode evaluateQueryWithResolver(String query, Function<String, String> dictionaryFinder,
                                              BiFunction<String, String, Josson> dataFinder,
                                              ResolverProgress progress) {
        JsonNode node = evaluateQueryWithResolverLoop(query, dictionaryFinder, dataFinder, progress);
        progress.addQueryResult(node);
        return node;
    }

    private JsonNode evaluateQueryWithResolverLoop(String query, Function<String, String> dictionaryFinder,
                                                   BiFunction<String, String, Josson> dataFinder,
                                                   ResolverProgress progress) {
        for (;;progress.nextRound()) {
            try {
                return evaluateQuery(query);
            } catch (UnresolvedDatasetException e) {
                String name = e.getDatasetName();
                JsonNode node = null;
                String findQuery = dictionaryFinder.apply(name);
                if (findQuery != null) {
                    try {
                        findQuery = fillInPlaceholderWithResolver(
                                findQuery, dictionaryFinder, dataFinder, false, progress);
                        if (buildDataset(name, findQuery, dictionaryFinder, dataFinder, progress)) {
                            continue;
                        }
                        progress.addResolvingFrom(name, findQuery);
                        node = evaluateQueryWithResolverLoop(findQuery, dictionaryFinder, dataFinder, progress);
                    } catch (NoValuePresentException ex) {
                        // ignore
                    }
                }
                putDataset(name, node == null ? null : Josson.create(node));
                progress.addResolvedNode(name, node);
            }
        }
    }

    /**
     * Evaluate a Jossons query to retrieve data from the stored dataset mapping.
     *
     * @param query the Jossons query
     * @return The resulting Jackson JsonNode
     * @throws UnresolvedDatasetException if the query is looking for a non-exists dataset
     */
    public JsonNode evaluateQuery(String query) throws UnresolvedDatasetException {
        String ifTrueValue = null;
        List<String[]> steps = decomposeTernarySteps(query);
        for (String[] step : steps) {
            JsonNode node = evaluateStatement(step[0]);
            if (step[1] == null) {
                return node;
            }
            ifTrueValue = step[1];
            if (node != null) {
                if (ifTrueValue.isEmpty()) {
                    if (!(node.isTextual() && node.textValue().isEmpty())) {
                        return node;
                    }
                } else if (node.asBoolean()) {
                    node = evaluateStatement(ifTrueValue);
                    if (node != null) {
                        return node;
                    }
                }
            }
        }
        return StringUtils.isEmpty(ifTrueValue) ? null : TextNode.valueOf("");
    }

    /**
     * Evaluate a Jossons statement to retrieve data from the stored dataset mapping.
     *
     * @param statement the Jossons statement
     * @return The resulting Jackson JsonNode
     * @throws UnresolvedDatasetException if the query is looking for a non-exists dataset
     */
    public JsonNode evaluateStatement(String statement) throws UnresolvedDatasetException {
        try {
            return toValueNode(statement);
        } catch (NumberFormatException e) {
            // continue
        }
        return new LogicalOpStack(datasets).evaluate(statement);
    }

    private static JsonNode joinNodes(JsonNode leftNode, String[] leftKeys, String leftArrayName, JoinOperator operator,
                                      JsonNode rightNode, String[] rightKeys, String rightArrayName) {
        String arrayName;
        if (operator == JoinOperator.RIGHT_JOIN_ONE || operator == JoinOperator.RIGHT_JOIN_MANY
                || (operator == JoinOperator.INNER_JOIN_ONE && !leftNode.isObject() && rightNode.isObject())) {
            JsonNode swapNode = leftNode;
            leftNode = rightNode;
            rightNode = swapNode;
            String[] swapKeys = leftKeys;
            leftKeys = rightKeys;
            rightKeys = swapKeys;
            if (operator == JoinOperator.RIGHT_JOIN_ONE) {
                operator = JoinOperator.LEFT_JOIN_ONE;
            } else if (operator == JoinOperator.RIGHT_JOIN_MANY) {
                operator = JoinOperator.LEFT_JOIN_MANY;
            }
            arrayName = leftArrayName;
        } else {
            arrayName = rightArrayName;
        }
        ArrayNode rightArray;
        if (rightNode.isArray()) {
            rightArray = (ArrayNode) rightNode;
        } else {
            rightArray = Josson.createArrayNode();
            rightArray.add(rightNode);
        }
        if (leftNode.isObject()) {
            return joinToObjectNode((ObjectNode) leftNode, leftKeys, operator, rightArray, rightKeys, arrayName);
        }
        ArrayNode joinedArray = Josson.createArrayNode();
        for (int i = 0; i < leftNode.size(); i++) {
            if (leftNode.get(i).isObject()) {
                ObjectNode joinedNode = joinToObjectNode(
                        (ObjectNode) leftNode.get(i), leftKeys, operator, rightArray, rightKeys, arrayName);
                if (joinedNode != null) {
                    joinedArray.add(joinedNode);
                }
            }
        }
        return joinedArray;
    }

    private static ObjectNode joinToObjectNode(ObjectNode leftObject, String[] leftKeys, JoinOperator operator,
                                               ArrayNode rightArray, String[] rightKeys, String arrayName) {
        String[] conditions = new String[leftKeys.length];
        for (int j = leftKeys.length - 1; j >= 0; j--) {
            JsonNode leftValue = Josson.getNode(leftObject, leftKeys[j]);
            if (leftValue == null || !leftValue.isValueNode()) {
                return null;
            }
            conditions[j] = rightKeys[j]
                    + (leftValue.isTextual() ? "='" : "=") + leftValue.asText().replaceAll("'", "''")
                    + (leftValue.isTextual() ? "'" : "");
        }
        if (operator == JoinOperator.LEFT_JOIN_MANY) {
            JsonNode rightToJoin = Josson.getNode(
                    rightArray, "[" + StringUtils.join(conditions, " & ") + "]*");
            if (rightToJoin != null) {
                ObjectNode joinedNode = leftObject.deepCopy();
                joinedNode.set(arrayName, rightToJoin);
                return joinedNode;
            }
        } else {
            JsonNode rightToJoin = Josson.getNode(
                    rightArray, "[" + StringUtils.join(conditions, " & ") + "]");
            if (rightToJoin != null && rightToJoin.isObject()) {
                ObjectNode joinedNode = leftObject.deepCopy();
                joinedNode.setAll((ObjectNode) rightToJoin);
                return joinedNode;
            }
            if (operator == JoinOperator.INNER_JOIN_ONE) {
                return null;
            }
        }
        return leftObject;
    }
}
