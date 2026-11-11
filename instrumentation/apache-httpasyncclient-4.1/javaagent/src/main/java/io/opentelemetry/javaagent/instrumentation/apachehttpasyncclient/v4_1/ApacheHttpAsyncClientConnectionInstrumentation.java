/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1.ApacheHttpAsyncClientInstrumentation.DelegatingRequestProducer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.nio.conn.ManagedClientAsyncConnection;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;

class ApacheHttpAsyncClientConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.apache.http.impl.nio.client.DefaultClientExchangeHandlerImpl",
        "org.apache.http.impl.nio.client.MinimalClientExchangeHandlerImpl",
        "org.apache.http.impl.nio.client.DefaultAsyncRequestDirector");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("generateRequest")
            .and(takesArguments(0))
            .and(
                isDeclaredBy(
                    namedOneOf(
                        "org.apache.http.impl.nio.client.DefaultClientExchangeHandlerImpl",
                        "org.apache.http.impl.nio.client.MinimalClientExchangeHandlerImpl"))),
        getClass().getName() + "$GenerateRequestAdvice");
    transformer.applyAdviceToMethod(
        named("connectionRequestCompleted")
            .and(takesArguments(named("org.apache.http.nio.conn.ManagedClientAsyncConnection")))
            .and(
                isDeclaredBy(named("org.apache.http.impl.nio.client.DefaultAsyncRequestDirector"))),
        getClass().getName() + "$LegacyConnectionCompletedAdvice");
  }

  @SuppressWarnings("unused")
  public static class GenerateRequestAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.FieldValue("requestProducer") HttpAsyncRequestProducer requestProducer) {
      if (requestProducer instanceof DelegatingRequestProducer) {
        ((DelegatingRequestProducer) requestProducer).captureNetworkPeer();
      }
    }
  }

  @SuppressWarnings({"unused", "deprecation"}) // Supporting the legacy AbstractHttpAsyncClient.
  public static class LegacyConnectionCompletedAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) ManagedClientAsyncConnection connection,
        @Advice.FieldValue("requestProducer") HttpAsyncRequestProducer requestProducer,
        @Advice.FieldValue("closed") boolean closed) {
      if (!closed && requestProducer instanceof DelegatingRequestProducer && connection.isOpen()) {
        ((DelegatingRequestProducer) requestProducer).captureNetworkPeer(connection);
      }
    }
  }
}
