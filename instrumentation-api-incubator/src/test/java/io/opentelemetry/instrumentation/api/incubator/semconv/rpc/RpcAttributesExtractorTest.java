/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD_ORIGINAL;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM_NAME;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
  }

  private static final class CustomErrorTypeGetter extends TestGetter {
    @Nullable
    @Override
    public String getErrorType(
        Map<String, String> request, @Nullable Void response, @Nullable Throwable error) {
      return request.get("errorType");
    }
  }

  private static final class NullErrorTypeGetter extends TestGetter {
    @Override
    public String getErrorType(Map<String, String> request, Void response, Throwable error) {
      return null;
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

  private static void testExtractor(AttributesExtractor<Map<String, String>, Void> extractor) {
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
        RpcServerAttributesExtractor.create(new CustomErrorTypeGetter());

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

  @Test
  void shouldNotExtractErrorType_whenGetterReturnsNullForError() {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");
    request.put("errorType", "CANCELLED");

    AttributesExtractor<Map<String, String>, Void> extractor =
        RpcServerAttributesExtractor.create(new NullErrorTypeGetter());

    Context context = Context.root();
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    extractor.onEnd(attributes, context, request, null, new IllegalArgumentException());

    if (emitPreviewRpcSemconv()) {
      assertThat(attributes.build()).doesNotContainKey(ERROR_TYPE);
    }
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "OK,NULL,false",
        "CANCELLED,CANCELLED,true",
        "NULL,_OTHER,false",
        "NULL,NULL,true",
        "NULL,NULL,false"
      },
      nullValues = "NULL")
  void shouldExtractStatusSeparatelyFromErrorType(
      String statusCode, String errorType, boolean exception) {
    TestGetter getter =
        new TestGetter() {
          @Override
          public String getRpcStatusCode(
              Map<String, String> request, Void response, Throwable error) {
            return statusCode;
          }

          @Override
          public String getErrorType(Map<String, String> request, Void response, Throwable error) {
            return request.get("errorType");
          }
        };
    Map<String, String> request = new HashMap<>();
    request.put("errorType", errorType);
    Throwable error = exception ? new IllegalArgumentException() : null;

    for (AttributesExtractor<Map<String, String>, Void> extractor :
        asList(
            RpcClientAttributesExtractor.create(getter),
            RpcServerAttributesExtractor.create(getter))) {
      AttributesBuilder attributes = Attributes.builder();
      extractor.onEnd(attributes, Context.root(), request, null, error);

      assertThat(attributes.build())
          .isEqualTo(
              Attributes.builder()
                  .put(stringKey("rpc.status_code"), emitPreviewRpcSemconv() ? statusCode : null)
                  .put(ERROR_TYPE, emitPreviewRpcSemconv() ? errorType : null)
                  .build());
    }
  }

  @Test
  void statusCodeIsUnavailableByDefault() {
    assertThat(new TestGetter().getRpcStatusCode(new HashMap<>(), null, null)).isNull();
  }
}
