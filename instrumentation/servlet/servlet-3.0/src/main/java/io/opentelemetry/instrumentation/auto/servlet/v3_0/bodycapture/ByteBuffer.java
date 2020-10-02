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

import java.nio.charset.Charset;
import java.util.Arrays;

public class ByteBuffer {

  private static final int MIN_BUFFER_SIZE = 128;
  private static final int MAX_BUFFER_SIZE = 1048576; // 1MB
  private static final int GROW_FACTOR = 4;
  private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
  private Charset charset;
  private byte[] buffer;
  private int bufferLen;

  public synchronized void appendData(byte[] bytes, int start, int end) {
    int newDataLen = end - start;
    if (resizeIfNeeded(newDataLen)) {
      int lenToCopy = Math.min(newDataLen, this.buffer.length - this.bufferLen);
      System.arraycopy(bytes, start, this.buffer, this.bufferLen, lenToCopy);
      this.bufferLen += lenToCopy;
    }
  }

  private boolean resizeIfNeeded(int newDataLen) {
    int newSize;
    if (this.buffer == null) {
      newSize = Math.max(Math.min(newDataLen, MAX_BUFFER_SIZE), MIN_BUFFER_SIZE);
      this.buffer = new byte[newSize];
    } else {
      if (this.bufferLen == MAX_BUFFER_SIZE) {
        return false;
      }

      if (this.buffer.length - this.bufferLen < newDataLen) {
        newSize =
            Math.min(
                Math.max(this.bufferLen + newDataLen, this.bufferLen * GROW_FACTOR),
                MAX_BUFFER_SIZE);
        this.buffer = Arrays.copyOf(this.buffer, newSize);
      }
    }

    return true;
  }

  public synchronized void appendData(int byteValue) {
    if (byteValue >= 0 && byteValue <= 255) {
      if (this.resizeIfNeeded(1)) {
        this.buffer[this.bufferLen] = (byte) byteValue;
        ++this.bufferLen;
      }
    }
  }

  public synchronized void setCharset(Charset charset) {
    this.charset = charset;
  }

  public synchronized String getBufferAsString() {
    if (this.bufferLen == 0) {
      return null;
    } else if (this.charset != null) {
      return new String(this.buffer, 0, this.bufferLen, this.charset);
    } else {
      // fallback to use ISO_8859_1
      return new String(this.buffer, 0, this.bufferLen, ISO_8859_1);
    }
  }
}
