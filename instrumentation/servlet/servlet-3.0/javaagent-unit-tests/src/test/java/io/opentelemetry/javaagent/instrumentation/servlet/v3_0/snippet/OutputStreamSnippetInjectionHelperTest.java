/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.TestUtil.readFile;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletOutputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class OutputStreamSnippetInjectionHelperTest {

  @Test
  void testInjectionForStringContainHeadTag() throws IOException {
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String original = readFile("beforeSnippetInjection.html");
    String correct = readFile("afterSnippetInjection.html");
    byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
    SnippetInjectingResponseWrapper response = mock(SnippetInjectingResponseWrapper.class);
    when(response.isCommitted()).thenReturn(false);
    when(response.getCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());
    InjectionState obj = new InjectionState(response);

    StringWriter writer = new StringWriter();

    ServletOutputStream sp =
        new ServletOutputStream() {
          @Override
          public void write(int b) throws IOException {
            writer.write(b);
          }
        };
    OutputStreamSnippetInjectionHelper helper = new OutputStreamSnippetInjectionHelper(testSnippet);
    boolean injected = helper.handleWrite(obj, sp, originalBytes, 0, originalBytes.length);
    assertThat(obj.getHeadTagBytesSeen()).isEqualTo(-1);
    assertThat(injected).isEqualTo(true);
    writer.flush();

    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo(correct);
  }

  @Test
  @Disabled
  void testInjectionForChinese() throws IOException {
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String original = readFile("beforeSnippetInjectionChinese.html");
    String correct = readFile("afterSnippetInjectionChinese.html");
    byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);

    SnippetInjectingResponseWrapper response = mock(SnippetInjectingResponseWrapper.class);
    when(response.isCommitted()).thenReturn(false);
    when(response.getCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());
    InjectionState obj = new InjectionState(response);

    StringWriter writer = new StringWriter();

    ServletOutputStream sp =
        new ServletOutputStream() {
          @Override
          public void write(int b) throws IOException {
            writer.write(b);
          }
        };
    OutputStreamSnippetInjectionHelper helper = new OutputStreamSnippetInjectionHelper(testSnippet);
    boolean injected = helper.handleWrite(obj, sp, originalBytes, 0, originalBytes.length);
    assertThat(obj.getHeadTagBytesSeen()).isEqualTo(-1);
    assertThat(injected).isEqualTo(true);
    writer.flush();

    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo(correct);
  }

  @Test
  void testInjectionForStringWithoutHeadTag() throws IOException {
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String original = readFile("htmlWithoutHeadTag.html");
    byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);

    SnippetInjectingResponseWrapper response = mock(SnippetInjectingResponseWrapper.class);
    when(response.isCommitted()).thenReturn(false);
    when(response.getCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());
    InjectionState obj = new InjectionState(response);
    StringWriter writer = new StringWriter();

    ServletOutputStream sp =
        new ServletOutputStream() {
          @Override
          public void write(int b) throws IOException {
            writer.write(b);
          }
        };
    OutputStreamSnippetInjectionHelper helper = new OutputStreamSnippetInjectionHelper(testSnippet);
    boolean injected = helper.handleWrite(obj, sp, originalBytes, 0, originalBytes.length);
    assertThat(obj.getHeadTagBytesSeen()).isEqualTo(0);
    assertThat(injected).isEqualTo(false);
    writer.flush();
    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo("");
  }

  @Test
  void testHeadTagSplitAcrossTwoWrites() throws IOException {
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String originalFirstPart = "<!DOCTYPE html>\n" + "<html lang=\"en\">\n" + "<he";
    byte[] originalFirstPartBytes = originalFirstPart.getBytes(StandardCharsets.UTF_8);
    SnippetInjectingResponseWrapper response = mock(SnippetInjectingResponseWrapper.class);
    when(response.isCommitted()).thenReturn(false);
    when(response.getCharacterEncoding()).thenReturn(StandardCharsets.UTF_8.name());
    InjectionState obj = new InjectionState(response);
    StringWriter writer = new StringWriter();

    ServletOutputStream sp =
        new ServletOutputStream() {
          @Override
          public void write(int b) throws IOException {
            writer.write(b);
          }
        };
    OutputStreamSnippetInjectionHelper helper = new OutputStreamSnippetInjectionHelper(testSnippet);
    boolean injected =
        helper.handleWrite(obj, sp, originalFirstPartBytes, 0, originalFirstPartBytes.length);

    writer.flush();
    String result = writer.toString();
    assertThat(obj.getHeadTagBytesSeen()).isEqualTo(3);
    assertThat(result).isEqualTo("");
    assertThat(injected).isEqualTo(false);
    String originalSecondPart =
        "ad>\n"
            + "  <meta charset=\"UTF-8\">\n"
            + "  <title>Title</title>\n"
            + "</head>\n"
            + "<body>\n"
            + "\n"
            + "</body>\n"
            + "</html>";
    byte[] originalSecondPartBytes = originalSecondPart.getBytes(StandardCharsets.UTF_8);
    injected =
        helper.handleWrite(obj, sp, originalSecondPartBytes, 0, originalSecondPartBytes.length);
    assertThat(obj.getHeadTagBytesSeen()).isEqualTo(-1);
    assertThat(injected).isEqualTo(true);
    String correctSecondPart =
        "ad>\n"
            + "  <script type=\"text/javascript\"> Test </script>\n"
            + "  <meta charset=\"UTF-8\">\n"
            + "  <title>Title</title>\n"
            + "</head>\n"
            + "<body>\n"
            + "\n"
            + "</body>\n"
            + "</html>";
    writer.flush();
    result = writer.toString();
    assertThat(result).isEqualTo(correctSecondPart);
  }
}
