/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD_ORIGINAL;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SchemaUrlProvider;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class RpcAttributesExtractorTest {

  private static class TestGetter implements RpcAttributesGetter<Map<String, String>, Void> {

    @Override
    public String getRpcSystemName(Map<String, String> request) {
      return "test";
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

    @Override
    public String getRpcMethodOriginal(Map<String, String> request) {
      return request.get("originalMethod");
    }

    @Nullable
    @Override
    public String getErrorType(
        Map<String, String> request, @Nullable Void response, @Nullable Throwable error) {
      return request.get("errorType");
    }
  }

  @Test
  void server() {
    testExtractor(RpcServerAttributesExtractor.create(new TestGetter()));
  }

  @Test
  void client() {
    testExtractor(RpcClientAttributesExtractor.create(new TestGetter()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void globalInstanceRegisteredAfterExtractorCreation(boolean client) {
    GlobalOpenTelemetry.resetForTest();
    try {
      AttributesExtractor<Map<String, String>, Void> beforeRegistration =
          client
              ? RpcClientAttributesExtractor.create(new TestGetter())
              : RpcServerAttributesExtractor.create(new TestGetter());
      assertThat(GlobalOpenTelemetry.isSet()).isFalse();

      DeclarativeConfigProperties general =
          mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
      DeclarativeConfigProperties semconv = general.get("rpc").get("semconv");
      when(semconv.getInt("version")).thenReturn(1);
      when(semconv.getBoolean("experimental", false)).thenReturn(true);
      ConfigProvider configProvider = mock(ConfigProvider.class);
      when(configProvider.getGeneralInstrumentationConfig()).thenReturn(general);
      when(configProvider.getInstrumentationConfig("common")).thenReturn(empty());
      ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
      when(openTelemetry.getConfigProvider()).thenReturn(configProvider);
      GlobalOpenTelemetry.set(openTelemetry);

      AttributesExtractor<Map<String, String>, Void> afterRegistration =
          client
              ? RpcClientAttributesExtractor.create(new TestGetter())
              : RpcServerAttributesExtractor.create(new TestGetter());
      Map<String, String> request = new HashMap<>();
      request.put("service", "my.Service");
      request.put("method", "Method");
      AttributesBuilder attributes = Attributes.builder();
      afterRegistration.onStart(attributes, Context.root(), request);
      afterRegistration.onEnd(attributes, Context.root(), request, null, null);

      assertThat(attributes.build())
          .containsOnly(entry(RPC_SYSTEM_NAME, "test"), entry(RPC_METHOD, "my.Service/Method"));
      assertThat(((SchemaUrlProvider) afterRegistration).internalGetSchemaUrl())
          .isEqualTo(SchemaUrls.V1_44_0);
      testExtractor(beforeRegistration);
    } finally {
      GlobalOpenTelemetry.resetForTest();
    }
  }

  private static void testExtractor(AttributesExtractor<Map<String, String>, Void> extractor) {
    assertThat(((SchemaUrlProvider) extractor).internalGetSchemaUrl())
        .isEqualTo(emitPreviewRpcSemconv() ? SchemaUrls.V1_44_0 : SchemaUrls.V1_37_0);

    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");
    request.put("originalMethod", "my.Service/OriginalMethod");

    Context context = Context.root();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);

    // Build expected entries list based on semconv mode
    List<Map.Entry<? extends AttributeKey<?>, ?>> expectedEntries = new ArrayList<>();

    if (emitPreviewRpcSemconv()) {
      expectedEntries.add(entry(RPC_SYSTEM_NAME, "test"));
      expectedEntries.add(entry(RPC_METHOD, "my.Service/Method"));
      expectedEntries.add(entry(RPC_METHOD_ORIGINAL, "my.Service/OriginalMethod"));
    }

    if (emitOldRpcSemconv()) {
      expectedEntries.add(entry(RPC_SYSTEM, "test"));
      expectedEntries.add(entry(RPC_SERVICE, "my.Service"));
      if (!emitPreviewRpcSemconv()) {
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
        RpcServerAttributesExtractor.create(new TestGetter());

    Context context = Context.root();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    extractor.onEnd(attributes, context, request, null, null);

    if (emitPreviewRpcSemconv()) {
      assertThat(attributes.build()).containsEntry(ERROR_TYPE, "CANCELLED");
    }
  }

  @Test
  void shouldExtractErrorType_exceptionClassName() {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");

    AttributesExtractor<Map<String, String>, Void> extractor =
        RpcServerAttributesExtractor.create(new TestGetter());

    Context context = Context.root();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    extractor.onEnd(attributes, context, request, null, new IllegalArgumentException());

    if (emitPreviewRpcSemconv()) {
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
        RpcServerAttributesExtractor.create(new TestGetter());

    Context context = Context.root();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    extractor.onEnd(attributes, context, request, null, null);

    if (emitPreviewRpcSemconv()) {
      assertThat(attributes.build()).doesNotContainKey(ERROR_TYPE);
    }
  }
}
