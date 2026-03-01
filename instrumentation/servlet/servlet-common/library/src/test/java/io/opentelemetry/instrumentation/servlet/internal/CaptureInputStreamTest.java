/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CaptureInputStreamTest {

  @Test
  void empty() throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
    ByteBuffer buffer = ByteBuffer.allocate(10);
    CaptureInputStream capture = new CaptureInputStream(input, buffer);

    assertThat(capture.read()).isEqualTo(-1);
    assertThat(buffer.position()).isEqualTo(0);
  }

  @Test
  void empty_throwsException() throws IOException {
    InputStream input = mock(InputStream.class);
    doThrow(new IOException()).when(input).read();

    ByteBuffer buffer = ByteBuffer.allocate(10);
    CaptureInputStream capture = new CaptureInputStream(input, buffer);

    assertThatThrownBy(capture::read).isInstanceOf(IOException.class);
    flipAndCheckBuffer(buffer, new byte[0]);
  }

  enum ReadStrategy {
    BYTE_BY_BYTE {
      @Override
      public void readWhole(InputStream input, byte[] expectedData) throws IOException {
        int value;
        int index = 0;
        do {
          value = input.read();
          if (value >= 0) {
            assertThat(value).isEqualTo(expectedData[index]);
            index++;
          }
        } while (value != -1);
      }

      @Override
      public void readUntilException(InputStream input, int maxLength) throws IOException {
        int read;
        do {
          read = input.read();
        } while (read > 0);
      }
    },
    WHOLE_ARRAY {
      @Override
      public void readWhole(InputStream input, byte[] expectedData) throws IOException {
        byte[] readBytes = new byte[expectedData.length];
        int read = input.read(readBytes);
        assertThat(read).isEqualTo(readBytes.length);
        assertThat(readBytes).isEqualTo(expectedData);
      }

      @Override
      public void readUntilException(InputStream input, int maxLength) throws IOException {
        input.read(new byte[maxLength]);
      }
    },
    ARRAY_IN_CHUNKS {
      @Override
      public void readWhole(InputStream input, byte[] expectedData) throws IOException {
        int chunkSize = 3;
        int left = expectedData.length;
        byte[] chunk = new byte[chunkSize];
        int read;
        do {
          read = input.read(chunk, 0, chunkSize);
          if (read > 0) {
            left -= read;
          }
        } while (read >= 0);
        assertThat(left).isZero();
      }

      @Override
      public void readUntilException(InputStream input, int maxLength) throws IOException {
        int chunkSize = 3;
        byte[] chunk = new byte[chunkSize];
        int left = maxLength;
        while (left > 0) {
          int read = input.read(chunk, 0, chunkSize);
          left -= read;
        }
      }
    };

    public abstract void readWhole(InputStream input, byte[] expectedData) throws IOException;

    public abstract void readUntilException(InputStream input, int maxLength) throws IOException;
  }

  @ParameterizedTest
  @EnumSource(ReadStrategy.class)
  void capture_wholeInput(ReadStrategy strategy) throws IOException {
    byte[] inputBytes = byteArray(10);
    ByteArrayInputStream input = new ByteArrayInputStream(inputBytes);

    ByteBuffer buffer = ByteBuffer.allocate(20);
    CaptureInputStream capture = new CaptureInputStream(input, buffer);

    strategy.readWhole(capture, inputBytes);

    assertThat(capture.read()).isEqualTo(-1);
    assertThat(capture.available()).isZero();

    flipAndCheckBuffer(buffer, inputBytes);
  }

  @ParameterizedTest
  @EnumSource(ReadStrategy.class)
  void capture_truncatedInput(ReadStrategy strategy) throws IOException {
    byte[] inputBytes = byteArray(10);
    ByteArrayInputStream input = new ByteArrayInputStream(inputBytes);

    ByteBuffer buffer = ByteBuffer.allocate(5);
    CaptureInputStream capture = new CaptureInputStream(input, buffer);

    strategy.readWhole(capture, inputBytes);

    assertThat(capture.read()).isEqualTo(-1);
    assertThat(capture.available()).isZero();

    flipAndCheckBuffer(buffer, Arrays.copyOf(inputBytes, 5));
  }

  @ParameterizedTest
  @EnumSource(ReadStrategy.class)
  void capture_untilException(ReadStrategy strategy) throws IOException {
    InputStream input = exceptionInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7}, 7);

    ByteBuffer buffer = ByteBuffer.allocate(10);
    CaptureInputStream capture = new CaptureInputStream(input, buffer);

    assertThatThrownBy(() -> strategy.readUntilException(capture, 8))
        .isInstanceOf(IOException.class);

    switch (strategy) {
      case BYTE_BY_BYTE:
        flipAndCheckBuffer(buffer, new byte[] {1, 2, 3, 4, 5, 6, 7});
        break;
      case WHOLE_ARRAY:
        flipAndCheckBuffer(buffer, new byte[0]);
        break;
      case ARRAY_IN_CHUNKS:
        // read in chunks of 3, so we should have two full chunks before exception
        flipAndCheckBuffer(buffer, new byte[] {1, 2, 3, 4, 5, 6});
        break;
    }
  }

  private static void flipAndCheckBuffer(ByteBuffer buffer, byte[] expectedBytes) {
    buffer.flip();
    int size = buffer.limit();
    assertThat(size).isEqualTo(expectedBytes.length);
    if (size > 0) {
      byte[] bufferBytes = new byte[size];
      buffer.get(bufferBytes);
      assertThat(bufferBytes).isEqualTo(expectedBytes);
    }
  }

  private static byte[] byteArray(int size) {
    byte[] array = new byte[size];
    for (int i = 0; i < size; i++) {
      array[i] = (byte) i;
    }
    return array;
  }

  private static InputStream exceptionInputStream(byte[] data, int exceptionIndex) {
    ByteArrayInputStream input = new ByteArrayInputStream(data);
    return new InputStream() {
      private int index = 0;

      @Override
      public int read() throws IOException {
        if (index == exceptionIndex) {
          throw new IOException("intentional io exception");
        }
        int read = input.read();
        if (read >= 0) {
          index++;
        }
        return read;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        if (index <= exceptionIndex && exceptionIndex <= (index + len)) {
          throw new IOException("intentional io exception");
        }
        int read = input.read(b, off, len);
        if (read >= 0) {
          index += read;
        }
        return read;
      }
    };
  }
}
