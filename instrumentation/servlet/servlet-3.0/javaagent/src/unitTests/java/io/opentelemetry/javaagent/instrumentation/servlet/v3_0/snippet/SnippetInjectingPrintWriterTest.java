/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.bootstrap.servlet.SnippetInjectingResponseWrapper;
import io.opentelemetry.javaagent.instrumentation.servlet.common.snippet.SnippetInjectingPrintWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SnippetInjectingPrintWriterTest {
  private static final String SNIPPET = "<!-- snippet -->";
  private static final String HTML = "<head><title>test</title></head>";

  @ParameterizedTest
  @EnumSource(WriteMethod.class)
  void reclaimsWriterOwnershipAtWriteBoundary(WriteMethod writeMethod) {
    SnippetInjectingResponseWrapper response =
        new SnippetInjectingResponseWrapper() {
          @Override
          public boolean isContentTypeTextHtml() {
            return true;
          }

          @Override
          public boolean isCurrentRequest() {
            return true;
          }

          @Override
          public void updateContentLengthIfPreviouslySet() {}

          @Override
          public void resetContentLengthIfPreviouslyAdjusted() {}

          @Override
          public boolean isNotSafeToInject() {
            return false;
          }

          @Override
          public String getCharacterEncoding() {
            return "UTF-8";
          }
        };
    InjectionState state = new InjectionState(response);
    StringWriter output = new StringWriter();
    SnippetInjectingPrintWriter writer =
        new SnippetInjectingPrintWriter(new PrintWriter(output), SNIPPET, state);

    state.beginForward();
    state.resetBuffer();
    writeMethod.write(writer, HTML.substring(0, 3));

    assertThat(state.getHeadTagBytesSeen()).isEqualTo(3);
    assertThat(state.shouldProcessStreamWrite()).isFalse();

    writeMethod.write(writer, HTML.substring(3));
    writer.flush();

    assertThat(output.toString()).isEqualTo("<head>" + SNIPPET + "<title>test</title></head>");
  }

  private enum WriteMethod {
    STRING {
      @Override
      void write(PrintWriter writer, String value) {
        writer.write(value, 0, value.length());
      }
    },
    CHARACTER {
      @Override
      void write(PrintWriter writer, String value) {
        for (int i = 0; i < value.length(); i++) {
          writer.write(value.charAt(i));
        }
      }
    },
    CHARACTER_ARRAY {
      @Override
      void write(PrintWriter writer, String value) {
        char[] chars = value.toCharArray();
        writer.write(chars, 0, chars.length);
      }
    };

    abstract void write(PrintWriter writer, String value);
  }
}
