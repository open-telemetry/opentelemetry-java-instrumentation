/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;

public class InputStreamUrlConnection extends URLConnection {
  private final InputStream inputStream;
  private final long contentLength;

  public InputStreamUrlConnection(URL url, InputStream inputStream, long contentLength) {
    super(url);
    this.inputStream = inputStream;
    this.contentLength = contentLength;
  }

  @Override
  public void connect() {
    connected = true;
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }

  @Override
  public Permission getPermission() {
    // No permissions needed because all classes are in memory
    return null;
  }

  @Override
  public long getContentLengthLong() {
    return contentLength;
  }
}
