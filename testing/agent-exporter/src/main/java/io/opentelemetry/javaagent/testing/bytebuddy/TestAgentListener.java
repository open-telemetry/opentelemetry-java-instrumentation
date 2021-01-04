/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.bytebuddy;

import io.opentelemetry.javaagent.tooling.matcher.AdditionalLibraryIgnoresMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestAgentListener implements AgentBuilder.Listener {

  private static final Logger logger = LoggerFactory.getLogger(TestAgentListener.class);

  private static final ElementMatcher.Junction<TypeDescription> GLOBAL_LIBRARIES_IGNORES_MATCHER =
      AdditionalLibraryIgnoresMatcher.additionalLibraryIgnoresMatcher();

  public static void reset() {
    INSTANCE.transformedClassesNames.clear();
    INSTANCE.transformedClassesTypes.clear();
    INSTANCE.instrumentationErrorCount.set(0);
    INSTANCE.skipTransformationConditions.clear();
    INSTANCE.skipErrorConditions.clear();
  }

  public static int getInstrumentationErrorCount() {
    return INSTANCE.instrumentationErrorCount.get();
  }

  public static List<String> getIgnoredButTransformedClassNames() {
    List<String> names = new ArrayList<>();
    for (TypeDescription type : INSTANCE.transformedClassesTypes) {
      if (GLOBAL_LIBRARIES_IGNORES_MATCHER.matches(type)) {
        names.add(type.getActualName());
      }
    }
    return names;
  }

  public static void addSkipTransformationCondition(Function<String, Boolean> condition) {
    INSTANCE.skipTransformationConditions.add(condition);
  }

  public static void addSkipErrorCondition(BiFunction<String, Throwable, Boolean> condition) {
    INSTANCE.skipErrorConditions.add(condition);
  }

  static final TestAgentListener INSTANCE = new TestAgentListener();

  private final Set<String> transformedClassesNames =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<TypeDescription> transformedClassesTypes =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final AtomicInteger instrumentationErrorCount = new AtomicInteger(0);
  private final Set<Function<String, Boolean>> skipTransformationConditions =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final Set<BiFunction<String, Throwable, Boolean>> skipErrorConditions =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  @Override
  public void onDiscovery(
      String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
    for (Function<String, Boolean> skipCondition : skipTransformationConditions) {
      if (skipCondition.apply(typeName)) {
        throw new AbortTransformationException(
            "Aborting transform for class name = " + typeName + ", loader = " + classLoader);
      }
    }
  }

  @Override
  public void onTransformation(
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module,
      boolean loaded,
      DynamicType dynamicType) {
    transformedClassesNames.add(typeDescription.getActualName());
    transformedClassesTypes.add(typeDescription);
  }

  @Override
  public void onIgnored(
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module,
      boolean loaded) {}

  @Override
  public void onError(
      String typeName,
      ClassLoader classLoader,
      JavaModule module,
      boolean loaded,
      Throwable throwable) {
    for (BiFunction<String, Throwable, Boolean> condition : skipErrorConditions) {
      if (condition.apply(typeName, throwable)) {
        return;
      }
    }
    if (!(throwable instanceof AbortTransformationException)) {
      logger.error(
          "Unexpected instrumentation error when instrumenting {} on {}",
          typeName,
          classLoader,
          throwable);
      instrumentationErrorCount.incrementAndGet();
    }
  }

  @Override
  public void onComplete(
      String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}

  /** Used to signal that a transformation was intentionally aborted and is not an error. */
  private static class AbortTransformationException extends RuntimeException {
    public AbortTransformationException(String message) {
      super(message);
    }
  }
}
