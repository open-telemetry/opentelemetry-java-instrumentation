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
import javax.servlet.ServletOutputStream;

public class BufferingServletOutputStream extends ServletOutputStream {

  private OutputStream outputStream;
  private ByteBuffer byteBuffer;

  public BufferingServletOutputStream(OutputStream outputStream) {
    this.outputStream = outputStream;
    byteBuffer = new ByteBuffer();
  }

  public synchronized ByteBuffer getByteBuffer() {
    if (byteBuffer == null) {
      byteBuffer = new ByteBuffer();
    }
    return byteBuffer;
  }

  @Override
  public void write(int b) throws IOException {
    outputStream.write(b);
    try {
      byteBuffer.appendData(b);
    } catch (Exception e) {
      System.out.printf("Error in write(int b) ", e);
    }
  }

  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    outputStream.write(b, off, len);
    try {
      byteBuffer.appendData(b, off, len);
    } catch (Exception e) {
      System.out.printf("Error in write(byte[] b, int off, int len) ", e);
    }
  }

  @Override
  public void flush() throws IOException {
    outputStream.flush();
  }

  @Override
  public void close() throws IOException {
    outputStream.close();
  }

  public String getBufferAsString() {
    return byteBuffer.getBufferAsString();
  }
}
