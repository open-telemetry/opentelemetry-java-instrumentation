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

package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/** HttpServletResponseWrapper since servlet 2.3, not applicable to 2.2 */
public class CountingHttpServletResponse extends HttpServletResponseWrapper {
  private CountingServletOutputStream outputStream = null;
  private CountingPrintWriter printWriter = null;
  private int errorLength = 0;

  /**
   * Constructs a response adaptor wrapping the given response.
   *
   * @throws IllegalArgumentException if the response is null
   */
  public CountingHttpServletResponse(HttpServletResponse response) {
    super(response);
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    if (outputStream == null) {
      outputStream = new CountingServletOutputStream(super.getOutputStream());
    }
    return outputStream;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (printWriter == null) {
      printWriter = new CountingPrintWriter(super.getWriter());
    }
    return printWriter;
  }

  public int getContentLength() {
    int contentLength = errorLength;
    if (outputStream != null) {
      contentLength += outputStream.counter;
    }
    if (printWriter != null) {
      contentLength += printWriter.counter.get();
    }
    return contentLength;
  }

  /** sendError bypasses the servlet response writers and writes directly to the response */
  @Override
  public void sendError(int sc, String msg) throws IOException {
    super.sendError(sc, msg);
    if (msg != null) {
      errorLength += msg.length();
    }
  }

  static class CountingServletOutputStream extends ServletOutputStream {

    private final ServletOutputStream delegate;
    private int counter = 0;

    public CountingServletOutputStream(ServletOutputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
      delegate.write(b);
      counter++;
    }

    @Override
    public void write(byte[] b) throws IOException {
      delegate.write(b);
      counter += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      delegate.write(b, off, len);
      counter += len;
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  static class CountingPrintWriter extends PrintWriter {
    // PrintWriter is synchronised, so the counter has to be atomic
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * write(String s) and write(char[] buf) are not overridden because they delegate to another
     * write function which would result in their write being counted twice.
     */
    public CountingPrintWriter(Writer out) {
      super(out);
    }

    @Override
    public void write(int c) {
      super.write(c);
      counter.incrementAndGet();
    }

    @Override
    public void write(char[] buf, int off, int len) {
      super.write(buf, off, len);
      counter.addAndGet(len);
    }

    @Override
    public void write(String s, int off, int len) {
      super.write(s, off, len);
      counter.addAndGet(len);
    }
  }
}
