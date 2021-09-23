package com.octomix.josson;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class ResolverProgress {

    private ResolverDebugLevel debugLevel = ResolverDebugLevel.SHOW_CONTENT_OF_VALUE_NODE_ONLY;
    private boolean autoMarkEnd = true;
    private int round = 1;
    private final List<String> steps = new ArrayList<>();

    public ResolverProgress() {
    }

    public ResolverProgress(String subject) {
        steps.add(subject);
    }

    public ResolverProgress debugLevel(ResolverDebugLevel level) {
        debugLevel = level;
        return this;
    }

    public ResolverDebugLevel getResolverDebugLevel() {
        return debugLevel;
    }

    public ResolverProgress autoMarkEnd(boolean auto) {
        autoMarkEnd = auto;
        return this;
    }

    public boolean isAutoMarkEnd() {
        return autoMarkEnd;
    }

    public void markEnd() {
        addStep("End");
    }

    public List<String> getSteps() {
        return steps;
    }

    void nextRound() {
        round++;
    }

    void addResolvingFrom(String name, String query) {
        addStep("Resolving " + name + " from " + query);
    }

    void addResolvedNode(String name, JsonNode node) {
        if (node == null) {
            addStep("Unresolvable " + name);
        } else {
            addStep("Resolved " + name + " = " + resolvedValue(node));
        }
    }

    void addResolvedDataset(String name, Josson dataset) {
        if (dataset == null || dataset.getNode() == null) {
            addStep("Unresolvable " + name);
        } else {
            addStep("Resolved " + name + " = " + simplifyResolvedValue(dataset.getNode()));
        }
    }

    void addQueryResult(JsonNode node) {
        addStep("Query result = " + (node == null ? "null" : resolvedValue(node)));
    }

    void addStep(String step) {
        steps.add("Round " + round + " : " + step);
    }

    private String resolvedValue(JsonNode node) {
        switch (debugLevel) {
            case SHOW_CONTENT_UP_TO_ARRAY_NODE:
                if (node.isArray()) {
                    return node.toString();
                }
                // fallthrough
            case SHOW_CONTENT_UP_TO_OBJECT_NODE:
                if (node.isObject()) {
                    return node.toString();
                }
                // fallthrough
        }
        return simplifyResolvedValue(node);
    }

    private String simplifyResolvedValue(JsonNode node) {
        if (node.isObject()) {
            return "Object with " + node.size() + " elements";
        }
        if (node.isArray()) {
            return "Array with " + node.size() + " elements";
        }
        return node.toString();
    }
}
