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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Method;

/**
 * We need to flush this so that it flushes its own buffer to the underlying stream. In Tomcat the
 * when a jsp is dispatched unless the buffer is full (8093 bytes by default) and flushed
 * automatically the jsp content wasn't getting flushed. So this is a workaround to flush for every
 * write. Note that this only flushes OutputStreamWriter's local buffer and not the underlying
 * stream
 */
public class FlushingOutputStreamWriter extends OutputStreamWriter {

  private Method m_flushBuffer;

  public FlushingOutputStreamWriter(OutputStream out, String charsetName)
      throws UnsupportedEncodingException {
    super(out, charsetName);
  }

  public void write(int c) throws IOException {
    super.write(c);
    flushBuffer();
  }

  public void write(char cbuf[], int off, int len) throws IOException {
    super.write(cbuf, off, len);
    flushBuffer();
  }

  public void write(String str, int off, int len) throws IOException {
    super.write(str, off, len);
    flushBuffer();
  }

  public Writer append(CharSequence csq) throws IOException {
    Writer writer = super.append(csq);
    flushBuffer();
    return writer;
  }

  private void flushBuffer() {
    try {
      if (m_flushBuffer == null) {
        m_flushBuffer = getClass().getSuperclass().getDeclaredMethod("flushBuffer");
        m_flushBuffer.setAccessible(true);
      }
      m_flushBuffer.invoke(this);
    } catch (Exception e) {
      System.out.printf("Error flushing buffer %s\n", e);
    }
  }
}
