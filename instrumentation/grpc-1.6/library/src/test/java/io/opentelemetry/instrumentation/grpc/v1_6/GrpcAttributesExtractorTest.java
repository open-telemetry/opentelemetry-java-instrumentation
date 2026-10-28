/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.testing.junit.rpc.SemconvRpcStabilityUtil.emitPreviewRpcSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.HashSet;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcAttributesExtractorTest {

  @ParameterizedTest
  @ValueSource(strings = {"INCLUDED-KEY", "INCLUDED-*", "INCLUDED-?EY"})
  void capturesSelectedRequestHeaders(String includedPattern) {
    Metadata metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("included-key", Metadata.ASCII_STRING_MARSHALLER), "included-value");
    metadata.put(Metadata.Key.of("included-key", Metadata.ASCII_STRING_MARSHALLER), "second-value");
    metadata.put(
        Metadata.Key.of("included-excluded", Metadata.ASCII_STRING_MARSHALLER), "excluded-value");
    metadata.put(Metadata.Key.of("other-key", Metadata.ASCII_STRING_MARSHALLER), "other-value");
    metadata.put(
        Metadata.Key.of("included-bin", Metadata.BINARY_BYTE_MARSHALLER),
        "binary-value".getBytes(UTF_8));
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(asList(includedPattern, "missing-key"))
            .setExcluded(singletonList("*-EXCLUDED"))
            .build();
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(selector).onEnd(attributes, Context.root(), request, null, null);

    Attributes result = attributes.build();
    assertThat(result.get(legacyHeaderAttributeKey("included-key")))
        .isEqualTo(emitOldRpcSemconv() ? asList("included-value", "second-value") : null);
    assertThat(result.get(stableHeaderAttributeKey("included-key")))
        .isEqualTo(emitPreviewRpcSemconv() ? asList("included-value", "second-value") : null);
    assertThat(result.get(stringArrayKey("rpc.request.metadata.included-key"))).isNull();
    assertThat(result.asMap())
        .hasSize((emitOldRpcSemconv() ? 1 : 0) + (emitPreviewRpcSemconv() ? 1 : 0));
    assertExcludedHeader(result, "included-excluded");
    assertExcludedHeader(result, "other-key");
    assertExcludedHeader(result, "included-bin");
  }

  @Test
  void absentSelectorCapturesNothing() {
    Metadata metadata = new Metadata();
    metadata.put(Metadata.Key.of("some-key", Metadata.ASCII_STRING_MARSHALLER), "some-value");
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(null).onEnd(attributes, Context.root(), request, null, null);

    assertExcludedHeader(attributes.build(), "some-key");
  }

  @Test
  void emptySelectorCapturesNothing() {
    Metadata metadata = new Metadata();
    metadata.put(Metadata.Key.of("some-key", Metadata.ASCII_STRING_MARSHALLER), "some-value");
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(IncludeExclude.builder().build())
        .onEnd(attributes, Context.root(), request, null, null);

    assertExcludedHeader(attributes.build(), "some-key");
  }

  @Test
  void matchesRequestHeadersCaseInsensitively() {
    Metadata delegate = new Metadata();
    delegate.put(
        Metadata.Key.of("included-key", Metadata.ASCII_STRING_MARSHALLER), "included-value");
    delegate.put(
        Metadata.Key.of("excluded-key", Metadata.ASCII_STRING_MARSHALLER), "excluded-value");
    // a transport could surface metadata key names with casing that Metadata.Key would normalize
    Metadata metadata = spy(delegate);
    when(metadata.keys()).thenReturn(new HashSet<>(asList("Included-Key", "Excluded-Key")));
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(singletonList("*-key"))
            .setExcluded(singletonList("excluded-key"))
            .build();
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(selector).onEnd(attributes, Context.root(), request, null, null);

    Attributes result = attributes.build();
    assertThat(result.get(legacyHeaderAttributeKey("included-key")))
        .isEqualTo(emitOldRpcSemconv() ? singletonList("included-value") : null);
    assertThat(result.get(stableHeaderAttributeKey("included-key")))
        .isEqualTo(emitPreviewRpcSemconv() ? singletonList("included-value") : null);
    assertExcludedHeader(result, "Included-Key");
    assertExcludedHeader(result, "excluded-key");
    assertExcludedHeader(result, "Excluded-Key");
  }

  @ParameterizedTest
  @ValueSource(strings = {":authority", "x-foo!bar", "x_foo+bar", "x-foo~bar"})
  void skipsKeysThatGrpcMetadataCannotRepresent(String key) {
    Metadata metadata = mock(Metadata.class);
    when(metadata.keys()).thenReturn(singleton(key));
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();
    IncludeExclude selector = IncludeExclude.builder().setIncluded(singleton("*")).build();

    createExtractor(selector).onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
  }

  private static void assertExcludedHeader(Attributes attributes, String key) {
    assertThat(attributes.get(legacyHeaderAttributeKey(key))).isNull();
    assertThat(attributes.get(stableHeaderAttributeKey(key))).isNull();
  }

  private static GrpcAttributesExtractor createExtractor(@Nullable IncludeExclude selector) {
    GrpcAttributesExtractor extractor =
        new GrpcAttributesExtractor(new GrpcRpcAttributesGetter(false), selector);
    extractor.internalInitialize(OpenTelemetry.noop());
    return extractor;
  }

  private static AttributeKey<List<String>> legacyHeaderAttributeKey(String key) {
    return stringArrayKey("rpc.grpc.request.metadata." + key);
  }

  private static AttributeKey<List<String>> stableHeaderAttributeKey(String key) {
    return stringArrayKey("rpc.request.header." + key);
  }
}
