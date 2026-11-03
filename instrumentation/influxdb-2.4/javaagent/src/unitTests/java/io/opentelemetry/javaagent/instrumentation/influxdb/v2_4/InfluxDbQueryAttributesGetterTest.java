/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class InfluxDbQueryAttributesGetterTest {

  private final InfluxDbQueryAttributesGetter getter = new InfluxDbQueryAttributesGetter();
  private final InfluxDbQuery request = InfluxDbQuery.create("localhost", 8086, "mydb", "SELECT *");

  @Test
  void getErrorType() {
    assertThat(getter.getErrorType(request, null, null)).isNull();
    assertThat(getter.getErrorType(request, new InfluxDbResponse(null), null)).isNull();
    assertThat(getter.getErrorType(request, new InfluxDbResponse("_OTHER"), null))
        .isEqualTo("_OTHER");

    IllegalStateException callbackError = new IllegalStateException();
    IOException thrownError = new IOException();

    assertThat(getter.getErrorType(request, null, callbackError))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(getter.getErrorType(request, new InfluxDbResponse(null), thrownError))
        .isEqualTo(IOException.class.getName());
    assertThat(getter.getErrorType(request, new InfluxDbResponse("_OTHER"), callbackError))
        .isEqualTo("_OTHER");
  }
}
