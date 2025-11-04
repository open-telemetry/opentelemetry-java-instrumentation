/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.common;

import com.ning.http.client.Request;
import javax.annotation.Nullable;

/**
 * Helper interface to abstract away differences between AsyncHttpClient versions.
 * This allows sharing common instrumentation code while handling version-specific API differences.
 */
public interface AsyncHttpClientHelper {

  /**
   * Get the full URL from the request.
   * 
   * @param request the HTTP request
   * @return the full URL as a string, or null if it cannot be determined
   */
  @Nullable
  String getUrlFull(Request request);

  /**
   * Get the server address (host) from the request.
   * 
   * @param request the HTTP request
   * @return the server address
   */
  String getServerAddress(Request request);

  /**
   * Get the server port from the request.
   * 
   * @param request the HTTP request
   * @return the server port
   */
  Integer getServerPort(Request request);

  /**
   * Set a header on the request.
   * 
   * @param request the HTTP request
   * @param key the header name
   * @param value the header value
   */
  void setHeader(Request request, String key, String value);
}