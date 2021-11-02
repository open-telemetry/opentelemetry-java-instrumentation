/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.bytebuddy;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.javaagent.tooling.SafeServiceLoader;
import io.opentelemetry.javaagent.tooling.ignore.AdditionalLibraryIgnoredTypesConfigurer;
import io.opentelemetry.javaagent.tooling.ignore.GlobalIgnoredTypesConfigurer;
import io.opentelemetry.javaagent.tooling.ignore.IgnoreAllow;
import io.opentelemetry.javaagent.tooling.ignore.IgnoredTypesBuilderImpl;
import io.opentelemetry.javaagent.tooling.instrumentation.MuzzleFailureCounter;
import io.opentelemetry.javaagent.tooling.util.Trie;
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
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestAgentListener implements AgentBuilder.Listener {

  private static final Logger logger = LoggerFactory.getLogger(TestAgentListener.class);

  private static final Trie<IgnoreAllow> ADDITIONAL_LIBRARIES_TRIE;
  private static final Trie<IgnoreAllow> OTHER_IGNORES_TRIE;

  static {
    ADDITIONAL_LIBRARIES_TRIE = buildAdditionalLibraryIgnores();
    OTHER_IGNORES_TRIE = buildOtherConfiguredIgnores();
  }

  private static Trie<IgnoreAllow> buildAdditionalLibraryIgnores() {
    IgnoredTypesBuilderImpl builder = new IgnoredTypesBuilderImpl();
    new AdditionalLibraryIgnoredTypesConfigurer().configure(builder);
    return builder.buildIgnoredTypesTrie();
  }

  private static Trie<IgnoreAllow> buildOtherConfiguredIgnores() {
    Config config = Config.builder().build();
    IgnoredTypesBuilderImpl builder = new IgnoredTypesBuilderImpl();
    for (IgnoredTypesConfigurer configurer :
        SafeServiceLoader.loadOrdered(IgnoredTypesConfigurer.class)) {
      // skip built-in agent ignores
      if (configurer instanceof AdditionalLibraryIgnoredTypesConfigurer
          || configurer instanceof GlobalIgnoredTypesConfigurer) {
        continue;
      }
      configurer.configure(config, builder);
    }
    return builder.buildIgnoredTypesTrie();
  }

  public static void reset() {
    INSTANCE.transformedClassesNames.clear();
    INSTANCE.instrumentationErrorCount.set(0);
    INSTANCE.skipTransformationConditions.clear();
    INSTANCE.skipErrorConditions.clear();
  }

  public static int getInstrumentationErrorCount() {
    return INSTANCE.instrumentationErrorCount.get();
  }

  public static int getAndResetMuzzleFailureCount() {
    return MuzzleFailureCounter.getAndReset();
  }

  public static List<String> getIgnoredButTransformedClassNames() {
    List<String> names = new ArrayList<>();
    for (String name : INSTANCE.transformedClassesNames) {
      // only record those types that weren't explicitly marked as either ignored or allowed by the
      // instrumentation authors
      if (ADDITIONAL_LIBRARIES_TRIE.getOrNull(name) == IgnoreAllow.IGNORE
          && OTHER_IGNORES_TRIE.getOrNull(name) == null) {
        names.add(name);
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
