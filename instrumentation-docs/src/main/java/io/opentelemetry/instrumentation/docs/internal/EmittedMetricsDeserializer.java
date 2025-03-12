/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class EmittedMetricsDeserializer extends JsonDeserializer<EmittedMetrics> {

  @Override
  public EmittedMetrics deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) p.getCodec();
    JsonNode node = mapper.readTree(p);

    List<EmittedMetrics.Metric> metrics =
        mapper.convertValue(
            node.get("metrics"),
            mapper
                .getTypeFactory()
                .constructCollectionType(List.class, EmittedMetrics.Metric.class));

    return new EmittedMetrics(metrics);
  }
}
