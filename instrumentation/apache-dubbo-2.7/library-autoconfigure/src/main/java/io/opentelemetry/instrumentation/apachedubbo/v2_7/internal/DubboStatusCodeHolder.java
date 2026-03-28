/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

/**
 * Holds Dubbo response status code information. Used with VirtualField to attach status code data
 * to RpcInvocation objects.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboStatusCodeHolder {

  private final String statusCode;
  private final boolean serverError;

  public DubboStatusCodeHolder(String statusCode, boolean serverError) {
    this.statusCode = statusCode;
    this.serverError = serverError;
  }

  public String getStatusCode() {
    return statusCode;
  }

  /** Whether this status code is considered a server-side error per semantic conventions. */
  public boolean isServerError() {
    return serverError;
  }
}
