package datadog.trace.instrumentation.rabbitmq.amqp;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.noop.NoopScopeManager;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Wrapping the consumer instead of instrumenting it directly because it doesn't get access to the
 * queue name when the message is consumed.
 */
public class TracedDelegatingConsumer implements Consumer {
  private final String queueName;
  private final Consumer delegate;

  public TracedDelegatingConsumer(final String queueName, final Consumer delegate) {
    this.queueName = queueName;
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
  public void handleRecoverOk() {
    delegate.handleRecoverOk();
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

      final String resourceName =
          queueName == null ? "basic.deliver null" : "basic.deliver " + queueName;

      scope =
          GlobalTracer.get()
              .buildSpan("amqp.command")
              .asChildOf(parentContext)
              .withTag(DDTags.SERVICE_NAME, "rabbitmq")
              .withTag(DDTags.RESOURCE_NAME, resourceName)
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.MESSAGE_CONSUMER)
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER)
              .withTag(Tags.COMPONENT.getKey(), "rabbitmq-amqp")
              .withTag("amqp.command", "basic.deliver")
              .withTag("amqp.exchange", envelope.getExchange())
              .withTag("amqp.routing_key", envelope.getRoutingKey())
              .withTag("message.size", body == null ? 0 : body.length)
              .withTag("span.origin.type", delegate.getClass().getName())
              .startActive(true);
    } finally {
      try {

        // Call delegate.
        delegate.handleDelivery(consumerTag, envelope, properties, body);

      } catch (final Throwable throwable) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      } finally {
        scope.close();
      }
    }
  }
}
