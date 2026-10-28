/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.HashSet;
import javax.annotation.Nullable;
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

    createExtractor(selector).onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build())
        .isEqualTo(
            Attributes.of(
                stringArrayKey("rpc.request.metadata.included-key"),
                singletonList("included-value")));
  }

  @Test
  void absentSelectorCapturesNothing() {
    Metadata metadata = new Metadata();
    metadata.put(Metadata.Key.of("some-key", Metadata.ASCII_STRING_MARSHALLER), "some-value");
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(null).onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
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

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
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

    createExtractor(selector).onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build())
        .isEqualTo(
            Attributes.of(
                stringArrayKey("rpc.request.metadata.included-key"),
                singletonList("included-value")));
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

  private static GrpcAttributesExtractor createExtractor(@Nullable IncludeExclude selector) {
    GrpcAttributesExtractor extractor =
        new GrpcAttributesExtractor(new GrpcRpcAttributesGetter(false), selector);
    extractor.internalInitialize(OpenTelemetry.noop());
    return extractor;
  }
}
