/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.servlet.v3_0.bodycapture;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class BufferingHttpServletResponse extends HttpServletResponseWrapper {

  private ServletOutputStream outputStream;
  private PrintWriter writer;
  private BufferingServletOutputStream bufferingServletOutputStream;
  private Map<String, List<String>> headers = new LinkedHashMap<>();

  public BufferingHttpServletResponse(HttpServletResponse httpServletResponse) {
    super(httpServletResponse);
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if (writer != null) {
      throw new IllegalStateException("getWriter() has already been called on this response.");
    }

    if (outputStream == null) {
      outputStream = getResponse().getOutputStream();
      bufferingServletOutputStream = new BufferingServletOutputStream(outputStream);
    }

    return bufferingServletOutputStream;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (outputStream != null) {
      throw new IllegalStateException(
          "getOutputStream() has already been called on this response.");
    }

    if (writer == null) {
      bufferingServletOutputStream =
          new BufferingServletOutputStream(getResponse().getOutputStream());
      writer =
          new PrintWriter(
              new FlushingOutputStreamWriter(
                  bufferingServletOutputStream, getResponse().getCharacterEncoding()),
              false);
    }

    return writer;
  }

  @Override
  public void flushBuffer() throws IOException {
    if (writer != null) {
      writer.flush();
    } else if (outputStream != null) {
      bufferingServletOutputStream.flush();
    }
  }

  public void setDateHeader(String name, long date) {
    super.setDateHeader(name, date);
    safelyCaptureHeader(name, date);
  }

  public void addDateHeader(String name, long date) {
    super.addDateHeader(name, date);
    safelyCaptureHeader(name, date);
  }

  public void setHeader(String name, String value) {
    super.setHeader(name, value);
    safelyCaptureHeader(name, value);
  }

  public void addHeader(String name, String value) {
    super.addHeader(name, value);
    safelyCaptureHeader(name, value);
  }

  public void setIntHeader(String name, int value) {
    super.setIntHeader(name, value);
    safelyCaptureHeader(name, value);
  }

  public void addIntHeader(String name, int value) {
    super.addIntHeader(name, value);
    safelyCaptureHeader(name, value);
  }

  public void addCookie(Cookie cookie) {
    super.addCookie(cookie);
    safelyAddCookieHeader(cookie);
  }

  private void safelyAddCookieHeader(Cookie cookie) {
    try {
      HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
      httpCookie.setComment(cookie.getComment());
      httpCookie.setDomain(cookie.getDomain());
      httpCookie.setPath(cookie.getPath());
      httpCookie.setMaxAge(cookie.getMaxAge());
      httpCookie.setVersion(cookie.getVersion());
      httpCookie.setHttpOnly(cookie.isHttpOnly());
      httpCookie.setSecure(cookie.getSecure());
      String headerValue = httpCookie.toString();
      List<String> values = new ArrayList<>(1);
      values.add(headerValue);
      headers.put("Set-Cookie", values);
    } catch (Exception e) {
      System.out.printf("Error capturing cookie - ", e);
    }
  }

  private void safelyCaptureHeader(String name, Object value) {
    try {
      List<String> values = headers.getOrDefault(name, new ArrayList<>());
      values.add(String.valueOf(value));
      headers.put(name, values);
    } catch (Exception e) {
      System.out.printf("Error capturing header - ", e);
    }
  }

  public Map<String, List<String>> getBufferedHeaders() {
    return headers;
  }

  public String getBufferAsString() {
    if (bufferingServletOutputStream != null) {
      return bufferingServletOutputStream.getBufferAsString();
    } else {
      return null;
    }
  }
}
