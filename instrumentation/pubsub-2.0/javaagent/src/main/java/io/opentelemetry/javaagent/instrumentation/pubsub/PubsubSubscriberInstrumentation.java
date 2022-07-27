/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PubsubSubscriberInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.google.cloud.pubsub.v1.MessageReceiver");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.google.cloud.pubsub.v1.MessageReceiver"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        isPublic().and(named("receiveMessage")),
        this.getClass().getName() + "$PubsubSubscriberAddAttributesAdvice");
  }

  @SuppressWarnings("unused")
  public static class PubsubSubscriberAddAttributesAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterHandle(
        @Advice.Argument(value = 0, readOnly = false) PubsubMessage pubsubMessage) {
      PubsubSingletons.buildAndFinishSpan(Context.current(), pubsubMessage);
    }
  }
}
