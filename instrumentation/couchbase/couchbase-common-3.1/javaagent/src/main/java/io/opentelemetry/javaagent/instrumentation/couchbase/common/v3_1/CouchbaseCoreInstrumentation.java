/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class CouchbaseCoreInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.Core");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isStatic().and(named("create")).and(takesArguments(3)).and(takesArgument(2, Set.class)),
        getClass().getName() + "$SeedNodesFactoryAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(3)).and(takesArgument(2, Set.class)),
        getClass().getName() + "$SeedNodesConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(4)).and(takesArgument(3, String.class)),
        getClass().getName() + "$TextConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(4))
            .and(takesArgument(3, named("com.couchbase.client.core.util.ConnectionString"))),
        getClass().getName() + "$ParsedSeedNodesConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(3))
            .and(takesArgument(2, named("com.couchbase.client.core.util.ConnectionString"))),
        getClass().getName() + "$ParsedConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class SeedNodesFactoryAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.Return Core core,
        @Advice.Argument(0) CoreEnvironment environment,
        @Advice.Argument(2) Set<SeedNode> seedNodes) {
      CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes, environment);
    }
  }

  @SuppressWarnings("unused")
  public static class SeedNodesConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core,
        @Advice.Argument(0) CoreEnvironment environment,
        @Advice.Argument(2) Set<SeedNode> seedNodes) {
      CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes, environment);
    }
  }

  @SuppressWarnings("unused")
  public static class TextConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core,
        @Advice.Argument(0) CoreEnvironment environment,
        @Advice.Argument(2) Set<SeedNode> seedNodes,
        @Advice.Argument(3) String connectionString) {
      if (connectionString == null) {
        CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes, environment);
      } else {
        CouchbaseServerTargets.register(core, CouchbaseConnectionStrings.target(connectionString));
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ParsedSeedNodesConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core,
        @Advice.Argument(0) CoreEnvironment environment,
        @Advice.Argument(2) Set<SeedNode> seedNodes,
        @Advice.Argument(3) ConnectionString connectionString) {
      if (connectionString == null) {
        CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes, environment);
      } else {
        CouchbaseServerTargets.register(core, CouchbaseConnectionStrings.target(connectionString));
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ParsedConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core, @Advice.Argument(2) ConnectionString connectionString) {
      CouchbaseServerTargets.register(core, CouchbaseConnectionStrings.target(connectionString));
    }
  }
}
