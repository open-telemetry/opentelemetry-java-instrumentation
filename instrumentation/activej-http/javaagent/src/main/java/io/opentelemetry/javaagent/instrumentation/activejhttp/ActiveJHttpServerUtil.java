/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import io.activej.http.HttpError;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * This utility class provides helper methods for extracting and manipulating HTTP-related
 * attributes from ActiveJ {@code HttpRequest} and {@code HttpResponse} objects. It is designed to
 * simplify the process of retrieving information such as HTTP methods, headers, status codes, and
 * other metadata, which are essential for OpenTelemetry instrumentation and distributed tracing.
 *
 * @author Krishna Chaitanya Surapaneni
 */
public final class ActiveJHttpServerUtil {

  private ActiveJHttpServerUtil() {
    throw new UnsupportedOperationException();
  }

  static String getHttpRequestMethod(HttpRequest request) {
    return request.getMethod().name();
  }

  static List<String> requestHeader(HttpRequest request, String name) {
    String headerValue = request.getHeader(HttpHeaders.of(name));
    if (headerValue == null) {
      return Collections.emptyList();
    }
    return Arrays.stream(headerValue.split(",")).collect(Collectors.toList());
  }

  static Integer getHttpResponseStatusCode(
      HttpRequest request, HttpResponse httpResponse, @Nullable Throwable error) {
    if (error != null && httpResponse.getCode() <= 0) {
      return HttpError.internalServerError500().getCode();
    }
    return httpResponse.getCode();
  }

  static List<String> getHttpResponseHeader(
      HttpRequest request, HttpResponse httpResponse, String name) {
    String headerValue = httpResponse.getHeader(HttpHeaders.of(name));
    if (headerValue == null) {
      return Collections.emptyList();
    }
    return Arrays.stream(headerValue.split(",")).collect(Collectors.toList());
  }
}
