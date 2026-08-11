/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import javax.annotation.Nullable;

public class BasePulsarRequest {

  private final String destination;
  @Nullable private final UrlData urlData;
  @Nullable private final String subscription;

  protected BasePulsarRequest(
      String destination, @Nullable UrlData urlData, @Nullable String subscription) {
    this.destination = destination;
    this.urlData = urlData;
    this.subscription = subscription;
  }

  public String getDestination() {
    return destination;
  }

  @Nullable
  public UrlData getUrlData() {
    return urlData;
  }

  /** Returns the name of the subscription this message was consumed from, if any. */
  @Nullable
  public String getSubscription() {
    return subscription;
  }
}
