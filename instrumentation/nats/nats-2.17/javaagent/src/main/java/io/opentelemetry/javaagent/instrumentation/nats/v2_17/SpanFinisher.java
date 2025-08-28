/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nats.v2_17;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsRequest;
import java.util.function.BiConsumer;

public class SpanFinisher implements BiConsumer<Message, Throwable> {
  private final Instrumenter<NatsRequest, NatsRequest> instrumenter;
  private final Context context;
  private final Connection connection;
  private final NatsRequest request;

  public SpanFinisher(
      Instrumenter<NatsRequest, NatsRequest> instrumenter,
      Context context,
      Connection connection,
      NatsRequest request) {
    this.instrumenter = instrumenter;
    this.context = context;
    this.connection = connection;
    this.request = request;
  }

  @Override
  public void accept(Message message, Throwable throwable) {
    if (message != null) {
      NatsRequest response = NatsRequest.create(connection, message);
      instrumenter.end(context, request, response, throwable);
    } else {
      instrumenter.end(context, request, null, throwable);
    }
  }
}
