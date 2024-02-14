/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub.publisher;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.api.gax.rpc.UnaryCallable;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PubsubPublisherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.google.cloud.pubsub.v1.stub.GrpcPublisherStub")
        .or(named("com.google.cloud.pubsub.v1.stub.HttpJsonPublisherStub"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(3)),
        PubsubPublisherInstrumentation.class.getName() + "$AddConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddConstructorAdvice {
    @Advice.OnMethodExit()
    public static void wrapCallable(
        @Advice.FieldValue(value = "publishCallable", readOnly = false)
            UnaryCallable<PublishRequest, PublishResponse> publishCallable) {
      publishCallable = new TracingMessagePublisher(publishCallable);
    }
  }
}
