/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.oracleucp.v11_2.OracleUcpTelemetry;

public final class OracleUcpSingletons {

  private static final OracleUcpTelemetry oracleUcpTelemetry =
      OracleUcpTelemetry.create(GlobalOpenTelemetry.get());

  public static OracleUcpTelemetry telemetry() {
    return oracleUcpTelemetry;
  }

  private OracleUcpSingletons() {}
}
