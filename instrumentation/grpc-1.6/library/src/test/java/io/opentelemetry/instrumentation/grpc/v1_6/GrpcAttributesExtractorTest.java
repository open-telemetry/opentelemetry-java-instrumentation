/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcAttributesExtractorTest {

  @ParameterizedTest
  @ValueSource(strings = {"included-key", "INCLUDED-KEY", "Included-Key"})
  void capturesExactRequestHeadersIgnoringCase(String includedName) {
    Metadata metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("included-key", Metadata.ASCII_STRING_MARSHALLER), "included-value");
    metadata.put(Metadata.Key.of("included-key", Metadata.ASCII_STRING_MARSHALLER), "second-value");
    metadata.put(
        Metadata.Key.of("included-excluded", Metadata.ASCII_STRING_MARSHALLER), "excluded-value");
    metadata.put(Metadata.Key.of("other-key", Metadata.ASCII_STRING_MARSHALLER), "other-value");
    metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "******");
    metadata.put(Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER), "session=secret");
    metadata.put(
        Metadata.Key.of("included-bin", Metadata.BINARY_BYTE_MARSHALLER),
        "binary-value".getBytes(UTF_8));
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(asList(includedName, "missing-key"))
        .onEnd(attributes, Context.root(), request, null, null);

    Attributes result = attributes.build();
    assertThat(result)
        .isEqualTo(
            Attributes.of(
                stringArrayKey("rpc.request.header.included-key"),
                asList("included-value", "second-value")));
  }

  @Test
  void emptyListCapturesNothing() {
    Metadata metadata = new Metadata();
    metadata.put(Metadata.Key.of("some-key", Metadata.ASCII_STRING_MARSHALLER), "some-value");
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(emptyList()).onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"*", "?", "included-*", "included-?ey"})
  void doesNotExpandWildcardsOrCaptureUnlistedSensitiveHeaders(String name) {
    Metadata metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("included-key", Metadata.ASCII_STRING_MARSHALLER), "included-value");
    metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "******");
    metadata.put(Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER), "session=secret");
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(singletonList(name)).onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        ":authority",
        "included-bin",
        "INCLUDED-BIN",
        "x-foo!bar",
        "x_foo+bar",
        "x-foo~bar"
      })
  void skipsBinaryPseudoAndInvalidHeaders(String key) {
    Metadata metadata = mock(Metadata.class);
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), metadata, null, null);
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(singletonList(key)).onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
    verifyNoInteractions(metadata);
  }

  @Test
  void missingMetadataCapturesNothing() {
    GrpcRequest request =
        GrpcRequest.createServerRequest(mock(MethodDescriptor.class), null, null, null);
    AttributesBuilder attributes = Attributes.builder();

    createExtractor(singletonList("included-key"))
        .onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
  }

  private static GrpcAttributesExtractor createExtractor(Collection<String> capturedHeaders) {
    return new GrpcAttributesExtractor(new GrpcRpcAttributesGetter(false), capturedHeaders);
  }
}
