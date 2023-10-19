/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.doReturn;

import io.opentelemetry.instrumentation.api.instrumenter.network.internal.AddressAndPort;
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
class ForwardedForAddressAndPortExtractorTest {

  @Mock HttpServerAttributesGetter<String, String> getter;

  @InjectMocks ForwardedForAddressAndPortExtractor<String> underTest;

  @ParameterizedTest
  @ArgumentsSource(ForwardedArgs.class)
  void shouldParseForwarded(
      List<String> headers, @Nullable String expectedAddress, @Nullable Integer expectedPort) {
    doReturn(headers).when(getter).getHttpRequestHeader("request", "forwarded");

    AddressAndPort sink = new AddressAndPort();
    underTest.extract(sink, "request");

    assertThat(sink.getAddress()).isEqualTo(expectedAddress);
    assertThat(sink.getPort()).isEqualTo(expectedPort);
  }

  static final class ForwardedArgs implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          // empty/invalid headers
          arguments(singletonList(""), null, null),
          arguments(singletonList("for="), null, null),
          arguments(singletonList("for=;"), null, null),
          arguments(singletonList("for=\""), null, null),
          arguments(singletonList("for=\"\""), null, null),
          arguments(singletonList("for=\"1.2.3.4"), null, null),
          arguments(singletonList("for=\"[::1]"), null, null),
          arguments(singletonList("for=[::1"), null, null),
          arguments(singletonList("for=\"[::1\""), null, null),
          arguments(singletonList("for=\"[::1\"]"), null, null),
          arguments(singletonList("by=1.2.3.4, test=abc"), null, null),

          // ipv6
          arguments(singletonList("for=[::1]"), "::1", null),
          arguments(singletonList("For=[::1]"), "::1", null),
          arguments(singletonList("for=\"[::1]\":42"), "::1", null),
          arguments(singletonList("for=[::1]:42"), "::1", 42),
          arguments(singletonList("for=\"[::1]:42\""), "::1", 42),
          arguments(singletonList("for=[::1], for=1.2.3.4"), "::1", null),
          arguments(singletonList("for=[::1]; for=1.2.3.4:42"), "::1", null),
          arguments(singletonList("for=[::1]:42abc"), "::1", 42),
          arguments(singletonList("for=[::1]:abc"), "::1", null),

          // ipv4
          arguments(singletonList("for=1.2.3.4"), "1.2.3.4", null),
          arguments(singletonList("FOR=1.2.3.4"), "1.2.3.4", null),
          arguments(singletonList("for=1.2.3.4, :42"), "1.2.3.4", null),
          arguments(singletonList("for=1.2.3.4;proto=https;by=4.3.2.1"), "1.2.3.4", null),
          arguments(singletonList("for=1.2.3.4:42"), "1.2.3.4", 42),
          arguments(singletonList("for=1.2.3.4:42abc"), "1.2.3.4", 42),
          arguments(singletonList("for=1.2.3.4:abc"), "1.2.3.4", null),
          arguments(singletonList("for=1.2.3.4; for=4.3.2.1:42"), "1.2.3.4", null),

          // multiple headers
          arguments(asList("proto=https", "for=1.2.3.4", "for=[::1]:42"), "1.2.3.4", null));
    }
  }

  @ParameterizedTest
  @ArgumentsSource(ForwardedForArgs.class)
  void shouldParseForwardedFor(
      List<String> headers, @Nullable String expectedAddress, @Nullable Integer expectedPort) {
    doReturn(emptyList()).when(getter).getHttpRequestHeader("request", "forwarded");
    doReturn(headers).when(getter).getHttpRequestHeader("request", "x-forwarded-for");

    AddressAndPort sink = new AddressAndPort();
    underTest.extract(sink, "request");

    assertThat(sink.getAddress()).isEqualTo(expectedAddress);
    assertThat(sink.getPort()).isEqualTo(expectedPort);
  }

  static final class ForwardedForArgs implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
      return Stream.of(
          // empty/invalid headers
          arguments(singletonList(""), null, null),
          arguments(singletonList(";"), null, null),
          arguments(singletonList("\""), null, null),
          arguments(singletonList("\"\""), null, null),
          arguments(singletonList("\"1.2.3.4"), null, null),
          arguments(singletonList("\"[::1]"), null, null),
          arguments(singletonList("[::1"), null, null),
          arguments(singletonList("\"[::1\""), null, null),
          arguments(singletonList("\"[::1\"]"), null, null),

          // ipv6
          arguments(singletonList("[::1]"), "::1", null),
          arguments(singletonList("\"[::1]\":42"), "::1", null),
          arguments(singletonList("[::1]:42"), "::1", 42),
          arguments(singletonList("\"[::1]:42\""), "::1", 42),
          arguments(singletonList("[::1],1.2.3.4"), "::1", null),
          arguments(singletonList("[::1];1.2.3.4:42"), "::1", null),
          arguments(singletonList("[::1]:42abc"), "::1", 42),
          arguments(singletonList("[::1]:abc"), "::1", null),

          // ipv4
          arguments(singletonList("1.2.3.4"), "1.2.3.4", null),
          arguments(singletonList("1.2.3.4, :42"), "1.2.3.4", null),
          arguments(singletonList("1.2.3.4,4.3.2.1"), "1.2.3.4", null),
          arguments(singletonList("1.2.3.4:42"), "1.2.3.4", 42),
          arguments(singletonList("1.2.3.4:42abc"), "1.2.3.4", 42),
          arguments(singletonList("1.2.3.4:abc"), "1.2.3.4", null),

          // ipv6 without brackets
          arguments(singletonList("::1"), "::1", null),
          arguments(singletonList("::1,::2,1.2.3.4"), "::1", null),
          arguments(singletonList("::1;::2;1.2.3.4"), "::1", null),

          // multiple headers
          arguments(asList("1.2.3.4", "::1"), "1.2.3.4", null));
    }
  }
}
