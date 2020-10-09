/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.servlet.v2_2;

import javax.servlet.http.HttpServletResponse;

public class ResponseWithStatus {

  private final HttpServletResponse response;
  private final int status;

  public ResponseWithStatus(HttpServletResponse response, int status) {
    this.response = response;
    this.status = status;
  }

  HttpServletResponse getResponse() {
    return response;
  }

  int getStatus() {
    return status;
  }
}
