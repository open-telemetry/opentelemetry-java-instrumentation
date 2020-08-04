/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.tooling;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.skipClassLoader;
import static io.opentelemetry.auto.tooling.matcher.GlobalIgnoresMatcher.globalIgnoresMatcher;
import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.tooling.context.FieldBackedProvider;
import io.opentelemetry.instrumentation.auto.api.SafeServiceLoader;
import io.opentelemetry.instrumentation.library.api.config.Config;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentInstaller {

  private static final Logger log = LoggerFactory.getLogger(AgentInstaller.class);

  private static final Map<String, List<Runnable>> CLASS_LOAD_CALLBACKS = new HashMap<>();
  private static volatile Instrumentation INSTRUMENTATION;

  public static Instrumentation getInstrumentation() {
    return INSTRUMENTATION;
  }

  static {
    // WeakMap is used by other classes below, so we need to register the provider first.
    AgentTooling.registerWeakMapProvider();
  }

  public static void installBytebuddyAgent(final Instrumentation inst) {
    if (Config.get().isTraceEnabled()) {
      installBytebuddyAgent(inst, false, new AgentBuilder.Listener[0]);
    } else {
      log.debug("Tracing is disabled, not installing instrumentations.");
    }
  }

  /**
   * Install the core bytebuddy agent along with all implementations of {@link Instrumenter}.
   *
   * @param inst Java Instrumentation used to install bytebuddy
   * @return the agent's class transformer
   */
  public static ResettableClassFileTransformer installBytebuddyAgent(
      final Instrumentation inst,
      final boolean skipAdditionalLibraryMatcher,
      final AgentBuilder.Listener... listeners) {

    ClassLoader savedContextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      // calling (shaded) OpenTelemetry.getTracerProvider() with context class loader set to the
      // agent class loader, so that SPI finds the agent's (isolated) SDK, and (shaded)
      // OpenTelemetry registers it, and then when instrumentation calls (shaded)
      // OpenTelemetry.getTracerProvider() later, they get back the agent's (isolated) SDK
      //
      // but if we don't trigger this early registration, then if instrumentation is the first to
      // call (shaded) OpenTelemetry.getTracerProvider(), then SPI can't see the agent class loader,
      // and so (shaded) OpenTelemetry registers the no-op TracerFactory, and it cannot be replaced
      // later
      Thread.currentThread().setContextClassLoader(AgentInstaller.class.getClassLoader());
      OpenTelemetry.getTracerProvider();
    } finally {
      Thread.currentThread().setContextClassLoader(savedContextClassLoader);
    }

    INSTRUMENTATION = inst;

    addByteBuddyRawSetting();

    FieldBackedProvider.resetContextMatchers();

    AgentBuilder.Ignored ignoredAgentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
            .with(AgentTooling.poolStrategy())
            .with(new ClassLoadListener())
            .with(AgentTooling.locationStrategy())
            // FIXME: we cannot enable it yet due to BB/JVM bug, see
            // https://github.com/raphw/byte-buddy/issues/558
            // .with(AgentBuilder.LambdaInstrumentationStrategy.ENABLED)
            .ignore(any(), skipClassLoader());

    ignoredAgentBuilder =
        ignoredAgentBuilder.or(globalIgnoresMatcher(skipAdditionalLibraryMatcher));

    ignoredAgentBuilder = ignoredAgentBuilder.or(matchesConfiguredExcludes());

    AgentBuilder agentBuilder = ignoredAgentBuilder;
    if (log.isDebugEnabled()) {
      agentBuilder =
          agentBuilder
              .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
              .with(new RedefinitionLoggingListener())
              .with(new TransformLoggingListener());
    }

    for (AgentBuilder.Listener listener : listeners) {
      agentBuilder = agentBuilder.with(listener);
    }
    int numInstrumenters = 0;
    for (Instrumenter instrumenter :
        SafeServiceLoader.load(Instrumenter.class, AgentInstaller.class.getClassLoader())) {
      log.debug("Loading instrumentation {}", instrumenter.getClass().getName());
      try {
        agentBuilder = instrumenter.instrument(agentBuilder);
        numInstrumenters++;
      } catch (final Exception | LinkageError e) {
        log.error("Unable to load instrumentation {}", instrumenter.getClass().getName(), e);
      }
    }
    log.debug("Installed {} instrumenter(s)", numInstrumenters);

    return agentBuilder.installOn(inst);
  }

  private static void addByteBuddyRawSetting() {
    String savedPropertyValue = System.getProperty(TypeDefinition.RAW_TYPES_PROPERTY);
    try {
      System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, "true");
      boolean rawTypes = TypeDescription.AbstractBase.RAW_TYPES;
      if (!rawTypes) {
        log.debug("Too late to enable {}", TypeDefinition.RAW_TYPES_PROPERTY);
      }
    } finally {
      if (savedPropertyValue == null) {
        System.clearProperty(TypeDefinition.RAW_TYPES_PROPERTY);
      } else {
        System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, savedPropertyValue);
      }
    }
  }

  private static ElementMatcher.Junction<Object> matchesConfiguredExcludes() {
    List<String> excludedClasses = Config.get().getExcludedClasses();
    ElementMatcher.Junction matcher = none();
    List<String> literals = new ArrayList<>();
    List<String> prefixes = new ArrayList<>();
    // first accumulate by operation because a lot of work can be aggregated
    for (String excludedClass : excludedClasses) {
      excludedClass = excludedClass.trim();
      if (excludedClass.endsWith("*")) {
        // remove the trailing *
        prefixes.add(excludedClass.substring(0, excludedClass.length() - 1));
      } else {
        literals.add(excludedClass);
      }
    }
    if (!literals.isEmpty()) {
      matcher = matcher.or(namedOneOf(literals));
    }
    for (String prefix : prefixes) {
      // TODO - with a prefix tree this matching logic can be handled by a
      // single longest common prefix query
      matcher = matcher.or(nameStartsWith(prefix));
    }
    return matcher;
  }

  static class RedefinitionLoggingListener implements AgentBuilder.RedefinitionStrategy.Listener {

    private static final Logger log = LoggerFactory.getLogger(RedefinitionLoggingListener.class);

    @Override
    public void onBatch(final int index, final List<Class<?>> batch, final List<Class<?>> types) {}

    @Override
    public Iterable<? extends List<Class<?>>> onError(
        final int index,
        final List<Class<?>> batch,
        final Throwable throwable,
        final List<Class<?>> types) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Exception while retransforming " + batch.size() + " classes: " + batch, throwable);
      }
      return Collections.emptyList();
    }

    @Override
    public void onComplete(
        final int amount,
        final List<Class<?>> types,
        final Map<List<Class<?>>, Throwable> failures) {}
  }

  static class TransformLoggingListener implements AgentBuilder.Listener {

    private static final Logger log = LoggerFactory.getLogger(TransformLoggingListener.class);

    @Override
    public void onError(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final Throwable throwable) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Failed to handle {} for transformation on classloader {}: {}",
            typeName,
            classLoader,
            throwable.getMessage());
      }
    }

    @Override
    public void onTransformation(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final DynamicType dynamicType) {
      log.debug("Transformed {} -- {}", typeDescription.getName(), classLoader);
    }

    @Override
    public void onIgnored(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {
      //      log.debug("onIgnored {}", typeDescription.getName());
    }

    @Override
    public void onComplete(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {
      //      log.debug("onComplete {}", typeName);
    }

    @Override
    public void onDiscovery(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {
      //      log.debug("onDiscovery {}", typeName);
    }
  }

  /**
   * Register a callback to run when a class is loading.
   *
   * <p>Caveats:
   *
   * <ul>
   *   <li>This callback will be invoked by a jvm class transformer.
   *   <li>Classes filtered out by {@link AgentInstaller}'s skip list will not be matched.
   * </ul>
   *
   * @param className name of the class to match against
   * @param callback runnable to invoke when class name matches
   */
  public static void registerClassLoadCallback(final String className, final Runnable callback) {
    synchronized (CLASS_LOAD_CALLBACKS) {
      List<Runnable> callbacks = CLASS_LOAD_CALLBACKS.get(className);
      if (callbacks == null) {
        callbacks = new ArrayList<>();
        CLASS_LOAD_CALLBACKS.put(className, callbacks);
      }
      callbacks.add(callback);
    }
  }

  private static class ClassLoadListener implements AgentBuilder.Listener {
    @Override
    public void onDiscovery(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule javaModule,
        final boolean b) {}

    @Override
    public void onTransformation(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule javaModule,
        final boolean b,
        final DynamicType dynamicType) {}

    @Override
    public void onIgnored(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule javaModule,
        final boolean b) {}

    @Override
    public void onError(
        final String s,
        final ClassLoader classLoader,
        final JavaModule javaModule,
        final boolean b,
        final Throwable throwable) {}

    @Override
    public void onComplete(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule javaModule,
        final boolean b) {
      synchronized (CLASS_LOAD_CALLBACKS) {
        List<Runnable> callbacks = CLASS_LOAD_CALLBACKS.get(typeName);
        if (callbacks != null) {
          for (final Runnable callback : callbacks) {
            callback.run();
          }
        }
      }
    }
  }

  private AgentInstaller() {}
}
