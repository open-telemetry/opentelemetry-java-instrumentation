/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcCommonAttributesExtractor.RPC_METHOD_ORIGINAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
class RpcAttributesExtractorTest {

  private static class TestGetter implements RpcAttributesGetter<Map<String, String>, Void> {

    private final boolean predefined;

    private TestGetter(boolean predefined) {
      this.predefined = predefined;
    }

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

    @Nullable
    @Override
    public String getRpcMethod(Map<String, String> request) {
      String service = getService(request);
      String method = getMethod(request);
      if (service == null || method == null) {
        return null;
      }
      return service + "/" + method;
    }

    @Nullable
    @Override
    public String getErrorType(
        Map<String, String> request, @Nullable Void response, @Nullable Throwable error) {
      return request.get("errorType");
    }

    @Override
    public boolean isPredefined(Map<String, String> stringStringMap) {
      return predefined;
    }
  }

  @Test
  void server() {
    testExtractor(RpcServerAttributesExtractor.create(new TestGetter(false)), "my.Service/Method");
  }

  @Test
  void serverPredefined() {
    testExtractor(RpcServerAttributesExtractor.create(new TestGetter(true)), null);
  }

  @Test
  void client() {
    testExtractor(RpcClientAttributesExtractor.create(new TestGetter(false)), "my.Service/Method");
  }

  @Test
  void clientPredefined() {
    testExtractor(RpcClientAttributesExtractor.create(new TestGetter(true)), null);
  }

  // Stable semconv keys
  private static final AttributeKey<String> RPC_SYSTEM_NAME =
      AttributeKey.stringKey("rpc.system.name");

  // Old semconv keys (from RpcIncubatingAttributes)
  private static final AttributeKey<String> RPC_SYSTEM = RpcIncubatingAttributes.RPC_SYSTEM;

  private static final AttributeKey<String> RPC_SERVICE = RpcIncubatingAttributes.RPC_SERVICE;

  private static final AttributeKey<String> RPC_METHOD = RpcIncubatingAttributes.RPC_METHOD;

  private static void testExtractor(
      AttributesExtractor<Map<String, String>, Void> extractor, @Nullable String originalMethod) {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");

    Context context = Context.root();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);

    // Build expected entries list based on semconv mode
    List<Map.Entry<? extends AttributeKey<?>, ?>> expectedEntries = new ArrayList<>();

    if (SemconvStability.emitStableRpcSemconv()) {
      expectedEntries.add(entry(RPC_SYSTEM_NAME, "test"));
      if (originalMethod != null) {
        expectedEntries.add(entry(RPC_METHOD_ORIGINAL, originalMethod));
        expectedEntries.add(entry(RPC_METHOD, "_OTHER"));
      } else {
        expectedEntries.add(entry(RPC_METHOD, "my.Service/Method"));
      }
    }

    if (SemconvStability.emitOldRpcSemconv()) {
      expectedEntries.add(entry(RPC_SYSTEM, "test"));
      expectedEntries.add(entry(RPC_SERVICE, "my.Service"));
      if (!SemconvStability.emitStableRpcSemconv()) {
        expectedEntries.add(entry(RPC_METHOD, "Method"));
      }
    }

    // safe conversion for test assertions
    @SuppressWarnings({"unchecked", "rawtypes"})
    Map.Entry<? extends AttributeKey<?>, ?>[] expectedArray =
        (Map.Entry<? extends AttributeKey<?>, ?>[]) expectedEntries.toArray(new Map.Entry[0]);
    assertThat(attributes.build()).containsOnly(expectedArray);

    extractor.onEnd(attributes, context, request, null, null);
    assertThat(attributes.build()).containsOnly(expectedArray);
  }

  @Test
  void shouldExtractErrorType_getter() {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");
    request.put("errorType", "CANCELLED");

    AttributesExtractor<Map<String, String>, Void> extractor =
        RpcServerAttributesExtractor.create(new TestGetter(false));

    Context context = Context.root();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    extractor.onEnd(attributes, context, request, null, null);

    if (SemconvStability.emitStableRpcSemconv()) {
      assertThat(attributes.build()).containsEntry(ERROR_TYPE, "CANCELLED");
    }
  }

  @Test
  void shouldExtractErrorType_exceptionClassName() {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");

    AttributesExtractor<Map<String, String>, Void> extractor =
        RpcServerAttributesExtractor.create(new TestGetter(false));

    Context context = Context.root();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    extractor.onEnd(attributes, context, request, null, new IllegalArgumentException());

    if (SemconvStability.emitStableRpcSemconv()) {
      assertThat(attributes.build())
          .containsEntry(ERROR_TYPE, "java.lang.IllegalArgumentException");
    }
  }

  @Test
  void shouldNotExtractErrorType_noError() {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");

    AttributesExtractor<Map<String, String>, Void> extractor =
        RpcServerAttributesExtractor.create(new TestGetter(false));

    Context context = Context.root();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    extractor.onEnd(attributes, context, request, null, null);

    if (SemconvStability.emitStableRpcSemconv()) {
      assertThat(attributes.build()).doesNotContainKey(ERROR_TYPE);
    }
  }
}
