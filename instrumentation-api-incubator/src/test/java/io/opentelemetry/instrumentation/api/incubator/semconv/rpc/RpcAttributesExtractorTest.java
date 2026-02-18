/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
class RpcAttributesExtractorTest {

  enum TestGetter implements RpcAttributesGetter<Map<String, String>, Void> {
    INSTANCE;

    @Override
    public String getSystem(Map<String, String> request) {
      return "test";
    }

    @Override
    public String getService(Map<String, String> request) {
      return request.get("service");
    }

    @Deprecated
    @Override
    public String getMethod(Map<String, String> request) {
      return request.get("method");
    }
  }

  @Test
  void server() {
    testExtractor(RpcServerAttributesExtractor.create(TestGetter.INSTANCE));
  }

  @Test
  void client() {
    testExtractor(RpcClientAttributesExtractor.create(TestGetter.INSTANCE));
  }

  private static void testExtractor(AttributesExtractor<Map<String, String>, Void> extractor) {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");

    Context context = Context.root();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    assertThat(attributes.build())
        .containsOnly(
            entry(RPC_SYSTEM, "test"),
            entry(RPC_SERVICE, "my.Service"),
            entry(RPC_METHOD, "Method"));
    extractor.onEnd(attributes, context, request, null, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(RPC_SYSTEM, "test"),
            entry(RPC_SERVICE, "my.Service"),
            entry(RPC_METHOD, "Method"));
  }
}
