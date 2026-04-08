/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InjectionStateTest {

  @Test
  void shouldUseIso88591WhenResponseEncodingIsUnavailable() {
    InjectionState state =
        new InjectionState(
            new SnippetInjectingResponseWrapper() {
              @Override
              public boolean isContentTypeTextHtml() {
                return true;
              }

              @Override
              public void updateContentLengthIfPreviouslySet() {}

              @Override
              public boolean isNotSafeToInject() {
                return false;
              }

              @Override
              public String getCharacterEncoding() {
                return null;
              }
            });

    assertThat(state.getCharacterEncoding()).isEqualTo(ISO_8859_1.name());
  }
}
