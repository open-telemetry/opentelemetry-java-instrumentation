/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/** Utilities for converting {@code file:} URLs to {@link File} instances. */
final class UrlPaths {

  /**
   * Convert a {@code file:} URL to a {@link File}, decoding percent-encoded characters in the path
   * (e.g. {@code %20} -> space).
   */
  static File toFile(URL url) throws IOException {
    try {
      return new File(url.toURI().getSchemeSpecificPart());
    } catch (URISyntaxException e) {
      throw new IOException("Invalid URL: " + url, e);
    }
  }

  /**
   * Convert a {@code file:} URL string to a {@link File}, decoding percent-encoded characters in
   * the path (e.g. {@code %20} -> space).
   */
  static File toFile(String fileUrl) throws IOException {
    try {
      return new File(new URI(fileUrl).getSchemeSpecificPart());
    } catch (URISyntaxException e) {
      throw new IOException("Invalid URL: " + fileUrl, e);
    }
  }

  private UrlPaths() {}
}
