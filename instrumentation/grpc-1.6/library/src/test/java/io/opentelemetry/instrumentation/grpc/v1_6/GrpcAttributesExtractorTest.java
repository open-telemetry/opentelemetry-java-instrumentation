/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcAttributesExtractorTest {

  @Test
  void selectsCanonicalAsciiMetadataKeys() {
    Metadata metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("included-key", Metadata.ASCII_STRING_MARSHALLER), "included-value");
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
            .setIncluded(asList("INCLUDED-*", "missing-key"))
            .setExcluded(singletonList("*-EXCLUDED"))
            .build();
    AttributesBuilder attributes = Attributes.builder();

    new GrpcAttributesExtractor(new GrpcRpcAttributesGetter(), selector)
        .onEnd(attributes, Context.root(), request, null, null);

    Attributes result = attributes.build();
    assertThat(result.get(oldMetadataAttributeKey("included-key")))
        .isEqualTo(emitOldRpcSemconv() ? singletonList("included-value") : null);
    assertThat(result.get(stableMetadataAttributeKey("included-key")))
        .isEqualTo(emitStableRpcSemconv() ? singletonList("included-value") : null);
    assertExcludedMetadata(result, "included-excluded");
    assertExcludedMetadata(result, "other-key");
    assertExcludedMetadata(result, "included-bin");
  }

  @Test
  void absentSelectorCapturesNothing() {
    Metadata metadata = new Metadata();
    metadata.put(Metadata.Key.of("some-key", Metadata.ASCII_STRING_MARSHALLER), "some-value");
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    new GrpcAttributesExtractor(new GrpcRpcAttributesGetter(), null)
        .onEnd(attributes, Context.root(), request, null, null);

    assertExcludedMetadata(attributes.build(), "some-key");
  }

  @Test
  void emptySelectorCapturesNothing() {
    Metadata metadata = new Metadata();
    metadata.put(Metadata.Key.of("some-key", Metadata.ASCII_STRING_MARSHALLER), "some-value");
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    new GrpcAttributesExtractor(new GrpcRpcAttributesGetter(), IncludeExclude.builder().build())
        .onEnd(attributes, Context.root(), request, null, null);

    assertExcludedMetadata(attributes.build(), "some-key");
  }

  @Test
  void matchesMetadataKeysCaseInsensitively() {
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

    new GrpcAttributesExtractor(new GrpcRpcAttributesGetter(), selector)
        .onEnd(attributes, Context.root(), request, null, null);

    Attributes result = attributes.build();
    assertThat(result.get(oldMetadataAttributeKey("included-key")))
        .isEqualTo(emitOldRpcSemconv() ? singletonList("included-value") : null);
    assertThat(result.get(stableMetadataAttributeKey("included-key")))
        .isEqualTo(emitStableRpcSemconv() ? singletonList("included-value") : null);
    assertExcludedMetadata(result, "Included-Key");
    assertExcludedMetadata(result, "excluded-key");
    assertExcludedMetadata(result, "Excluded-Key");
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

    new GrpcAttributesExtractor(new GrpcRpcAttributesGetter(), selector)
        .onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
  }

  private static void assertExcludedMetadata(Attributes attributes, String key) {
    assertThat(attributes.get(oldMetadataAttributeKey(key))).isNull();
    assertThat(attributes.get(stableMetadataAttributeKey(key))).isNull();
  }

  private static AttributeKey<List<String>> oldMetadataAttributeKey(String key) {
    return stringArrayKey("rpc.grpc.request.metadata." + key);
  }

  private static AttributeKey<List<String>> stableMetadataAttributeKey(String key) {
    return stringArrayKey("rpc.request.metadata." + key);
  }
}
