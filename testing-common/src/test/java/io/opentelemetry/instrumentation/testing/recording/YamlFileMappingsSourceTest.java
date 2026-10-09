/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.recording;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import io.opentelemetry.context.Scope;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class YamlFileMappingsSourceTest {

  @TempDir Path directory;

  @Test
  @SuppressWarnings("deprecation") // Tests the cleanup resource registered by RecordingExtension.
  void testStoreCleanupRestoresPreviousRecording() throws Throwable {
    ExtensionContext outer = testContext("trim");
    ExtensionContext inner = testContext("toString");
    ExtensionContext.Store store = mock(ExtensionContext.Store.class);
    when(inner.getStore(any())).thenReturn(store);
    RecordingExtension extension = new RecordingExtension("http://localhost");
    ArgumentCaptor<ExtensionContext.Store.CloseableResource> cleanup =
        ArgumentCaptor.forClass(ExtensionContext.Store.CloseableResource.class);
    YamlFileMappingsSource mappings =
        new YamlFileMappingsSource(new SingleRootFileSource(directory.toString()));

    try (Scope outerScope = YamlFileMappingsSource.setCurrentTest(outer)) {
      extension.afterTestExecution(inner);
      verify(store).put(eq(extension), cleanup.capture());
      try {
        mappings.save(get(urlEqualTo("/inner")).willReturn(ok()).build());
      } finally {
        cleanup.getValue().close();
      }
      mappings.save(get(urlEqualTo("/outer")).willReturn(ok()).build());
    }

    assertThat(directory.resolve("java.lang.string.tostring.yaml")).exists();
    assertThat(directory.resolve("java.lang.string.trim.yaml")).exists();
  }

  @Test
  void restoresTestAfterNestedRecordingFails() throws Exception {
    ExtensionContext outer = testContext("trim");
    ExtensionContext inner = testContext("toString");
    YamlFileMappingsSource mappings =
        new YamlFileMappingsSource(new SingleRootFileSource(directory.toString()));

    try (Scope outerScope = YamlFileMappingsSource.setCurrentTest(outer)) {
      assertThatThrownBy(
              () -> {
                try (Scope innerScope = YamlFileMappingsSource.setCurrentTest(inner)) {
                  mappings.save(get(urlEqualTo("/inner")).willReturn(ok()).build());
                  throw new IllegalStateException("recording failed");
                }
              })
          .isInstanceOf(IllegalStateException.class);
      mappings.save(get(urlEqualTo("/outer")).willReturn(ok()).build());
    }

    assertThat(directory.resolve("java.lang.string.tostring.yaml")).exists();
    assertThat(directory.resolve("java.lang.string.trim.yaml")).exists();
    assertThatThrownBy(() -> mappings.save(get(urlEqualTo("/outside")).willReturn(ok()).build()))
        .isInstanceOf(NullPointerException.class);
  }

  private static ExtensionContext testContext(String method) throws Exception {
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getTestMethod()).thenReturn(Optional.of(String.class.getMethod(method)));
    return context;
  }
}
