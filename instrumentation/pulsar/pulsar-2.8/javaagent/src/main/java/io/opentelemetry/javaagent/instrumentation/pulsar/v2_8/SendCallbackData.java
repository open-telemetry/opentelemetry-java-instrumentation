/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarRequest;

public class SendCallbackData {
  public final Context context;
  public final PulsarRequest request;

  public static SendCallbackData create(Context context, PulsarRequest request) {
    return new SendCallbackData(context, request);
  }

  private SendCallbackData(Context context, PulsarRequest request) {
    this.context = context;
    this.request = request;
  }
}
