package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

//10.2+ drops Context after Unmarshalling. Added this simple instrumentation to ensure Context is kept.
public class AkkaHttpUnmarshallerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.unmarshalling.Unmarshal");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("to")
            .and(takesArgument(1, named("scala.concurrent.ExecutionContext")))
            .and(takesArgument(2, named("akka.stream.Materializer"))),
        this.getClass().getName() + "$UnmarshallerAdvice");
  }

  @SuppressWarnings("unused")
  public static class UnmarshallerAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Argument(1) ExecutionContext executionContext,
        @Advice.Return(readOnly = false) Future<?> responseFuture) {
      if (responseFuture != null) {
        responseFuture =
            FutureWrapper.wrap(responseFuture, executionContext, currentContext());
      }
    }
  }
}
