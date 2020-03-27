/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.rabbitmq.amqp;

import static io.opentelemetry.auto.instrumentation.rabbitmq.amqp.RabbitDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.rabbitmq.amqp.RabbitDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.rabbitmq.amqp.TextMapExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.CONSUMER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapping the consumer instead of instrumenting it directly because it doesn't get access to the
 * queue name when the message is consumed.
 */
@Slf4j
public class TracedDelegatingConsumer implements Consumer {
  private final String queue;
  private final Consumer delegate;

  public TracedDelegatingConsumer(final String queue, final Consumer delegate) {
    this.queue = queue;
    this.delegate = delegate;
  }

  @Override
  public void handleConsumeOk(final String consumerTag) {
    delegate.handleConsumeOk(consumerTag);
  }

  @Override
  public void handleCancelOk(final String consumerTag) {
    delegate.handleCancelOk(consumerTag);
  }

  @Override
  public void handleCancel(final String consumerTag) throws IOException {
    delegate.handleCancel(consumerTag);
  }

  @Override
  public void handleShutdownSignal(final String consumerTag, final ShutdownSignalException sig) {
    delegate.handleShutdownSignal(consumerTag, sig);
  }

  @Override
  public void handleRecoverOk(final String consumerTag) {
    delegate.handleRecoverOk(consumerTag);
  }

  @Override
  public void handleDelivery(
      final String consumerTag,
      final Envelope envelope,
      final AMQP.BasicProperties properties,
      final byte[] body)
      throws IOException {
    Span span = null;
    Scope scope = null;
    try {
      final Map<String, Object> headers = properties.getHeaders();
      final Span.Builder spanBuilder =
          TRACER.spanBuilder(DECORATE.spanNameOnDeliver(queue)).setSpanKind(CONSUMER);
      SpanContext extractedContext = SpanContext.getInvalid();
      if (headers != null) {
        extractedContext = TRACER.getHttpTextFormat().extract(headers, GETTER);
      }
      if (extractedContext.isValid()) {
        spanBuilder.setParent(extractedContext);
      } else {
        // explicitly setting "no parent" in case a span was propagated to this thread
        // by the java-concurrent instrumentation when the thread was started
        spanBuilder.setNoParent();
      }

      span = spanBuilder.startSpan();
      span.setAttribute("message.size", body == null ? 0 : body.length);
      span.setAttribute("span.origin.type", delegate.getClass().getName());
      DECORATE.afterStart(span);
      DECORATE.onDeliver(span, envelope);

      scope = currentContextWith(span);

    } catch (final Exception e) {
      log.debug("Instrumentation error in tracing consumer", e);
    } finally {
      try {

        // Call delegate.
        delegate.handleDelivery(consumerTag, envelope, properties, body);

      } catch (final Throwable throwable) {
        if (span != null) {
          DECORATE.onError(span, throwable);
        }
        throw throwable;
      } finally {
        if (scope != null) {
          DECORATE.beforeFinish(span);
          span.end();
          scope.close();
        }
      }
    }
  }
}
