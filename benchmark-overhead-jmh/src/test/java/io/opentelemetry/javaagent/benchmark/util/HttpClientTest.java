/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;

public class HttpClientTest {

  @Test
  public void testCombinations() throws IOException {
    byte[] buffer = new byte[8192];
    String response = "HTTP/1.1 200 OK\r\nContent-Length: 12\r\n\r\n123456789012";
    for (int i = 1; i < response.length() - 1; i++) {
      String part1 = response.substring(0, i);
      String part2 = response.substring(i);
      List<byte[]> chunks = new ArrayList<>();
      chunks.add(part1.getBytes());
      chunks.add(part2.getBytes());
      InputStream in = new TestInputStream(chunks);
      HttpClient.drain(in, buffer);
      String string = new String(buffer, 0, response.length());
      assertThat(string).isEqualTo(response);
    }
  }

  private static class TestInputStream extends InputStream {

    private final Iterator<byte[]> chunks;

    private TestInputStream(List<byte[]> chunks) {
      this.chunks = chunks.iterator();
    }

    @Override
    public int read() {
      return 0;
    }

    @Override
    public int read(byte[] buffer, int off, int len) {
      if (!chunks.hasNext()) {
        return -1;
      }
      byte[] next = chunks.next();
      System.arraycopy(next, 0, buffer, off, next.length);
      return next.length;
    }
  }
}
