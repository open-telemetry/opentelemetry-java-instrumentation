/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0;

import static io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0.SpringCloudGcpSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.cloud.spring.pubsub.support.converter.ConvertedBasicAcknowledgeablePubsubMessage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class PubSubInboundChannelAdapterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("consumeMessage")
            .and(
                takesArgument(
                    0,
                    named(
                        "com.google.cloud.spring.pubsub.support.converter.ConvertedBasicAcknowledgeablePubsubMessage"))),
        getClass().getName() + "$ConsumeMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConsumeMessageAdvice {

    // consumed Pub/Sub messages are never converted to ack/nack exceptions, so there is no risk of
    // a span leak through ExceptionHandlerInstrumentation
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope methodEnter(
        @Advice.Argument(0) ConvertedBasicAcknowledgeablePubsubMessage<?> message) {
      // context is extracted from the PubsubMessage attributes by the instrumenter, so there is no
      // ambient parent to use here
      Context parentContext = Context.root();
      if (!instrumenter.shouldStart(parentContext, message)) {
        return null;
      }
      Context context = instrumenter.start(parentContext, message);
      return new AdviceScope(instrumenter, context, message, context.makeCurrent());
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Argument(0) ConvertedBasicAcknowledgeablePubsubMessage<?> message,
        @Advice.Enter @Nullable AdviceScope adviceScope,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }

  // the advice below runs in the host class, so this class and its members invoked from the host
  // method must be public
  public static final class AdviceScope {
    private final Instrumenter<ConvertedBasicAcknowledgeablePubsubMessage<?>, Void> instrumenter;
    private final Context context;
    private final ConvertedBasicAcknowledgeablePubsubMessage<?> message;
    private final Scope scope;

    public AdviceScope(
        Instrumenter<ConvertedBasicAcknowledgeablePubsubMessage<?>, Void> instrumenter,
        Context context,
        ConvertedBasicAcknowledgeablePubsubMessage<?> message,
        Scope scope) {
      this.instrumenter = instrumenter;
      this.context = context;
      this.message = message;
      this.scope = scope;
    }

    public void end(@Nullable Throwable throwable) {
      scope.close();
      instrumenter.end(context, message, null, throwable);
    }
  }
}
