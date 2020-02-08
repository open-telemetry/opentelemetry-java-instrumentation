package io.opentelemetry.auto.instrumentation.jms;

import static io.opentelemetry.auto.instrumentation.jms.JMSDecorator.PRODUCER_DECORATE;
import static io.opentelemetry.auto.instrumentation.jms.JMSDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.jms.MessageInjectAdapter.SETTER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentelemetry.trace.Span.Kind.PRODUCER;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JMSMessageProducerInstrumentation extends Instrumenter.Default {

  public JMSMessageProducerInstrumentation() {
    super("jms", "jms-1", "jms-2");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.jms.MessageProducer")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      packageName + ".JMSDecorator",
      packageName + ".JMSDecorator$1",
      packageName + ".JMSDecorator$2",
      packageName + ".MessageExtractAdapter",
      packageName + ".MessageInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("send").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        JMSMessageProducerInstrumentation.class.getName() + "$ProducerAdvice");
    transformers.put(
        named("send")
            .and(takesArgument(0, named("javax.jms.Destination")))
            .and(takesArgument(1, named("javax.jms.Message")))
            .and(isPublic()),
        JMSMessageProducerInstrumentation.class.getName() + "$ProducerWithDestinationAdvice");
    return transformers;
  }

  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.Argument(0) final Message message, @Advice.This final MessageProducer producer) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MessageProducer.class);
      if (callDepth > 0) {
        return null;
      }

      Destination defaultDestination;
      try {
        defaultDestination = producer.getDestination();
      } catch (final JMSException e) {
        defaultDestination = null;
      }

      final Span span = TRACER.spanBuilder("jms.produce").setSpanKind(PRODUCER).startSpan();
      span.setAttribute("span.origin.type", producer.getClass().getName());
      PRODUCER_DECORATE.afterStart(span);
      PRODUCER_DECORATE.onProduce(span, message, defaultDestination);

      TRACER.getHttpTextFormat().inject(span.getContext(), message, SETTER);

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      final Span span = spanWithScope.getSpan();
      PRODUCER_DECORATE.onError(span, throwable);
      PRODUCER_DECORATE.beforeFinish(span);

      span.end();
      spanWithScope.closeScope();
      CallDepthThreadLocalMap.reset(MessageProducer.class);
    }
  }

  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.Argument(0) final Destination destination,
        @Advice.Argument(1) final Message message,
        @Advice.This final MessageProducer producer) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MessageProducer.class);
      if (callDepth > 0) {
        return null;
      }

      final Span span = TRACER.spanBuilder("jms.produce").setSpanKind(PRODUCER).startSpan();
      span.setAttribute("span.origin.type", producer.getClass().getName());
      PRODUCER_DECORATE.afterStart(span);
      PRODUCER_DECORATE.onProduce(span, message, destination);

      TRACER.getHttpTextFormat().inject(span.getContext(), message, SETTER);

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      final Span span = spanWithScope.getSpan();
      PRODUCER_DECORATE.onError(span, throwable);
      PRODUCER_DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
      CallDepthThreadLocalMap.reset(MessageProducer.class);
    }
  }
}
