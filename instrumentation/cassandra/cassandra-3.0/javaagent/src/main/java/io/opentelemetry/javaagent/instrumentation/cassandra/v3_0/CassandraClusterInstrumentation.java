/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.datastax.driver.core.Cluster;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CassandraClusterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.datastax.driver.core.Cluster");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("buildFrom")
            .and(takesArgument(0, named("com.datastax.driver.core.Cluster$Initializer"))),
        getClass().getName() + "$BuildFromAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildFromAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Cluster.Initializer initializer, @Advice.Return Cluster cluster) {
      if (emitStableDatabaseSemconv() && initializer instanceof Cluster.Builder) {
        CassandraServerTarget.store(
            (Cluster.Builder) initializer,
            cluster,
            cluster.getConfiguration().getProtocolOptions().getPort());
      }
    }
  }
}
