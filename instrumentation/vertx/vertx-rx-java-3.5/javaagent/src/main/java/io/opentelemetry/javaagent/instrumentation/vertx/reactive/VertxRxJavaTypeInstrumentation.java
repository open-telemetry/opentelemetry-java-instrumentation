package io.opentelemetry.javaagent.instrumentation.vertx.reactive;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class VertxRxJavaTypeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // match io.vertx.reactivex.* classes that produce Rx types or have subscribe(...) methods
    return nameStartsWith("io.vertx.reactivex");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // instrument methods named 'subscribe' to wrap the observer argument
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("subscribe").or(named("subscribeWith")))
            // common signatures have observer/consumer arguments
            .and(takesArgument(0, any())),
        SubscribeAdvice.class.getName());
  }
}
