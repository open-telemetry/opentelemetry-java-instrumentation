/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.bootstrap.servlet.InjectionState;
import io.opentelemetry.javaagent.instrumentation.servlet.common.snippet.OutputStreamSnippetInjectionHelper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LateContentTypeTest {
  private static final String SNIPPET = "<script>test</script>";

  @ParameterizedTest
  @EnumSource(Output.class)
  void injectsAfterAcquisitionBeforeContentType(Output output) throws IOException {
    for (String contentType :
        new String[] {"text/html", "text/html;charset=UTF-8", "application/xhtml+xml"}) {
      Fixture fixture = new Fixture(output);
      fixture.wrapper.setContentType("text/plain");
      fixture.acquire();
      fixture.wrapper.setContentType(contentType);
      fixture.write("<he");
      fixture.write("ad>你好</head><head>");
      assertThat(fixture.content()).isEqualTo("<head>" + SNIPPET + "你好</head><head>");
    }
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void injectsWithContentTypeHeader(Output output) throws IOException {
    Fixture fixture = new Fixture(output);
    fixture.wrapper.setHeader("Content-Type", "text/html");
    fixture.write("<head>");
    assertThat(fixture.content()).isEqualTo("<head>" + SNIPPET);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void leavesNonHtmlUnchanged(Output output) throws IOException {
    Fixture fixture = new Fixture(output);
    fixture.wrapper.setHeader("Content-Length", "12");
    fixture.write("<head>");
    fixture.wrapper.setContentType("text/plain");
    fixture.write("<head>");
    assertThat(fixture.content()).isEqualTo("<head><head>");
    verify(fixture.response, never()).setContentLength(12 + SNIPPET.length());
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void usesCurrentContentTypeBeforeWriting(Output output) throws IOException {
    Fixture fixture = new Fixture(output);
    fixture.wrapper.setContentType("text/html");
    fixture.acquire();
    fixture.wrapper.setContentType("text/plain");
    fixture.write("<head>");
    fixture.wrapper.setContentType("text/html");
    fixture.write("<head>");
    assertThat(fixture.content()).isEqualTo("<head><head>" + SNIPPET);
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void doesNotMatchHeadWrittenBeforeHtmlType(Output output) throws IOException {
    Fixture fixture = new Fixture(output);
    fixture.write("<he");
    fixture.wrapper.setContentType("text/html");
    fixture.write("ad>");
    assertThat(fixture.content()).isEqualTo("<head>");
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void clearsPartialMatchOnNonHtmlWrites(Output output) throws IOException {
    Fixture fixture = new Fixture(output);
    fixture.wrapper.setContentType("text/html");
    fixture.write("<he");
    fixture.wrapper.setContentType("text/plain");
    fixture.write("ignored");
    fixture.wrapper.setContentType("text/html");
    fixture.write("ad><head>");
    assertThat(fixture.content()).isEqualTo("<heignoredad><head>" + SNIPPET);
  }

  @Test
  void matchesHeadAcrossDelegatedBulkWrites() throws IOException {
    Fixture fixture = new Fixture(Output.STREAM);
    fixture.wrapper.setContentType("text/html");
    byte[] prefix = "<he".getBytes(UTF_8);
    InjectionState state = ServletOutputStreamInjectionState.getInjectionState(fixture.stream);

    assertThat(fixture.helper.handleWrite(state, fixture.stream, prefix, 0, prefix.length))
        .isFalse();
    assertThat(fixture.helper.handleWrite(state, fixture.stream, prefix, 0, prefix.length))
        .isFalse();
    fixture.stream.write(prefix);
    fixture.write("ad>late<head>");

    assertThat(fixture.content()).isEqualTo("<head>" + SNIPPET + "late<head>");
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void doesNotInjectTwiceAfterTypeChangesAndReacquisition(Output output) throws IOException {
    Fixture fixture = new Fixture(output);
    fixture.wrapper.setContentType("text/html");
    fixture.write("<head>");
    fixture.wrapper.setContentType("text/plain");
    fixture.acquire();
    fixture.write("<head>");
    fixture.wrapper.setContentType("text/html");
    fixture.acquire();
    fixture.write("<head>");
    assertThat(fixture.content()).isEqualTo("<head>" + SNIPPET + "<head><head>");
  }

  @Test
  void updatesContentLengthSetBeforeContentType() throws IOException {
    for (String setter :
        new String[] {"length", "header", "addHeader", "intHeader", "addIntHeader"}) {
      Fixture fixture = new Fixture(Output.STREAM);
      fixture.setContentLength(setter);
      fixture.wrapper.setContentType("text/html");
      fixture.write("<head>");
      assertThat(fixture.content()).isEqualTo("<head>" + SNIPPET);
      verify(fixture.response).setContentLength(6 + SNIPPET.length());
    }
  }

  @ParameterizedTest
  @EnumSource(Output.class)
  void doesNotInjectIntoCommittedResponseWithContentLength(Output output) throws IOException {
    Fixture fixture = new Fixture(output);
    fixture.wrapper.setHeader("Content-Length", "6");
    fixture.wrapper.setContentType("text/html");
    when(fixture.response.isCommitted()).thenReturn(true);
    fixture.write("<head>");
    assertThat(fixture.content()).isEqualTo("<head>");
    verify(fixture.response, never()).setContentLength(6 + SNIPPET.length());
  }

  @Test
  void bulkWriterWritesCheckContentTypeOnce() throws IOException {
    char[] chars = new char[10_000];
    Arrays.fill(chars, 'x');
    String content = new String(chars);

    Fixture fixture = new Fixture(Output.WRITER);
    fixture.wrapper.setContentType("text/html");
    fixture.writer.write(content);
    assertThat(fixture.content()).isEqualTo(content);
    verify(fixture.response, times(1)).getContentType();

    fixture.writer.write(chars);
    assertThat(fixture.content()).isEqualTo(content + content);
    verify(fixture.response, times(2)).getContentType();
  }

  @Test
  void standaloneWriterWritesCheckContentTypeForEachCharacter() throws IOException {
    Fixture fixture = new Fixture(Output.WRITER);
    fixture.wrapper.setContentType("text/html");

    for (char c : "<head>".toCharArray()) {
      fixture.writer.write(c);
    }

    assertThat(fixture.content()).isEqualTo("<head>" + SNIPPET);
    verify(fixture.response, times(6)).getContentType();
  }

  @Test
  void singleByteWritesFallBackAndInjectAfterLateContentType() throws IOException {
    Fixture fixture = new Fixture(Output.STREAM);
    fixture.wrapper.setContentType("text/plain");
    InjectionState state = ServletOutputStreamInjectionState.getInjectionState(fixture.stream);
    for (byte b : "plain".getBytes(UTF_8)) {
      if (!fixture.helper.handleWrite(state, fixture.stream, b)) {
        fixture.stream.write(b);
      }
    }
    assertThat(fixture.content()).isEqualTo("plain");

    fixture.wrapper.setContentType("text/html");
    for (byte b : "<head><head>".getBytes(UTF_8)) {
      if (!fixture.helper.handleWrite(state, fixture.stream, b)) {
        fixture.stream.write(b);
      }
    }
    assertThat(fixture.content()).isEqualTo("plain<head>" + SNIPPET + "<head>");
  }

  enum Output {
    STREAM,
    WRITER
  }

  private static class Fixture {
    final HttpServletResponse response = mock(HttpServletResponse.class);
    final Servlet3SnippetInjectingResponseWrapper wrapper =
        new Servlet3SnippetInjectingResponseWrapper(response, SNIPPET);
    final Output output;
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final StringWriter chars = new StringWriter();
    final OutputStreamSnippetInjectionHelper helper =
        new OutputStreamSnippetInjectionHelper(() -> SNIPPET);
    final ServletOutputStream rawStream =
        new ServletOutputStream() {
          @Override
          public void write(int b) {
            bytes.write(b);
          }
        };
    String contentType;
    String contentTypeHeader;
    ServletOutputStream stream;
    PrintWriter writer;

    Fixture(Output output) throws IOException {
      this.output = output;
      when(response.getCharacterEncoding()).thenReturn(UTF_8.name());
      when(response.getContentType()).thenAnswer(invocation -> contentType);
      when(response.getHeader("content-type")).thenAnswer(invocation -> contentTypeHeader);
      doAnswer(
              invocation -> {
                contentType = invocation.getArgument(0);
                return null;
              })
          .when(response)
          .setContentType(anyString());
      doAnswer(
              invocation -> {
                contentTypeHeader = invocation.getArgument(1);
                return null;
              })
          .when(response)
          .setHeader(eq("Content-Type"), anyString());
      when(response.getOutputStream()).thenReturn(rawStream);
      when(response.getWriter()).thenReturn(new PrintWriter(chars));
      acquire();
    }

    void acquire() throws IOException {
      if (output == Output.STREAM) {
        stream = wrapper.getOutputStream();
        assertThat(ServletOutputStreamInjectionState.getInjectionState(stream)).isNotNull();
      } else {
        writer = wrapper.getWriter();
      }
    }

    void write(String value) throws IOException {
      switch (output) {
        case STREAM:
          byte[] data = value.getBytes(UTF_8);
          InjectionState state = ServletOutputStreamInjectionState.getInjectionState(stream);
          if (!helper.handleWrite(state, stream, data, 0, data.length)) {
            stream.write(data);
          }
          break;
        case WRITER:
          writer.write(value);
          break;
      }
    }

    String content() {
      return output == Output.STREAM ? new String(bytes.toByteArray(), UTF_8) : chars.toString();
    }

    void setContentLength(String setter) {
      switch (setter) {
        case "length":
          wrapper.setContentLength(6);
          break;
        case "header":
          wrapper.setHeader("Content-Length", "6");
          break;
        case "addHeader":
          wrapper.addHeader("Content-Length", "6");
          break;
        case "intHeader":
          wrapper.setIntHeader("Content-Length", 6);
          break;
        case "addIntHeader":
          wrapper.addIntHeader("Content-Length", 6);
          break;
        default:
          throw new IllegalArgumentException(setter);
      }
    }
  }
}
