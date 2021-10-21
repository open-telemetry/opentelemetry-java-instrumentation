/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitSingletons.deliverInstrumenter;

import com.rabbitmq.client.AMQP;
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

  public TracedDelegatingConsumer(String queue, Consumer delegate) {
    this.queue = queue;
    this.delegate = delegate;
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
    DeliveryRequest request = DeliveryRequest.create(queue, envelope, properties, body);
    Context context = deliverInstrumenter().start(parentContext, request);

    try (Scope ignored = context.makeCurrent()) {
      // Call delegate.
      delegate.handleDelivery(consumerTag, envelope, properties, body);
      deliverInstrumenter().end(context, request, null, null);
    } catch (Throwable throwable) {
      deliverInstrumenter().end(context, request, null, throwable);
      throw throwable;
    }
  }
}
