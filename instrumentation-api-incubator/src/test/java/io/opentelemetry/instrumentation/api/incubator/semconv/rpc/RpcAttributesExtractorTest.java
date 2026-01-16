/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.maybeUnstable;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.maybeUnstableMethodEntry;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RpcAttributesExtractorTest {

  enum TestGetter implements RpcAttributesGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public String getSystem(Map<String, String> request) {
      return "test";
    }

    @Override
    public String getService(Map<String, String> request) {
      return request.get("service");
    }

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

  // Stable semconv keys
  private static final AttributeKey<String> RPC_SYSTEM_NAME =
      AttributeKey.stringKey("rpc.system.name");
  private static final AttributeKey<String> RPC_METHOD_ORIGINAL =
      AttributeKey.stringKey("rpc.method_original");

  private static void testExtractor(AttributesExtractor<Map<String, String>, Void> extractor) {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");

    Context context = Context.root();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);

    assertThat(attributes.build())
        .containsOnly(
            entry(maybeUnstable(RPC_SYSTEM_NAME), "test"),
            maybeUnstableMethodEntry("my.Service", "Method"),
            entry(maybeUnstable(RPC_METHOD_ORIGINAL), "Method"));
    extractor.onEnd(attributes, context, request, null, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(maybeUnstable(RPC_SYSTEM_NAME), "test"),
            maybeUnstableMethodEntry("my.Service", "Method"),
            entry(maybeUnstable(RPC_METHOD_ORIGINAL), "Method"));
  }
}
