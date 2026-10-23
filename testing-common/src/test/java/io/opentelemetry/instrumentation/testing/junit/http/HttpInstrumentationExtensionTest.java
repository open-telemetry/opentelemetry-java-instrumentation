/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.LibraryTestRunner;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

class HttpInstrumentationExtensionTest {

  static {
    // TODO: remove once test dependency on javaagent-tooling is removed
    System.setProperty("io.opentelemetry.context.contextStorageProvider", "default");
  }

  @Test
  void clientLibraryFactory() {
    InstrumentationExtension extension = HttpClientInstrumentationExtension.forLibrary();
    assertThat(extension).isExactlyInstanceOf(HttpClientInstrumentationExtension.class);
    assertThat(extension.getOpenTelemetry())
        .isSameAs(LibraryTestRunner.instance().getOpenTelemetry());
    AbstractHttpClientTest<?> fixture = mock(AbstractHttpClientTest.class);
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getRequiredTestInstance()).thenReturn(fixture);

    extension.beforeEach(context);
    verify(fixture).setTesting(eq(LibraryTestRunner.instance()), any(HttpClientTestServer.class));
  }

  @Test
  void serverLibraryFactory() throws Exception {
    InstrumentationExtension extension = HttpServerInstrumentationExtension.forLibrary();
    assertThat(extension).isExactlyInstanceOf(HttpServerInstrumentationExtension.class);
    assertThat(extension.getOpenTelemetry())
        .isSameAs(LibraryTestRunner.instance().getOpenTelemetry());
    AbstractHttpServerUsingTest<?> fixture = mock(AbstractHttpServerUsingTest.class);
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getRequiredTestInstance()).thenReturn(fixture);

    extension.beforeAll(context);
    try {
      verify(fixture).setTesting(eq(LibraryTestRunner.instance()), any(), any(), anyInt());
    } finally {
      extension.afterAll(context);
    }
  }
}
