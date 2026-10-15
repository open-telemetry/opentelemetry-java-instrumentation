/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import javax.annotation.Nullable;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;

/**
 * A request that pekko-http rejected while parsing it. Pekko HTTP 1.x tells the parsing error
 * handler nothing about the rejected request, so only the {@code ErrorInfo} is known. Pekko HTTP
 * 2.0 also reports what was read of the request before it was rejected, and any part of it may be
 * missing because the request was rejected before that part was read.
 */
class PekkoHttpParsingErrorRequest {

  private final ErrorInfo info;
  @Nullable private final String method;
  @Nullable private final String rawRequestTarget;
  @Nullable private final String protocol;

  PekkoHttpParsingErrorRequest(
      ErrorInfo info,
      @Nullable String method,
      @Nullable String rawRequestTarget,
      @Nullable String protocol) {
    this.info = info;
    this.method = method;
    this.rawRequestTarget = rawRequestTarget;
    this.protocol = protocol;
  }

  ErrorInfo info() {
    return info;
  }

  @Nullable
  String method() {
    return method;
  }

  /**
   * The request-target exactly as the client sent it. It is malformed whenever it is what caused
   * the rejection.
   */
  @Nullable
  String rawRequestTarget() {
    return rawRequestTarget;
  }

  /** The protocol as pekko-http reports it, for example {@code HTTP/1.1}. */
  @Nullable
  String protocol() {
    return protocol;
  }
}
