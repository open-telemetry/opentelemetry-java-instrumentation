/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HostAddressAndPortExtractorTest {

  private static final String REQUEST = "request";

  @Mock HttpCommonAttributesGetter<String, String> getter;
  @Mock AddressAndPortExtractor.AddressPortSink sink;

  @InjectMocks HostAddressAndPortExtractor<String> underTest;

  @Test
  void noHostHeader() {
    when(getter.getHttpRequestHeader(REQUEST, "host")).thenReturn(emptyList());

    underTest.extract(sink, REQUEST);

    verifyNoInteractions(sink);
  }

  @Test
  void justHost() {
    when(getter.getHttpRequestHeader(REQUEST, "host")).thenReturn(singletonList("host"));

    underTest.extract(sink, REQUEST);

    verify(sink).setAddress("host");
    verifyNoMoreInteractions(sink);
  }

  @Test
  void portIsNotNumeric() {
    when(getter.getHttpRequestHeader(REQUEST, "host")).thenReturn(singletonList("host:port"));

    underTest.extract(sink, REQUEST);

    verify(sink).setAddress("host");
    verifyNoMoreInteractions(sink);
  }

  @Test
  void hostAndPort() {
    when(getter.getHttpRequestHeader(REQUEST, "host")).thenReturn(singletonList("host:42"));

    underTest.extract(sink, REQUEST);

    verify(sink).setAddress("host");
    verify(sink).setPort(42);
    verifyNoMoreInteractions(sink);
  }
}
