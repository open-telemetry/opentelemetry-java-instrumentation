/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPort;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForwardedHostAddressAndPortExtractorTest {

  private static final String REQUEST = "request";

  @Mock HttpCommonAttributesGetter<String, String> getter;

  @InjectMocks ForwardedHostAddressAndPortExtractor<String> underTest;

  @ParameterizedTest
  @ArgumentsSource(ForwardedArgs.class)
  void shouldParseForwarded(
      List<String> headers, @Nullable String expectedAddress, @Nullable Integer expectedPort) {
    when(getter.getHttpRequestHeader(REQUEST, "forwarded")).thenReturn(headers);

    AddressAndPort sink = new AddressAndPort();
    underTest.extract(sink, REQUEST);

    assertThat(sink.getAddress()).isEqualTo(expectedAddress);
    assertThat(sink.getPort()).isEqualTo(expectedPort);
  }

  static final class ForwardedArgs implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          // empty/invalid headers
          arguments(singletonList(""), null, null),
          arguments(singletonList("host="), null, null),
          arguments(singletonList("host=;"), null, null),
          arguments(singletonList("host=\""), null, null),
          arguments(singletonList("host=\"\""), null, null),
          arguments(singletonList("host=\"example.com"), null, null),
          arguments(singletonList("by=1.2.3.4, test=abc"), null, null),
          arguments(singletonList("host=example.com"), "example.com", null),
          arguments(singletonList("host=\"example.com\""), "example.com", null),
          arguments(singletonList("host=example.com; test=abc:1234"), "example.com", null),
          arguments(singletonList("host=\"example.com\"; test=abc:1234"), "example.com", null),
          arguments(singletonList("host=example.com:port"), "example.com", null),
          arguments(singletonList("host=\"example.com:port\""), "example.com", null),
          arguments(singletonList("host=example.com:42"), "example.com", 42),
          arguments(singletonList("host=\"example.com:42\""), "example.com", 42),
          arguments(singletonList("host=example.com:42; test=abc:1234"), "example.com", 42),
          arguments(singletonList("host=\"example.com:42\"; test=abc:1234"), "example.com", 42),

          // multiple headers
          arguments(
              asList("proto=https", "host=example.com", "host=github.com:1234"),
              "example.com",
              null));
    }
  }

  @ParameterizedTest
  @ArgumentsSource(HostArgs.class)
  @SuppressWarnings("MockitoDoSetup")
  void shouldParseForwardedHost(
      List<String> headers, @Nullable String expectedAddress, @Nullable Integer expectedPort) {
    doReturn(emptyList()).when(getter).getHttpRequestHeader(REQUEST, "forwarded");
    doReturn(headers).when(getter).getHttpRequestHeader(REQUEST, "x-forwarded-host");

    AddressAndPort sink = new AddressAndPort();
    underTest.extract(sink, REQUEST);

    assertThat(sink.getAddress()).isEqualTo(expectedAddress);
    assertThat(sink.getPort()).isEqualTo(expectedPort);
  }

  @ParameterizedTest
  @ArgumentsSource(HostArgs.class)
  @SuppressWarnings("MockitoDoSetup")
  void shouldParsePseudoAuthority(
      List<String> headers, @Nullable String expectedAddress, @Nullable Integer expectedPort) {
    doReturn(emptyList()).when(getter).getHttpRequestHeader(REQUEST, "forwarded");
    doReturn(emptyList()).when(getter).getHttpRequestHeader(REQUEST, "x-forwarded-host");
    doReturn(headers).when(getter).getHttpRequestHeader(REQUEST, ":authority");

    AddressAndPort sink = new AddressAndPort();
    underTest.extract(sink, REQUEST);

    assertThat(sink.getAddress()).isEqualTo(expectedAddress);
    assertThat(sink.getPort()).isEqualTo(expectedPort);
  }

  @ParameterizedTest
  @ArgumentsSource(HostArgs.class)
  @SuppressWarnings("MockitoDoSetup")
  void shouldParseHost(
      List<String> headers, @Nullable String expectedAddress, @Nullable Integer expectedPort) {
    doReturn(emptyList()).when(getter).getHttpRequestHeader(REQUEST, "forwarded");
    doReturn(emptyList()).when(getter).getHttpRequestHeader(REQUEST, "x-forwarded-host");
    doReturn(emptyList()).when(getter).getHttpRequestHeader(REQUEST, ":authority");
    doReturn(headers).when(getter).getHttpRequestHeader(REQUEST, "host");

    AddressAndPort sink = new AddressAndPort();
    underTest.extract(sink, REQUEST);

    assertThat(sink.getAddress()).isEqualTo(expectedAddress);
    assertThat(sink.getPort()).isEqualTo(expectedPort);
  }

  static final class HostArgs implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          // empty/invalid headers
          arguments(singletonList(""), null, null),
          arguments(singletonList("\""), null, null),
          arguments(singletonList("\"\""), null, null),
          arguments(singletonList("example.com"), "example.com", null),
          arguments(singletonList("example.com:port"), "example.com", null),
          arguments(singletonList("example.com:42"), "example.com", 42),
          arguments(singletonList("\"example.com\""), "example.com", null),
          arguments(singletonList("\"example.com:port\""), "example.com", null),
          arguments(singletonList("\"example.com:42\""), "example.com", 42),

          // multiple headers
          arguments(asList("example.com", "github.com:1234"), "example.com", null));
    }
  }
}
