/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.testing.common.AgentClassLoaderAccess;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Promise;
import scala.concurrent.Promise$;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

class ResponseFutureCallbacksTest {

  @Test
  void failedProjectionIsMarkedWhenSourceCompletesBeforePropagation() throws Exception {
    Promise<Integer> source = Promise$.MODULE$.apply();
    Future<Integer> sourceFuture = source.future();
    Future<Throwable> projection = sourceFuture.failed();

    Class<?> callbacksClass = responseFutureCallbacks(sourceFuture.getClass().getClassLoader());
    Method mark = callbacksClass.getDeclaredMethod("mark", Future.class);
    mark.setAccessible(true);
    mark.invoke(null, sourceFuture);
    source.success(1);

    ContextKey<String> marker = ContextKey.named("completed-failed-projection");
    CompletableFuture<String> callback = new CompletableFuture<>();
    ExecutionContext executionContext =
        new ExecutionContext() {
          @Override
          public ExecutionContext prepare() {
            return this;
          }

          @Override
          public void execute(Runnable command) {
            try (Scope ignored = Context.root().makeCurrent()) {
              command.run();
            }
          }

          @Override
          public void reportFailure(Throwable error) {
            callback.completeExceptionally(error);
          }
        };

    try (Scope ignored = Context.root().with(marker, "registered").makeCurrent()) {
      Method inheritFailed =
          callbacksClass.getDeclaredMethod("inheritFailed", Future.class, Future.class);
      inheritFailed.setAccessible(true);
      inheritFailed.invoke(null, sourceFuture, projection);
      projection.onComplete(
          new AbstractFunction1<Try<Throwable>, Object>() {
            @Override
            public Object apply(Try<Throwable> result) {
              callback.complete(Context.current().get(marker));
              return null;
            }
          },
          executionContext);
      assertThat(callback.get(5, SECONDS)).isEqualTo("registered");
    }
  }

  private static Class<?> responseFutureCallbacks(ClassLoader instrumentedClassLoader)
      throws Exception {
    Class<?> moduleRegistry =
        AgentClassLoaderAccess.loadClass(
            "io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry");
    Method getInstrumentationClassLoader =
        moduleRegistry.getMethod("getInstrumentationClassLoader", String.class, ClassLoader.class);
    ClassLoader moduleClassLoader =
        (ClassLoader)
            getInstrumentationClassLoader.invoke(
                null,
                "io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client.ScalaFutureInstrumentationModule",
                instrumentedClassLoader);
    return moduleClassLoader.loadClass(
        "io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client.ResponseFutureCallbacks");
  }
}
