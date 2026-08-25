/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterTargets;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Records the target a cluster was configured with while the cluster is being constructed, which is
 * the earliest point at which the driver has settled the configuration and the last point at which
 * it is still the one the client was built with.
 *
 * <p>Only the driver 3.1 to 3.7 location of {@code BaseCluster} is matched here. From 3.8 the class
 * moved into an internal package that the newer instrumentation modules cover.
 *
 * <p>A driver at that location resolves an SRV host into seeds as it parses the connection string,
 * and it keeps no SRV host in its cluster settings, so a cluster here is always described by its
 * seeds.
 */
final class ClusterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.connection.BaseCluster");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("com.mongodb.connection.ClusterId")))
            .and(takesArgument(1, named("com.mongodb.connection.ClusterSettings"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.Argument(0) ClusterId clusterId, @Advice.Argument(1) ClusterSettings settings) {
      MongoClusterTargets.register(clusterId, MongoServerTarget.seeds(settings.getHosts()));
    }
  }
}
