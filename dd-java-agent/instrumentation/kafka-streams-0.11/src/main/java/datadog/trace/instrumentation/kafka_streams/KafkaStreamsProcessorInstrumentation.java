package datadog.trace.instrumentation.kafka_streams;

import static datadog.trace.instrumentation.kafka_streams.KafkaStreamsDecorator.CONSUMER_DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsProcessorInstrumentation {
  // These two instrumentations work together to apply StreamTask.process.
  // The combination of these is needed because there's no good instrumentation point.
  // FIXME: this instrumentation takes somewhat strange approach. It looks like Kafka Streams
  // defines notions of 'processor', 'source' and 'sink'. There is no 'consumer' as such.
  // Also this instrumentation doesn't define 'producer' making it 'asymmetric' - resulting
  // in awkward tests and traces. We may want to revisit this in the future.

  @AutoService(Instrumenter.class)
  public static class StartInstrumentation extends Instrumenter.Default {

    public StartInstrumentation() {
      super("kafka", "kafka-streams");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.kafka.streams.processor.internals.PartitionGroup");
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        "datadog.trace.agent.decorator.BaseDecorator",
        "datadog.trace.agent.decorator.ClientDecorator",
        packageName + ".KafkaStreamsDecorator",
        packageName + ".TextMapExtractAdapter"
      };
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(isPackagePrivate())
              .and(named("nextRecord"))
              .and(returns(named("org.apache.kafka.streams.processor.internals.StampedRecord"))),
          StartSpanAdvice.class.getName());
    }

    public static class StartSpanAdvice {

      @Advice.OnMethodExit(suppress = Throwable.class)
      public static void startSpan(@Advice.Return final StampedRecord record) {
        if (record == null) {
          return;
        }

        final SpanContext extractedContext =
            GlobalTracer.get()
                .extract(
                    Format.Builtin.TEXT_MAP, new TextMapExtractAdapter(record.value.headers()));

        final Scope scope =
            GlobalTracer.get()
                .buildSpan("kafka.consume")
                .asChildOf(extractedContext)
                .startActive(true);
        CONSUMER_DECORATE.afterStart(scope);
        CONSUMER_DECORATE.onConsume(scope, record);
      }
    }
  }

  @AutoService(Instrumenter.class)
  public static class StopInstrumentation extends Instrumenter.Default {

    public StopInstrumentation() {
      super("kafka", "kafka-streams");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.kafka.streams.processor.internals.StreamTask");
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        "datadog.trace.agent.decorator.BaseDecorator",
        "datadog.trace.agent.decorator.ClientDecorator",
        packageName + ".KafkaStreamsDecorator",
        packageName + ".TextMapExtractAdapter"
      };
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("process")).and(takesArguments(0)),
          StopSpanAdvice.class.getName());
    }

    public static class StopSpanAdvice {

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void stopSpan(@Advice.Thrown final Throwable throwable) {
        final Scope scope = GlobalTracer.get().scopeManager().active();
        if (scope != null) {
          CONSUMER_DECORATE.onError(scope, throwable);
          CONSUMER_DECORATE.beforeFinish(scope);
          scope.close();
        }
      }
    }
  }
}
