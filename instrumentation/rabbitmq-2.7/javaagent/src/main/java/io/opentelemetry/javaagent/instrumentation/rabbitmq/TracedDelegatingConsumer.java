/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitSingletons.deliverInstrumenter;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;

/**
 * Wrapping the consumer instead of instrumenting it directly because it doesn't get access to the
 * queue name when the message is consumed.
 */
public class TracedDelegatingConsumer implements Consumer {

  private final String queue;
  private final Consumer delegate;
  private final Connection connection;

  public TracedDelegatingConsumer(String queue, Consumer delegate, Connection connection) {
    this.queue = queue;
    this.delegate = delegate;
    this.connection = connection;
  }

  @Override
  public void handleConsumeOk(String consumerTag) {
    delegate.handleConsumeOk(consumerTag);
  }

  @Override
  public void handleCancelOk(String consumerTag) {
    delegate.handleCancelOk(consumerTag);
  }

  @Override
  public void handleCancel(String consumerTag) throws IOException {
    delegate.handleCancel(consumerTag);
  }

  @Override
  public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
    delegate.handleShutdownSignal(consumerTag, sig);
  }

  @Override
  public void handleRecoverOk(String consumerTag) {
    delegate.handleRecoverOk(consumerTag);
  }

  @Override
  public void handleDelivery(
      String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
      throws IOException {
    Context parentContext = Context.current();
    DeliveryRequest request = DeliveryRequest.create(queue, envelope, connection, properties, body);

    if (!deliverInstrumenter().shouldStart(parentContext, request)) {
      delegate.handleDelivery(consumerTag, envelope, properties, body);
      return;
    }

    Context context = deliverInstrumenter().start(parentContext, request);

    try (Scope ignored = context.makeCurrent()) {
      // Call delegate.
      delegate.handleDelivery(consumerTag, envelope, properties, body);
    } catch (Throwable t) {
      deliverInstrumenter().end(context, request, null, t);
      throw t;
    }
    deliverInstrumenter().end(context, request, null, null);
  }
}
