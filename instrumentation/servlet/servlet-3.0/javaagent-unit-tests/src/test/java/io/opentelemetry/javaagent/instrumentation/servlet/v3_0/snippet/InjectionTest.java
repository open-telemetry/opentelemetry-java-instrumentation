/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.TestUtil.readFile;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.servlet.ExperimentalSnippetHolder;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletOutputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class InjectionTest {

  @Test
  void testInjectionForStringContainHeadTag() throws IOException {
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    // read the originalFile
    String original = readFile("staticHtmlOrigin.html");
    // read the correct answer
    String correct = readFile("staticHtmlAfter.html");
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
    boolean injected = helper.handleWrite(originalBytes, 0, originalBytes.length, obj, sp);
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
    // read the originalFile
    String original = readFile("staticHtmlChineseOrigin.html");
    // read the correct answer
    String correct = readFile("staticHtmlChineseAfter.html");
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
    boolean injected = helper.handleWrite(originalBytes, 0, originalBytes.length, obj, sp);
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
    ExperimentalSnippetHolder.setSnippet(testSnippet);
    // read the originalFile
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
    boolean injected = helper.handleWrite(originalBytes, 0, originalBytes.length, obj, sp);
    assertThat(obj.getHeadTagBytesSeen()).isEqualTo(0);
    assertThat(injected).isEqualTo(false);
    writer.flush();
    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo("");
  }

  @Test
  void testHalfHeadTag() throws IOException {
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    // read the original string
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
        helper.handleWrite(originalFirstPartBytes, 0, originalFirstPartBytes.length, obj, sp);

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
        helper.handleWrite(originalSecondPartBytes, 0, originalSecondPartBytes.length, obj, sp);
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
