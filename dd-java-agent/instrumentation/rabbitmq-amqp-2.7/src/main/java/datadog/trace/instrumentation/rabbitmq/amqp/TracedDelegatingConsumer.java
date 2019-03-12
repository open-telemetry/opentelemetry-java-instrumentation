package datadog.trace.instrumentation.rabbitmq.amqp;

import static datadog.trace.instrumentation.rabbitmq.amqp.RabbitDecorator.CONSUMER_DECORATE;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.noop.NoopScopeManager;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
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
    Scope scope = NoopScopeManager.NoopScope.INSTANCE;
    try {
      final Map<String, Object> headers = properties.getHeaders();
      final SpanContext parentContext =
          headers == null
              ? null
              : GlobalTracer.get()
                  .extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(headers));

      scope =
          GlobalTracer.get()
              .buildSpan("amqp.command")
              .asChildOf(parentContext)
              .withTag("message.size", body == null ? 0 : body.length)
              .withTag("span.origin.type", delegate.getClass().getName())
              .startActive(true);
      CONSUMER_DECORATE.afterStart(scope);
      CONSUMER_DECORATE.onDeliver(scope, queue, envelope);

    } catch (final Exception e) {
      log.debug("Instrumentation error in tracing consumer", e);
    } finally {
      try {

        // Call delegate.
        delegate.handleDelivery(consumerTag, envelope, properties, body);

      } catch (final Throwable throwable) {
        CONSUMER_DECORATE.onError(scope, throwable);
        throw throwable;
      } finally {
        CONSUMER_DECORATE.beforeFinish(scope);
        scope.close();
      }
    }
  }
}
