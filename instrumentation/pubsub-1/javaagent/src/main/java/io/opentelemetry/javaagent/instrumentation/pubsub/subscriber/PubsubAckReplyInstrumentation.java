package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PubsubAckReplyInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.google.cloud.pubsub.v1.AckReplyConsumerImpl")
            .or(named("com.google.cloud.pubsub.v1.AckReplyConsumerWithResponseImpl"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
            isMethod().and(named("ack")).and(takesNoArguments()),
            PubsubAckReplyInstrumentation.class.getName() + "$AddAckAdvice");
    transformer.applyAdviceToMethod(
            isMethod().and(named("nack")).and(takesNoArguments()),
            PubsubAckReplyInstrumentation.class.getName() + "$AddNackAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddAckAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor() {
      AckReplyHelper.ack(Context.current());
    }
  }

  @SuppressWarnings("unused")
  public static class AddNackAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor() {
      AckReplyHelper.nack(Context.current());
    }
  }
}
