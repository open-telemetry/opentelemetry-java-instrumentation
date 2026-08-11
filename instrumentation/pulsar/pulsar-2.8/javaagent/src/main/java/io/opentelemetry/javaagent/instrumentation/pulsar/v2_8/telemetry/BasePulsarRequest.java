/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.UrlParser.UrlData;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.impl.MultiTopicsConsumerImpl;

public class BasePulsarRequest {

  @Nullable private final String destination;
  @Nullable private final UrlData urlData;
  @Nullable private final String subscription;

  protected BasePulsarRequest(
      @Nullable String destination, @Nullable UrlData urlData, @Nullable String subscription) {
    this.destination = destination;
    this.urlData = urlData;
    this.subscription = subscription;
  }

  @Nullable
  public String getDestination() {
    return destination;
  }

  @Nullable
  static String getConsumerDestination(Consumer<?> consumer) {
    return consumer instanceof MultiTopicsConsumerImpl ? null : consumer.getTopic();
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
