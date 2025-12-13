/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.util.logging.Level.FINE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class OpenSearchBodySanitizer {

  private static final Logger logger = Logger.getLogger(OpenSearchBodySanitizer.class.getName());

  private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();
  private static final String MASKED_VALUE = "?";
  private static final OpenSearchBodySanitizer DEFAULT_INSTANCE =
      new OpenSearchBodySanitizer(DEFAULT_OBJECT_MAPPER);

  private final ObjectMapper objectMapper;

  private OpenSearchBodySanitizer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public static OpenSearchBodySanitizer create() {
    return new OpenSearchBodySanitizer(DEFAULT_OBJECT_MAPPER);
  }

  public static OpenSearchBodySanitizer create(ObjectMapper objectMapper) {
    return new OpenSearchBodySanitizer(objectMapper);
  }

  public static OpenSearchBodySanitizer getDefault() {
    return DEFAULT_INSTANCE;
  }

  public static String sanitize(String jsonString) {
    return DEFAULT_INSTANCE.sanitizeInstance(jsonString);
  }

  public String sanitizeInstance(String jsonString) {
    if (jsonString == null) {
      return null;
    }

    List<String> queries = QuerySplitter.splitQueries(jsonString);
    if (queries.isEmpty()) {
      return null;
    }

    List<String> sanitizedQueries = new ArrayList<>();
    for (String query : queries) {
      String sanitized = sanitizeSingleQuery(query);
      sanitizedQueries.add(sanitized);
    }

    return QuerySplitter.joinQueries(sanitizedQueries);
  }

  private String sanitizeSingleQuery(String query) {
    try {
      JsonNode rootNode = objectMapper.readTree(query);
      JsonNode sanitizedNode = sanitizeNode(rootNode);
      return objectMapper.writeValueAsString(sanitizedNode);
    } catch (Exception e) {
      logger.log(FINE, "Failure sanitizing single query", e);
      return query;
    }
  }

  private JsonNode sanitizeNode(JsonNode node) {
    if (node == null || node.isNull()) {
      return node;
    }

    if (node.isTextual()) {
      return new TextNode(MASKED_VALUE);
    }

    if (node.isNumber() || node.isBoolean()) {
      return new TextNode(MASKED_VALUE);
    }

    if (node.isArray()) {
      ArrayNode arrayNode = objectMapper.createArrayNode();
      for (JsonNode element : node) {
        arrayNode.add(sanitizeNode(element));
      }
      return arrayNode;
    }

    if (node.isObject()) {
      ObjectNode objectNode = objectMapper.createObjectNode();

      for (Map.Entry<String, JsonNode> field : node.properties()) {
        String key = field.getKey();
        JsonNode value = field.getValue();

        objectNode.set(key, sanitizeNode(value));
      }
      return objectNode;
    }

    return node;
  }
}
