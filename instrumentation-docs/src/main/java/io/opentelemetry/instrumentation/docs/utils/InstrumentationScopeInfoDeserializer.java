/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.io.IOException;

public class InstrumentationScopeInfoDeserializer
    extends JsonDeserializer<InstrumentationScopeInfo> {

  @Override
  public InstrumentationScopeInfo deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    JsonNode node = mapper.readTree(p);

    String name = node.get("name").asText();
    String version = node.has("version") ? node.get("version").asText() : null;
    String schemaUrl = node.has("schemaUrl") ? node.get("schemaUrl").asText() : null;
    Attributes attributes = mapper.convertValue(node.get("attributes"), Attributes.class);

    return InstrumentationScopeInfo.builder(name)
        .setVersion(version != null ? version : "")
        .setSchemaUrl(schemaUrl != null ? schemaUrl : "")
        .setAttributes(attributes)
        .build();
  }
}
