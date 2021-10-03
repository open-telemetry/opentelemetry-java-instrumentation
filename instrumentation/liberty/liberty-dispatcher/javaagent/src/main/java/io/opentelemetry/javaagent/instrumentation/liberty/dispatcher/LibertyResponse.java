/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import com.ibm.wsspi.http.channel.values.StatusCodes;

public class LibertyResponse {
  private final StatusCodes code;

  public LibertyResponse(StatusCodes code) {
    this.code = code;
  }

  public int getStatus() {
    return code.getIntCode();
  }
}
