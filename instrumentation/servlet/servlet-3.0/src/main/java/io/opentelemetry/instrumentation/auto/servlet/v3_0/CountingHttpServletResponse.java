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

package io.opentelemetry.instrumentation.auto.servlet.v3_0;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
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
      contentLength += printWriter.counter;
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
    private static final MethodHandle IS_READY_HANDLE;
    private static final MethodHandle SET_WRITE_LISTENER_HANDLE;

    static {
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();

      MethodHandle isReadyHandle;
      try {
        isReadyHandle =
            lookup.findVirtual(
                ServletOutputStream.class, "isReady", MethodType.methodType(boolean.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // servlet-api 3.0 does not have that method
        isReadyHandle = null;
      }

      MethodHandle setWriteListenerHandle;
      try {
        setWriteListenerHandle =
            lookup.findVirtual(
                ServletOutputStream.class,
                "setWriteListener",
                MethodType.methodType(void.class, WriteListener.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        // servlet-api 3.0 does not have that method
        setWriteListenerHandle = null;
      }

      IS_READY_HANDLE = isReadyHandle;
      SET_WRITE_LISTENER_HANDLE = setWriteListenerHandle;
    }

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

    // New abstract methods introduced in servlet-api 3.1, 3.0 does not have them.

    // @Override
    public boolean isReady() {
      if (IS_READY_HANDLE != null) {
        try {
          return (boolean) IS_READY_HANDLE.invoke(delegate);
        } catch (Error | RuntimeException e) {
          throw e;
        } catch (Throwable ignored) {
        }
      }
      return false;
    }

    // @Override
    public void setWriteListener(WriteListener writeListener) {
      if (SET_WRITE_LISTENER_HANDLE != null) {
        try {
          SET_WRITE_LISTENER_HANDLE.invoke(delegate, writeListener);
        } catch (Error | RuntimeException e) {
          throw e;
        } catch (Throwable ignored) {
        }
      }
    }
  }

  static class CountingPrintWriter extends PrintWriter {

    private int counter = 0;

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
      counter++;
    }

    @Override
    public void write(char[] buf, int off, int len) {
      super.write(buf, off, len);
      counter += len;
    }

    @Override
    public void write(String s, int off, int len) {
      super.write(s, off, len);
      counter += len;
    }
  }
}
