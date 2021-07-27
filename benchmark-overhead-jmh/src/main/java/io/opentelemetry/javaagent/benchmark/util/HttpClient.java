/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

// this minimal http client really helps to keep benchmark run variation to a minimum
public class HttpClient {

  private static final int LOWERCASE_OFFSET = 'a' - 'A';

  private static final byte[] CONTENT_LENGTH_BYTES = "CONTENT-LENGTH:".getBytes(UTF_8);

  private final Socket socket;
  private final OutputStream out;
  private final InputStream in;
  private final byte[] requestBytes;

  private final byte[] buffer = new byte[8192];

  public HttpClient(String host, int port, String path) throws IOException {
    socket = new Socket(host, port);
    out = socket.getOutputStream();
    in = socket.getInputStream();
    String request = "GET " + path + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\n\r\n";
    requestBytes = request.getBytes(UTF_8);
  }

  public void close() throws IOException {
    out.close();
    in.close();
    socket.close();
  }

  public void execute() throws IOException {
    out.write(requestBytes);
    drain(in, buffer);
  }

  // visible for testing
  static void drain(InputStream in, byte[] buffer) throws IOException {
    int startLookingFromIndex = 0;
    int bytesRead = 0;
    int headerLen = 0;
    List<Integer> possibleHeaderPositions = new ArrayList<>(2);
    while (headerLen == 0) {
      bytesRead += in.read(buffer, bytesRead, buffer.length - bytesRead);
      for (int i = startLookingFromIndex; i < bytesRead - 2; i++) {
        if (buffer[i] == '\r') {
          int nextLineStartPosition = i + 2;
          byte b = buffer[nextLineStartPosition];
          if (b == '\r') {
            // found end of headers
            headerLen = nextLineStartPosition + 2;
            break;
          } else if (b == 'C' || b == 'c') {
            possibleHeaderPositions.add(nextLineStartPosition);
          }
        }
      }
      startLookingFromIndex = Math.max(bytesRead - 2, 0);
    }
    int contentLength = getContentLength(buffer, possibleHeaderPositions);
    while (bytesRead < headerLen + contentLength) {
      bytesRead += in.read(buffer, bytesRead, buffer.length - bytesRead);
    }
  }

  private static int getContentLength(byte[] buffer, List<Integer> possibleHeaderPositions) {
    for (int startIndex : possibleHeaderPositions) {
      if (isContentLengthHeader(buffer, startIndex)) {
        String contentLength = getRestOfLine(buffer, startIndex + CONTENT_LENGTH_BYTES.length);
        return Integer.parseInt(contentLength.trim());
      }
    }
    throw new IllegalStateException("Did not find Content-Length header");
  }

  private static boolean isContentLengthHeader(byte[] buffer, int startIndex) {
    for (int i = 0; i < CONTENT_LENGTH_BYTES.length; i++) {
      byte current = buffer[startIndex + i];
      byte expected = CONTENT_LENGTH_BYTES[i];
      if (current != expected && current != expected + LOWERCASE_OFFSET) {
        return false;
      }
    }
    return true;
  }

  private static String getRestOfLine(byte[] buffer, int startIndex) {
    StringBuilder value = new StringBuilder();
    for (int i = startIndex; i < buffer.length; i++) {
      byte b = buffer[i];
      if (b == '\r') {
        break;
      }
      value.append((char) b);
    }
    return value.toString();
  }
}
