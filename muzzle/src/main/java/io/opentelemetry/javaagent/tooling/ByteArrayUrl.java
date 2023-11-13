/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.security.PrivilegedAction;

public class ByteArrayUrl {

  private ByteArrayUrl() {}

  private static final String URL_SCHEMA = "x-otel-binary";

  @SuppressWarnings("removal")
  public static URL create(String contentName, byte[] data) {
    if (System.getSecurityManager() != null) {
      return java.security.AccessController.doPrivileged(
          (PrivilegedAction<URL>)
              () -> {
                return doCreate(contentName, data);
              });
    } else {
      return doCreate(contentName, data);
    }
  }

  private static URL doCreate(String contentName, byte[] data) {
    try {
      String file = URLEncoder.encode(contentName, StandardCharsets.UTF_8.toString());
      return new URL(URL_SCHEMA, null, -1, file, new ByteArrayUrlStreamHandler(data));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Failed to generate URL for the provided arguments", e);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * This class is based on ByteBuddy {@link
   * net.bytebuddy.dynamic.loading.ByteArrayClassLoader.PersistenceHandler}.
   */
  private static class ByteArrayUrlStreamHandler extends URLStreamHandler {

    /** The binary representation of a type's class file. */
    private final byte[] binaryRepresentation;

    /**
     * Creates a new byte array URL stream handler.
     *
     * @param binaryRepresentation The binary representation of a type's class file.
     */
    private ByteArrayUrlStreamHandler(byte[] binaryRepresentation) {
      this.binaryRepresentation = binaryRepresentation;
    }

    @Override
    protected URLConnection openConnection(URL url) {
      return new ByteArrayUrlConnection(url);
    }

    private class ByteArrayUrlConnection extends URLConnection {

      private final InputStream inputStream;

      protected ByteArrayUrlConnection(URL url) {
        super(url);
        inputStream = new ByteArrayInputStream(binaryRepresentation);
      }

      @Override
      public void connect() {
        connected = true;
      }

      @Override
      public InputStream getInputStream() {
        connect(); // Mimics the semantics of an actual URL connection.
        return inputStream;
      }

      @Override
      public Permission getPermission() {
        return null;
      }

      @Override
      public long getContentLengthLong() {
        return binaryRepresentation.length;
      }
    }
  }
}
