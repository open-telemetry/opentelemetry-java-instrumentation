package datadog.trace.instrumentation.rabbitmq.amqp;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.rabbitmq.amqp.RabbitDecorator.CONSUMER_DECORATE;
import static datadog.trace.instrumentation.rabbitmq.amqp.RabbitDecorator.DECORATE;
import static datadog.trace.instrumentation.rabbitmq.amqp.RabbitDecorator.PRODUCER_DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.canThrow;
import static net.bytebuddy.matcher.ElementMatchers.isGetter;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSetter;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.noop.NoopSpan;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class RabbitChannelInstrumentation extends Instrumenter.Default {

  public RabbitChannelInstrumentation() {
    super("amqp", "rabbitmq");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("com.rabbitmq.client.Channel")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      packageName + ".RabbitDecorator",
      packageName + ".RabbitDecorator$1",
      packageName + ".RabbitDecorator$2",
      packageName + ".TextMapInjectAdapter",
      packageName + ".TextMapExtractAdapter",
      packageName + ".TracedDelegatingConsumer",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    // We want the advice applied in a specific order, so use an ordered map.
    final Map<ElementMatcher<? super MethodDescription>, String> transformers =
        new LinkedHashMap<>();
    transformers.put(
        isMethod()
            .and(
                not(
                    isGetter()
                        .or(isSetter())
                        .or(nameEndsWith("Listener"))
                        .or(nameEndsWith("Listeners"))
                        .or(named("processAsync"))
                        .or(named("open"))
                        .or(named("close"))
                        .or(named("abort"))
                        .or(named("basicGet"))))
            .and(isPublic())
            .and(canThrow(IOException.class).or(canThrow(InterruptedException.class))),
        ChannelMethodAdvice.class.getName());
    transformers.put(
        isMethod().and(named("basicPublish")).and(takesArguments(6)),
        ChannelPublishAdvice.class.getName());
    transformers.put(
        isMethod().and(named("basicGet")).and(takesArgument(0, String.class)),
        ChannelGetAdvice.class.getName());
    transformers.put(
        isMethod()
            .and(named("basicConsume"))
            .and(takesArgument(0, String.class))
            .and(takesArgument(6, named("com.rabbitmq.client.Consumer"))),
        ChannelConsumeAdvice.class.getName());
    return transformers;
  }

  public static class ChannelMethodAdvice {
    @Advice.OnMethodEnter
    public static Scope startSpan(
        @Advice.This final Channel channel, @Advice.Origin("Channel.#m") final String method) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Channel.class);
      if (callDepth > 0) {
        return null;
      }

      final Connection connection = channel.getConnection();

      final Scope scope =
          GlobalTracer.get()
              .buildSpan("amqp.command")
              .withTag(DDTags.RESOURCE_NAME, method)
              .withTag(Tags.PEER_PORT.getKey(), connection.getPort())
              .startActive(true);
      DECORATE.afterStart(scope);
      DECORATE.onPeerConnection(scope.span(), connection.getAddress());
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (scope != null) {
        DECORATE.onError(scope, throwable);
        DECORATE.beforeFinish(scope);
        scope.close();
        CallDepthThreadLocalMap.reset(Channel.class);
      }
    }
  }

  public static class ChannelPublishAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void setResourceNameAddHeaders(
        @Advice.Argument(0) final String exchange,
        @Advice.Argument(1) final String routingKey,
        @Advice.Argument(value = 4, readOnly = false) AMQP.BasicProperties props,
        @Advice.Argument(5) final byte[] body) {
      final Span span = GlobalTracer.get().activeSpan();

      if (span != null) {
        PRODUCER_DECORATE.afterStart(span); // Overwrite tags set by generic decorator.
        PRODUCER_DECORATE.onPublish(span, exchange, routingKey);
        span.setTag("message.size", body == null ? 0 : body.length);

        // This is the internal behavior when props are null.  We're just doing it earlier now.
        if (props == null) {
          props = MessageProperties.MINIMAL_BASIC;
        }
        span.setTag("amqp.delivery_mode", props.getDeliveryMode());

        // We need to copy the BasicProperties and provide a header map we can modify
        Map<String, Object> headers = props.getHeaders();
        headers = (headers == null) ? new HashMap<String, Object>() : new HashMap<>(headers);

        GlobalTracer.get()
            .inject(span.context(), Format.Builtin.TEXT_MAP, new TextMapInjectAdapter(headers));

        props =
            new AMQP.BasicProperties(
                props.getContentType(),
                props.getContentEncoding(),
                headers,
                props.getDeliveryMode(),
                props.getPriority(),
                props.getCorrelationId(),
                props.getReplyTo(),
                props.getExpiration(),
                props.getMessageId(),
                props.getTimestamp(),
                props.getType(),
                props.getUserId(),
                props.getAppId(),
                props.getClusterId());
      }
    }
  }

  public static class ChannelGetAdvice {
    @Advice.OnMethodEnter
    public static long takeTimestamp(
        @Advice.Local("placeholderScope") Scope placeholderScope,
        @Advice.Local("callDepth") int callDepth) {
      callDepth = CallDepthThreadLocalMap.incrementCallDepth(Channel.class);
      // Don't want RabbitCommandInstrumentation to mess up our actual parent span.
      placeholderScope = GlobalTracer.get().scopeManager().activate(NoopSpan.INSTANCE, false);
      return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void extractAndStartSpan(
        @Advice.This final Channel channel,
        @Advice.Argument(0) final String queue,
        @Advice.Enter final long startTime,
        @Advice.Local("placeholderScope") final Scope placeholderScope,
        @Advice.Local("callDepth") final int callDepth,
        @Advice.Return final GetResponse response,
        @Advice.Thrown final Throwable throwable) {

      if (placeholderScope.span() instanceof NoopSpan) {
        placeholderScope.close();
      }

      if (callDepth > 0) {
        return;
      }
      SpanContext parentContext = null;

      if (response != null && response.getProps() != null) {
        final Map<String, Object> headers = response.getProps().getHeaders();

        parentContext =
            headers == null
                ? null
                : GlobalTracer.get()
                    .extract(Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(headers));
      }

      if (parentContext == null) {
        final Span parent = GlobalTracer.get().activeSpan();
        if (parent != null) {
          parentContext = parent.context();
        }
      }

      final Connection connection = channel.getConnection();

      final Integer length = response == null ? null : response.getBody().length;

      // TODO: it would be better if we could actually have span wrapped into the scope started in
      // OnMethodEnter
      final Span span =
          GlobalTracer.get()
              .buildSpan("amqp.command")
              .withStartTimestamp(TimeUnit.MILLISECONDS.toMicros(startTime))
              .asChildOf(parentContext)
              .withTag("message.size", length)
              .withTag(Tags.PEER_PORT.getKey(), connection.getPort())
              .start();
      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
        CONSUMER_DECORATE.afterStart(span);
        CONSUMER_DECORATE.onGet(span, queue);
        CONSUMER_DECORATE.onPeerConnection(span, connection.getAddress());
        CONSUMER_DECORATE.onError(span, throwable);
        CONSUMER_DECORATE.beforeFinish(span);
      } finally {
        span.finish();
        CallDepthThreadLocalMap.reset(Channel.class);
      }
    }
  }

  public static class ChannelConsumeAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapConsumer(
        @Advice.Argument(0) final String queue,
        @Advice.Argument(value = 6, readOnly = false) Consumer consumer) {
      // We have to save off the queue name here because it isn't available to the consumer later.
      if (consumer != null && !(consumer instanceof TracedDelegatingConsumer)) {
        consumer = new TracedDelegatingConsumer(queue, consumer);
      }
    }
  }
}
