/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import javax.annotation.Nullable;

/**
 * A request that pekko-http rejected while parsing it. Pekko HTTP 1.x tells the parsing error
 * handler nothing about the rejected request, so nothing is known of it. Pekko HTTP 2.0 reports
 * what was read of the request before it was rejected, and any part of it may be missing because
 * the request was rejected before that part was read.
 */
class PekkoHttpParsingErrorRequest {

  @Nullable private final String method;
  @Nullable private final String rawRequestTarget;
  @Nullable private final String protocol;

  PekkoHttpParsingErrorRequest(
      @Nullable String method, @Nullable String rawRequestTarget, @Nullable String protocol) {
    this.method = method;
    this.rawRequestTarget = rawRequestTarget;
    this.protocol = protocol;
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
