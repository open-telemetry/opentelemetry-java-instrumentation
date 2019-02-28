package datadog.trace.instrumentation.jms;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.jms.JMSDecorator.PRODUCER_DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Scope;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
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
      packageName + ".MessagePropertyTextMap",
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
    public static Scope startSpan(
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

      final Scope scope =
          GlobalTracer.get()
              .buildSpan("jms.produce")
              .withTag("span.origin.type", producer.getClass().getName())
              .startActive(true);
      PRODUCER_DECORATE.afterStart(scope);
      PRODUCER_DECORATE.onProduce(scope, message, defaultDestination);

      GlobalTracer.get()
          .inject(
              scope.span().context(), Format.Builtin.TEXT_MAP, new MessagePropertyTextMap(message));

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {

      if (scope != null) {
        PRODUCER_DECORATE.onError(scope, throwable);
        PRODUCER_DECORATE.beforeFinish(scope);
        scope.close();
        CallDepthThreadLocalMap.reset(MessageProducer.class);
      }
    }
  }

  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(0) final Destination destination,
        @Advice.Argument(1) final Message message,
        @Advice.This final MessageProducer producer) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MessageProducer.class);
      if (callDepth > 0) {
        return null;
      }

      final Scope scope =
          GlobalTracer.get()
              .buildSpan("jms.produce")
              .withTag("span.origin.type", producer.getClass().getName())
              .startActive(true);
      PRODUCER_DECORATE.afterStart(scope);
      PRODUCER_DECORATE.onProduce(scope, message, destination);

      GlobalTracer.get()
          .inject(
              scope.span().context(), Format.Builtin.TEXT_MAP, new MessagePropertyTextMap(message));

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (scope != null) {
        PRODUCER_DECORATE.onError(scope, throwable);
        PRODUCER_DECORATE.beforeFinish(scope);
        scope.close();
        CallDepthThreadLocalMap.reset(MessageProducer.class);
      }
    }
  }
}
