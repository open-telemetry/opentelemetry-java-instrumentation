package datadog.trace.instrumentation.rabbitmq.amqp;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.rabbitmq.amqp.RabbitDecorator.CONSUMER_DECORATE;
import static datadog.trace.instrumentation.rabbitmq.amqp.TextMapExtractAdapter.GETTER;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.AgentSpan.Context;
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
    AgentScope scope = null;
    try {
      final Map<String, Object> headers = properties.getHeaders();
      final Context context = headers == null ? null : propagate().extract(headers, GETTER);

      final AgentSpan span =
          startSpan("amqp.command", context)
              .setTag("message.size", body == null ? 0 : body.length)
              .setTag("span.origin.type", delegate.getClass().getName());
      CONSUMER_DECORATE.afterStart(span);
      CONSUMER_DECORATE.onDeliver(span, queue, envelope);

      scope = activateSpan(span, true);

    } catch (final Exception e) {
      log.debug("Instrumentation error in tracing consumer", e);
    } finally {
      try {

        // Call delegate.
        delegate.handleDelivery(consumerTag, envelope, properties, body);

      } catch (final Throwable throwable) {
        if (scope != null) {
          CONSUMER_DECORATE.onError(scope, throwable);
        }
        throw throwable;
      } finally {
        if (scope != null) {
          CONSUMER_DECORATE.beforeFinish(scope);
          scope.close();
        }
      }
    }
  }
}
