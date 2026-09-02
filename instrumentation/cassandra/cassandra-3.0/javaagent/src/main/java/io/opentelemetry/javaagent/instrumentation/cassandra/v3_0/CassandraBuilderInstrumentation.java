/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import com.datastax.driver.core.Cluster;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CassandraBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.datastax.driver.core.Cluster$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("addContactPoint", "addContactPoints", "addContactPointsWithPorts"),
        getClass().getName() + "$AddContactPointsAdvice");
    transformer.applyAdviceToMethod(named("build"), getClass().getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddContactPointsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static CallDepth onEnter() {
      CallDepth callDepth = CallDepth.forClass(Cluster.Builder.class);
      callDepth.getAndIncrement();
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This Cluster.Builder builder,
        @Advice.AllArguments Object[] arguments,
        @Advice.Enter CallDepth callDepth,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (callDepth.decrementAndGet() == 0 && emitStableDatabaseSemconv()) {
        if (throwable == null) {
          CassandraConfiguredTarget.capture(builder, arguments);
        } else {
          CassandraConfiguredTarget.invalidate(builder);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This Cluster.Builder builder,
        @Advice.FieldValue("port") int port,
        @Advice.Return Cluster cluster) {
      if (emitStableDatabaseSemconv()) {
        CassandraConfiguredTarget.store(builder, cluster, port);
      }
    }
  }
}
