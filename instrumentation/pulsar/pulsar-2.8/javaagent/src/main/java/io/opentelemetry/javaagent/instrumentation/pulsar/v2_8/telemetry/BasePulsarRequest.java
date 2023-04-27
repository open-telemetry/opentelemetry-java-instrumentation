/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;

public class BasePulsarRequest {

  private final String destination;
  private final UrlData urlData;

  protected BasePulsarRequest(String destination, UrlData urlData) {
    this.destination = destination;
    this.urlData = urlData;
  }

  public String getDestination() {
    return destination;
  }

  public UrlData getUrlData() {
    return urlData;
  }
}
