/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.skipClassLoader;
import static io.opentelemetry.javaagent.tooling.matcher.GlobalIgnoresMatcher.globalIgnoresMatcher;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.internal.BootstrapPackagePrefixesHolder;
import io.opentelemetry.javaagent.instrumentation.api.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.instrumentation.api.SafeServiceLoader;
import io.opentelemetry.javaagent.spi.BootstrapPackagesProvider;
import io.opentelemetry.javaagent.tooling.config.ConfigInitializer;
import io.opentelemetry.javaagent.tooling.context.FieldBackedProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  private static final String TRACE_ENABLED_CONFIG = "otel.trace.enabled";
  private static final String EXCLUDED_CLASSES_CONFIG = "otel.trace.classes.exclude";

  private static final Map<String, List<Runnable>> CLASS_LOAD_CALLBACKS = new HashMap<>();
  private static volatile Instrumentation INSTRUMENTATION;

  public static Instrumentation getInstrumentation() {
    return INSTRUMENTATION;
  }

  static {
    BootstrapPackagePrefixesHolder.setBoostrapPackagePrefixes(loadBootstrapPackagePrefixes());
    // WeakMap is used by other classes below, so we need to register the provider first.
    AgentTooling.registerWeakMapProvider();
    // this needs to be done as early as possible - before the first Config.get() call
    ConfigInitializer.initialize();
  }

  public static void installBytebuddyAgent(Instrumentation inst) {
    if (Config.get().getBooleanProperty(TRACE_ENABLED_CONFIG, true)) {
      installBytebuddyAgent(inst, false);
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
      Instrumentation inst,
      boolean skipAdditionalLibraryMatcher,
      AgentBuilder.Listener... listeners) {

    ClassLoader savedContextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      // calling (shaded) OpenTelemetry.getGlobalTracerProvider() with context class loader set to
      // the
      // agent class loader, so that SPI finds the agent's (isolated) SDK, and (shaded)
      // OpenTelemetry registers it, and then when instrumentation calls (shaded)
      // OpenTelemetry.getGlobalTracerProvider() later, they get back the agent's (isolated) SDK
      //
      // but if we don't trigger this early registration, then if instrumentation is the first to
      // call (shaded) OpenTelemetry.getGlobalTracerProvider(), then SPI can't see the agent class
      // loader,
      // and so (shaded) OpenTelemetry registers the no-op TracerFactory, and it cannot be replaced
      // later
      Thread.currentThread().setContextClassLoader(AgentInstaller.class.getClassLoader());
      OpenTelemetry.getGlobalTracerProvider();
    } finally {
      Thread.currentThread().setContextClassLoader(savedContextClassLoader);
    }

    OpenTelemetrySdkAccess.internalSetForceFlush(
        (timeout, unit) -> OpenTelemetrySdk.getGlobalTracerManagement().forceFlush().join(timeout, unit));

    INSTRUMENTATION = inst;

    addByteBuddyRawSetting();

    FieldBackedProvider.resetContextMatchers();

    AgentBuilder.Ignored ignoredAgentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE)
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
              .with(AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE)
              .with(new RedefinitionLoggingListener())
              .with(new TransformLoggingListener());
    }

    for (AgentBuilder.Listener listener : listeners) {
      agentBuilder = agentBuilder.with(listener);
    }

    int numInstrumenters = 0;

    Iterable<Instrumenter> instrumenters =
        SafeServiceLoader.load(Instrumenter.class, AgentInstaller.class.getClassLoader());
    for (Instrumenter instrumenter : orderInstrumenters(instrumenters)) {
      log.debug("Loading instrumentation {}", instrumenter.getClass().getName());
      try {
        agentBuilder = instrumenter.instrument(agentBuilder);
        numInstrumenters++;
      } catch (Exception | LinkageError e) {
        log.error("Unable to load instrumentation {}", instrumenter.getClass().getName(), e);
      }
    }

    for (InstrumentationModule instrumentationModule : loadInstrumentationModules()) {
      log.debug("Loading instrumentation {}", instrumentationModule.getClass().getName());
      try {
        agentBuilder = instrumentationModule.instrument(agentBuilder);
        numInstrumenters++;
      } catch (Exception | LinkageError e) {
        log.error(
            "Unable to load instrumentation {}", instrumentationModule.getClass().getName(), e);
      }
    }

    log.debug("Installed {} instrumenter(s)", numInstrumenters);
    return agentBuilder.installOn(inst);
  }

  private static Iterable<Instrumenter> orderInstrumenters(Iterable<Instrumenter> instrumenters) {
    List<Instrumenter> orderedInstrumenters = new ArrayList<>();
    instrumenters.forEach(orderedInstrumenters::add);
    orderedInstrumenters.sort(Comparator.comparingInt(Instrumenter::getOrder));
    return orderedInstrumenters;
  }

  private static List<InstrumentationModule> loadInstrumentationModules() {
    return SafeServiceLoader.load(
            InstrumentationModule.class, AgentInstaller.class.getClassLoader())
        .stream()
        .sorted(Comparator.comparingInt(InstrumentationModule::getOrder))
        .collect(Collectors.toList());
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
    List<String> excludedClasses = Config.get().getListProperty(EXCLUDED_CLASSES_CONFIG);
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

  private static List<String> loadBootstrapPackagePrefixes() {
    List<String> bootstrapPackages =
        new ArrayList<>(Arrays.asList(Constants.BOOTSTRAP_PACKAGE_PREFIXES));
    Iterable<BootstrapPackagesProvider> bootstrapPackagesProviders =
        SafeServiceLoader.load(
            BootstrapPackagesProvider.class, AgentInstaller.class.getClassLoader());
    for (BootstrapPackagesProvider provider : bootstrapPackagesProviders) {
      List<String> packagePrefixes = provider.getPackagePrefixes();
      log.debug(
          "Loaded bootstrap package prefixes from {}: {}",
          provider.getClass().getName(),
          packagePrefixes);
      bootstrapPackages.addAll(packagePrefixes);
    }
    return bootstrapPackages;
  }

  static class RedefinitionLoggingListener implements AgentBuilder.RedefinitionStrategy.Listener {

    private static final Logger log = LoggerFactory.getLogger(RedefinitionLoggingListener.class);

    @Override
    public void onBatch(int index, List<Class<?>> batch, List<Class<?>> types) {}

    @Override
    public Iterable<? extends List<Class<?>>> onError(
        int index, List<Class<?>> batch, Throwable throwable, List<Class<?>> types) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Exception while retransforming " + batch.size() + " classes: " + batch, throwable);
      }
      return Collections.emptyList();
    }

    @Override
    public void onComplete(
        int amount, List<Class<?>> types, Map<List<Class<?>>, Throwable> failures) {}
  }

  static class TransformLoggingListener implements AgentBuilder.Listener {

    private static final Logger log = LoggerFactory.getLogger(TransformLoggingListener.class);

    @Override
    public void onError(
        String typeName,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        Throwable throwable) {
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
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        DynamicType dynamicType) {
      log.debug("Transformed {} -- {}", typeDescription.getName(), classLoader);
    }

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded) {
      //      log.debug("onIgnored {}", typeDescription.getName());
    }

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
      // log.debug("onComplete {}", typeName);
    }

    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
      // log.debug("onDiscovery {}", typeName);
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
  public static void registerClassLoadCallback(String className, Runnable callback) {
    synchronized (CLASS_LOAD_CALLBACKS) {
      List<Runnable> callbacks = CLASS_LOAD_CALLBACKS.computeIfAbsent(className, k -> new ArrayList<>());
      callbacks.add(callback);
    }
  }

  private static class ClassLoadListener implements AgentBuilder.Listener {
    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule javaModule, boolean b) {}

    @Override
    public void onTransformation(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule javaModule,
        boolean b,
        DynamicType dynamicType) {}

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule javaModule,
        boolean b) {}

    @Override
    public void onError(
        String s, ClassLoader classLoader, JavaModule javaModule, boolean b, Throwable throwable) {}

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule javaModule, boolean b) {
      synchronized (CLASS_LOAD_CALLBACKS) {
        List<Runnable> callbacks = CLASS_LOAD_CALLBACKS.get(typeName);
        if (callbacks != null) {
          for (Runnable callback : callbacks) {
            callback.run();
          }
        }
      }
    }
  }

  private AgentInstaller() {}
}
