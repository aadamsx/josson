/*
 * Copyright 2020-2022 Octomix Software Technology Limited
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.octomix.josson.commons.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.octomix.josson.FuncExecutor.*;
import static com.octomix.josson.JossonCore.*;
import static com.octomix.josson.Mapper.MAPPER;
import static com.octomix.josson.PatternMatcher.decomposeFunctionParameters;

class FuncString {
    static JsonNode funcAbbreviate(JsonNode node, String params) {
        return applyWithTwoInt(node, params,
                JsonNode::isTextual,
                (jsonNode, paramList) -> {
                    int offset = paramList[0];
                    int maxWidth;
                    if (paramList[1] < Integer.MAX_VALUE) {
                        maxWidth = paramList[1];
                    } else {
                        maxWidth = offset;
                        offset = 0;
                    }
                    return new Integer[]{offset, maxWidth};
                },
                (jsonNode, intVar) -> TextNode.valueOf(StringUtils.abbreviate(jsonNode.asText(), intVar[0], intVar[1]))
        );
    }

    static JsonNode funcAppendIfMissing(JsonNode node, String params, boolean ignoreCase) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> TextNode.valueOf(ignoreCase ?
                        StringUtils.appendIfMissingIgnoreCase(jsonNode.asText(), (String) objVar) :
                        StringUtils.appendIfMissing(jsonNode.asText(), (String) objVar))
        );
    }

    static JsonNode funcCapitalize(JsonNode node, String params) {
        return applyWithoutArgument(node, params,
                JsonNode::isTextual,
                jsonNode -> TextNode.valueOf(StringUtils.capitalize(jsonNode.asText()))
        );
    }

    static JsonNode funcCenter(JsonNode node, String params) {
        return applyWithAlignment(node, params,
                JossonCore::nodeHasValue,
                (jsonNode, alignment) -> TextNode.valueOf(StringUtils.center(
                        jsonNode.asText(), alignment.getKey(), alignment.getValue()))
        );
    }

    static JsonNode funcConcat(JsonNode node, String params) {
        List<String> paramList = decomposeFunctionParameters(params, 1, -1);
        List<Pair<Character, String>> args = new ArrayList<>();
        for (String param : paramList) {
            if (param.isEmpty()) {
                continue;
            }
            if (param.charAt(0) == QUOTE_SYMBOL) {
                args.add(Pair.of(QUOTE_SYMBOL, unquoteString(param)));
            } else {
                args.add(Pair.of('.', param));
            }
        }
        if (!node.isArray()) {
            return TextNode.valueOf(funcConcatElement(node, args, -1));
        }
        ArrayNode array = MAPPER.createArrayNode();
        for (int i  = 0; i < node.size(); i++) {
            array.add(funcConcatElement(node, args, i));
        }
        return array;
    }

    private static String funcConcatElement(JsonNode node, List<Pair<Character, String>> args, int index) {
        StringBuilder sb = new StringBuilder();
        for (Pair<Character, String> arg : args) {
            if (arg.getKey() == QUOTE_SYMBOL) {
                sb.append(arg.getValue());
                continue;
            }
            JsonNode tryNode = getNodeByPath(node, index, arg.getValue());
            if (!nodeHasValue(tryNode)) {
                return null;
            }
            sb.append(tryNode.asText());
        }
        return sb.toString();
    }

    static JsonNode funcKeepAfter(JsonNode node, String params, boolean ignoreCase, boolean last) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> {
                    String find = (String) objVar;
                    if (find.isEmpty()) {
                        return jsonNode;
                    }
                    String text = jsonNode.asText();
                    int pos = last ?
                            (ignoreCase ?
                                    StringUtils.lastIndexOfIgnoreCase(text, find) :
                                    StringUtils.lastIndexOf(text, find)) :
                            (ignoreCase ?
                                    StringUtils.indexOfIgnoreCase(text, find) :
                                    StringUtils.indexOf(text, find));
                    return TextNode.valueOf(pos < 0 ? "" : text.substring(pos + find.length()));
                }
        );
    }

    static JsonNode funcKeepBefore(JsonNode node, String params, boolean ignoreCase, boolean last) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> {
                    String find = (String) objVar;
                    if (find.isEmpty()) {
                        return jsonNode;
                    }
                    String text = jsonNode.asText();
                    int pos = last ?
                            (ignoreCase ?
                                    StringUtils.lastIndexOfIgnoreCase(text, find) :
                                    StringUtils.lastIndexOf(text, find)) :
                            (ignoreCase ?
                                    StringUtils.indexOfIgnoreCase(text, find) :
                                    StringUtils.indexOf(text, find));
                    return TextNode.valueOf(pos < 0 ? "" : text.substring(0, pos));
                }
        );
    }

    static JsonNode funcLeftPad(JsonNode node, String params) {
        return applyWithAlignment(node, params,
                JossonCore::nodeHasValue,
                (jsonNode, alignment) -> TextNode.valueOf(StringUtils.leftPad(
                        jsonNode.asText(), alignment.getKey(), alignment.getValue()))
        );
    }

    static JsonNode funcLength(JsonNode node, String params) {
        return applyWithoutArgument(node, params,
                JossonCore::nodeHasValue,
                jsonNode -> IntNode.valueOf(jsonNode.asText().length())
        );
    }

    static JsonNode funcLowerCase(JsonNode node, String params) {
        return applyWithoutArgument(node, params,
                JsonNode::isTextual,
                jsonNode -> TextNode.valueOf(StringUtils.lowerCase(jsonNode.asText()))
        );
    }

    static JsonNode funcPrependIfMissing(JsonNode node, String params, boolean ignoreCase) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> TextNode.valueOf(ignoreCase ?
                        StringUtils.prependIfMissingIgnoreCase(jsonNode.asText(), (String) objVar) :
                        StringUtils.prependIfMissing(jsonNode.asText(), (String) objVar))
        );
    }

    static JsonNode funcRemoveEnd(JsonNode node, String params, boolean ignoreCase) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> TextNode.valueOf(ignoreCase ?
                        StringUtils.removeEndIgnoreCase(jsonNode.asText(), (String) objVar) :
                        StringUtils.removeEnd(jsonNode.asText(), (String) objVar))
        );
    }

    static JsonNode funcRemoveStart(JsonNode node, String params, boolean ignoreCase) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> TextNode.valueOf(ignoreCase ?
                        StringUtils.removeStartIgnoreCase(jsonNode.asText(), (String) objVar) :
                        StringUtils.removeStart(jsonNode.asText(), (String) objVar))
        );
    }

    static JsonNode funcRepeat(JsonNode node, String params) {
        return applyWithArguments(node, params, 1, 1,
                JossonCore::nodeHasValue,
                (jsonNode, paramList) -> getNodeAsInt(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> TextNode.valueOf(StringUtils.repeat(jsonNode.asText(), (int) objVar))
        );
    }

    static JsonNode funcReplace(JsonNode node, String params, boolean ignoreCase) {
        return applyWithArguments(node, params, 2, 3,
                JsonNode::isTextual,
                (jsonNode, paramList) -> Pair.of(
                        new String[]{getNodeAsText(jsonNode, paramList.get(0)), getNodeAsText(jsonNode, paramList.get(1))},
                        paramList.size() > 2 ? getNodeAsInt(jsonNode, paramList.get(2)) : -1),
                (jsonNode, objVar) -> {
                    String[] texts = (String[]) ((Pair<?, ?>) objVar).getKey();
                    int max = (int) ((Pair<?, ?>) objVar).getValue();
                    return TextNode.valueOf(StringUtils.replace(jsonNode.asText(), texts[0], texts[1], max, ignoreCase));
                }
        );
    }

    static JsonNode funcRightPad(JsonNode node, String params) {
        return applyWithAlignment(node, params,
                JossonCore::nodeHasValue,
                (jsonNode, alignment) -> TextNode.valueOf(StringUtils.rightPad(
                        jsonNode.asText(), alignment.getKey(), alignment.getValue()))
        );
    }

    static JsonNode funcSplit(JsonNode node, String params) {
        return applyWithArguments(node, params, 0, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> paramList.size() > 0 ? getNodeAsText(jsonNode, paramList.get(0)) : null,
                (jsonNode, objVar) -> {
                    ArrayNode array = MAPPER.createArrayNode();
                    for (String text : StringUtils.split(jsonNode.asText(), (String) objVar)) {
                        array.add(TextNode.valueOf(text));
                    }
                    return array;
                }
        );
    }

    static JsonNode funcStrip(JsonNode node, String params) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> TextNode.valueOf(StringUtils.strip(jsonNode.asText(), (String) objVar))
        );
    }

    static JsonNode funcStripEnd(JsonNode node, String params) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> TextNode.valueOf(StringUtils.stripEnd(jsonNode.asText(), (String) objVar))
        );
    }

    static JsonNode funcStripStart(JsonNode node, String params) {
        return applyWithArguments(node, params, 1, 1,
                JsonNode::isTextual,
                (jsonNode, paramList) -> getNodeAsText(jsonNode, paramList.get(0)),
                (jsonNode, objVar) -> TextNode.valueOf(StringUtils.stripStart(jsonNode.asText(), (String) objVar))
        );
    }

    static JsonNode funcSubstr(JsonNode node, String params) {
        return applyWithTwoInt(node, params,
                JsonNode::isTextual,
                (jsonNode, paramList) -> paramList,
                (jsonNode, intVar) -> TextNode.valueOf(StringUtils.substring(jsonNode.asText(), intVar[0], intVar[1]))
        );
    }

    static JsonNode funcTrim(JsonNode node, String params) {
        return applyWithoutArgument(node, params,
                JsonNode::isTextual,
                jsonNode -> TextNode.valueOf(StringUtils.trim(jsonNode.asText()))
        );
    }

    static JsonNode funcUncapitalize(JsonNode node, String params) {
        return applyWithoutArgument(node, params,
                JsonNode::isTextual,
                jsonNode -> TextNode.valueOf(StringUtils.uncapitalize(jsonNode.asText()))
        );
    }

    static JsonNode funcUpperCase(JsonNode node, String params) {
        return applyWithoutArgument(node, params,
                JsonNode::isTextual,
                jsonNode -> TextNode.valueOf(StringUtils.upperCase(jsonNode.asText()))
        );
    }
}
