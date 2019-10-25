package datadog.trace.instrumentation.jms;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jms.JMSDecorator.PRODUCER_DECORATE;
import static datadog.trace.instrumentation.jms.MessageInjectAdapter.SETTER;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
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
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
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
        ProducerAdvice.class.getName());
    transformers.put(
        named("send")
            .and(takesArgument(0, named("javax.jms.Destination")))
            .and(takesArgument(1, named("javax.jms.Message")))
            .and(isPublic()),
        ProducerWithDestinationAdvice.class.getName());
    return transformers;
  }

  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
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

      final AgentSpan span =
          startSpan("jms.produce").setTag("span.origin.type", producer.getClass().getName());
      PRODUCER_DECORATE.afterStart(span);
      PRODUCER_DECORATE.onProduce(span, message, defaultDestination);

      propagate().inject(span, message, SETTER);

      return activateSpan(span, true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      PRODUCER_DECORATE.onError(scope, throwable);
      PRODUCER_DECORATE.beforeFinish(scope);
      scope.close();
      CallDepthThreadLocalMap.reset(MessageProducer.class);
    }
  }

  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.Argument(0) final Destination destination,
        @Advice.Argument(1) final Message message,
        @Advice.This final MessageProducer producer) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MessageProducer.class);
      if (callDepth > 0) {
        return null;
      }

      final AgentSpan span =
          startSpan("jms.produce").setTag("span.origin.type", producer.getClass().getName());
      PRODUCER_DECORATE.afterStart(span);
      PRODUCER_DECORATE.onProduce(span, message, destination);

      propagate().inject(span, message, SETTER);

      return activateSpan(span, true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      PRODUCER_DECORATE.onError(scope, throwable);
      PRODUCER_DECORATE.beforeFinish(scope);
      scope.close();
      CallDepthThreadLocalMap.reset(MessageProducer.class);
    }
  }
}
