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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SnippetInjectingResponseWrapperTest {

  @Test
  void testInjectToTextHtml() throws IOException {

    String original = readFile("staticHtmlOrigin.html");
    String correct = readFile("staticHtmlAfter.html");
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getContentType()).thenReturn("text/html");
    when(response.getStatus()).thenReturn(200);
    when(response.containsHeader("content-type")).thenReturn(true);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    SnippetInjectingResponseWrapper responseWrapper =
        new SnippetInjectingResponseWrapper(response, testSnippet);
    responseWrapper.getWriter().write(original);
    responseWrapper.getWriter().flush();
    responseWrapper.getWriter().close();

    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo(correct);
  }

  @Test
  @Disabled
  void testInjectToChineseTextHtml() throws IOException {

    String original = readFile("staticHtmlChineseOrigin.html");
    String correct = readFile("staticHtmlChineseAfter.html");
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getContentType()).thenReturn("text/html");

    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    SnippetInjectingResponseWrapper responseWrapper =
        new SnippetInjectingResponseWrapper(response, testSnippet);
    responseWrapper.getWriter().write(original);
    responseWrapper.getWriter().flush();
    responseWrapper.getWriter().close();

    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo(correct);
  }

  @Test
  void shouldNotInjectToTextHtml() throws IOException {

    String original = readFile("staticHtmlOrigin.html");

    StringWriter writer = new StringWriter();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getContentType()).thenReturn("not/text");
    when(response.getStatus()).thenReturn(200);
    when(response.containsHeader("content-type")).thenReturn(true);

    when(response.getWriter()).thenReturn(new PrintWriter(writer, true));

    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    SnippetInjectingResponseWrapper responseWrapper =
        new SnippetInjectingResponseWrapper(response, testSnippet);
    responseWrapper.getWriter().write(original);
    responseWrapper.getWriter().flush();
    responseWrapper.getWriter().close();

    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo(original);
  }

  @Test
  void testWriteInt() throws IOException {

    String original = readFile("staticHtmlOrigin.html");
    String correct = readFile("staticHtmlAfter.html");
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getContentType()).thenReturn("text/html");

    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    SnippetInjectingResponseWrapper responseWrapper =
        new SnippetInjectingResponseWrapper(response, testSnippet);
    byte[] originalBytes = original.getBytes(Charset.defaultCharset());
    for (byte originalByte : originalBytes) {
      responseWrapper.getWriter().write(originalByte);
    }

    responseWrapper.getWriter().flush();
    responseWrapper.getWriter().close();

    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo(correct);
  }

  @Test
  void testWriteCharArray() throws IOException {

    String original = readFile("staticHtmlChineseOrigin.html");
    String correct = readFile("staticHtmlChineseAfter.html");
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getContentType()).thenReturn("text/html");
    when(response.getStatus()).thenReturn(200);
    when(response.containsHeader("content-type")).thenReturn(true);

    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    SnippetInjectingResponseWrapper responseWrapper =
        new SnippetInjectingResponseWrapper(response, testSnippet);
    char[] originalChars = original.toCharArray();
    responseWrapper.getWriter().write(originalChars, 0, originalChars.length);
    responseWrapper.getWriter().flush();
    responseWrapper.getWriter().close();

    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo(correct);
  }

  @Test
  void testWriteWithOffset() throws IOException {

    String original = readFile("staticHtmlChineseOrigin.html");
    String correct = readFile("staticHtmlChineseAfter.html");
    String extraBuffer = "this buffer should not be print out";
    original = extraBuffer + original;
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getContentType()).thenReturn("text/html");
    when(response.getStatus()).thenReturn(200);
    when(response.containsHeader("content-type")).thenReturn(true);

    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    String testSnippet = "\n  <script type=\"text/javascript\"> Test </script>";
    SnippetInjectingResponseWrapper responseWrapper =
        new SnippetInjectingResponseWrapper(response, testSnippet);

    responseWrapper
        .getWriter()
        .write(original, extraBuffer.length(), original.length() - extraBuffer.length());
    responseWrapper.getWriter().flush();
    responseWrapper.getWriter().close();

    String result = writer.toString();
    writer.close();
    assertThat(result).isEqualTo(correct);
  }
}
