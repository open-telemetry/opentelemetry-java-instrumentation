/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RpcAttributesExtractorTest {

  static class TestExtractor extends RpcAttributesExtractor<Map<String, String>, Void> {

    @Override
    protected String system(Map<String, String> request) {
      return "test";
    }

    @Override
    protected String service(Map<String, String> request) {
      return request.get("service");
    }

    @Override
    protected String method(Map<String, String> request) {
      return request.get("method");
    }
  }

  @Test
  void normal() {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");

    TestExtractor extractor = new TestExtractor();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, request);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.RPC_SYSTEM, "test"),
            entry(SemanticAttributes.RPC_SERVICE, "my.Service"),
            entry(SemanticAttributes.RPC_METHOD, "Method"));
    extractor.onEnd(attributes, request, null, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.RPC_SYSTEM, "test"),
            entry(SemanticAttributes.RPC_SERVICE, "my.Service"),
            entry(SemanticAttributes.RPC_METHOD, "Method"));
  }
}
