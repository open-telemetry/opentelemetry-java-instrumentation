/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.bootstrap.AgentInitializer.isJavaBefore9;
import static io.opentelemetry.javaagent.tooling.SafeServiceLoader.load;
import static io.opentelemetry.javaagent.tooling.SafeServiceLoader.loadOrdered;
import static io.opentelemetry.javaagent.tooling.Utils.getResourceName;
import static net.bytebuddy.matcher.ElementMatchers.any;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import io.opentelemetry.javaagent.bootstrap.BootstrapPackagePrefixesHolder;
import io.opentelemetry.javaagent.bootstrap.ClassFileTransformerHolder;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.extension.bootstrap.BootstrapPackagesConfigurer;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.api.internal.InstrumentedTaskClasses;
import io.opentelemetry.javaagent.tooling.asyncannotationsupport.WeakRefAsyncOperationEndStrategies;
import io.opentelemetry.javaagent.tooling.bootstrap.BootstrapPackagesBuilderImpl;
import io.opentelemetry.javaagent.tooling.config.ConfigInitializer;
import io.opentelemetry.javaagent.tooling.field.FieldBackedImplementationInstaller;
import io.opentelemetry.javaagent.tooling.ignore.IgnoredClassLoadersMatcher;
import io.opentelemetry.javaagent.tooling.ignore.IgnoredTypesBuilderImpl;
import io.opentelemetry.javaagent.tooling.ignore.IgnoredTypesMatcher;
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentInstaller {

  private static final Logger logger;

  private static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";

  // This property may be set to force synchronous AgentListener#afterAgent() execution: the
  // condition for delaying the AgentListener initialization is pretty broad and in case it covers
  // too much javaagent users can file a bug, force sync execution by setting this property to true
  // and continue using the javaagent
  private static final String FORCE_SYNCHRONOUS_AGENT_LISTENERS_CONFIG =
      "otel.javaagent.experimental.force-synchronous-agent-listeners";

  private static final String STRICT_CONTEXT_STRESSOR_MILLIS =
      "otel.javaagent.testing.strict-context-stressor-millis";

  private static final Map<String, List<Runnable>> CLASS_LOAD_CALLBACKS = new HashMap<>();

  static {
    LoggingConfigurer.configureLogger();
    logger = LoggerFactory.getLogger(AgentInstaller.class);

    addByteBuddyRawSetting();
    // this needs to be done as early as possible - before the first Config.get() call
    ConfigInitializer.initialize();

    // ensure java.lang.reflect.Proxy is loaded, as transformation code uses it internally
    // loading java.lang.reflect.Proxy after the bytebuddy transformer is set up causes
    // the internal-proxy instrumentation module to transform it, and then the bytebuddy
    // transformation code also tries to load it, which leads to a ClassCircularityError
    // loading java.lang.reflect.Proxy early here still allows it to be retransformed by the
    // internal-proxy instrumentation module after the bytebuddy transformer is set up
    Proxy.class.getName();

    // caffeine can trigger first access of ForkJoinPool under transform(), which leads ForkJoinPool
    // not to get transformed itself.
    // loading it early here still allows it to be retransformed as part of agent installation below
    ForkJoinPool.class.getName();

    // caffeine uses AtomicReferenceArray, ensure it is loaded to avoid ClassCircularityError during
    // transform.
    AtomicReferenceArray.class.getName();

    Integer strictContextStressorMillis = Integer.getInteger(STRICT_CONTEXT_STRESSOR_MILLIS);
    if (strictContextStressorMillis != null) {
      io.opentelemetry.context.ContextStorage.addWrapper(
          storage -> new StrictContextStressor(storage, strictContextStressorMillis));
    }
  }

  public static void installBytebuddyAgent(Instrumentation inst) {
    logVersionInfo();
    Config config = Config.get();
    if (config.getBoolean(JAVAAGENT_ENABLED_CONFIG, true)) {
      setupUnsafe(inst);
      List<AgentListener> agentListeners = loadOrdered(AgentListener.class);
      installBytebuddyAgent(inst, agentListeners);
    } else {
      logger.debug("Tracing is disabled, not installing instrumentations.");
    }
  }

  /**
   * Install the core bytebuddy agent along with all implementations of {@link
   * InstrumentationModule}.
   *
   * @param inst Java Instrumentation used to install bytebuddy
   * @return the agent's class transformer
   */
  public static ResettableClassFileTransformer installBytebuddyAgent(
      Instrumentation inst, Iterable<AgentListener> agentListeners) {

    WeakRefAsyncOperationEndStrategies.initialize();

    Config config = Config.get();

    setBootstrapPackages(config);

    runBeforeAgentListeners(agentListeners, config);

    FieldBackedImplementationInstaller.resetContextMatchers();

    AgentBuilder agentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new RedefinitionDiscoveryStrategy())
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
            .with(AgentTooling.poolStrategy())
            .with(new ClassLoadListener())
            .with(AgentTooling.locationStrategy(Utils.getBootstrapProxy()));

    agentBuilder = configureIgnoredTypes(config, agentBuilder);

    if (logger.isDebugEnabled()) {
      agentBuilder =
          agentBuilder
              .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
              .with(new RedefinitionDiscoveryStrategy())
              .with(new RedefinitionLoggingListener())
              .with(new TransformLoggingListener());
    }

    int numberOfLoadedExtensions = 0;
    for (AgentExtension agentExtension : loadOrdered(AgentExtension.class)) {
      logger.debug(
          "Loading extension {} [class {}]",
          agentExtension.extensionName(),
          agentExtension.getClass().getName());
      try {
        agentBuilder = agentExtension.extend(agentBuilder);
        numberOfLoadedExtensions++;
      } catch (Exception | LinkageError e) {
        logger.error(
            "Unable to load extension {} [class {}]",
            agentExtension.extensionName(),
            agentExtension.getClass().getName(),
            e);
      }
    }
    logger.debug("Installed {} extension(s)", numberOfLoadedExtensions);

    ResettableClassFileTransformer resettableClassFileTransformer = agentBuilder.installOn(inst);
    ClassFileTransformerHolder.setClassFileTransformer(resettableClassFileTransformer);
    runAfterAgentListeners(agentListeners, config);
    return resettableClassFileTransformer;
  }

  private static void setupUnsafe(Instrumentation inst) {
    try {
      UnsafeInitializer.initialize(inst, AgentInstaller.class.getClassLoader());
    } catch (UnsupportedClassVersionError exception) {
      // ignore
    }
  }

  private static void setBootstrapPackages(Config config) {
    BootstrapPackagesBuilderImpl builder = new BootstrapPackagesBuilderImpl();
    for (BootstrapPackagesConfigurer configurer : load(BootstrapPackagesConfigurer.class)) {
      configurer.configure(config, builder);
    }
    BootstrapPackagePrefixesHolder.setBoostrapPackagePrefixes(builder.build());
  }

  private static void runBeforeAgentListeners(
      Iterable<AgentListener> agentListeners, Config config) {
    for (AgentListener agentListener : agentListeners) {
      agentListener.beforeAgent(config);
    }
  }

  private static AgentBuilder configureIgnoredTypes(Config config, AgentBuilder agentBuilder) {
    IgnoredTypesBuilderImpl builder = new IgnoredTypesBuilderImpl();
    for (IgnoredTypesConfigurer configurer : loadOrdered(IgnoredTypesConfigurer.class)) {
      configurer.configure(config, builder);
    }

    InstrumentedTaskClasses.setIgnoredTaskClasses(builder.buildIgnoredTasksTrie());

    return agentBuilder
        .ignore(any(), new IgnoredClassLoadersMatcher(builder.buildIgnoredClassLoadersTrie()))
        .or(new IgnoredTypesMatcher(builder.buildIgnoredTypesTrie()));
  }

  private static void runAfterAgentListeners(
      Iterable<AgentListener> agentListeners, Config config) {
    // java.util.logging.LogManager maintains a final static LogManager, which is created during
    // class initialization. Some AgentListener implementations may use JRE bootstrap classes
    // which touch this class (e.g. JFR classes or some MBeans).
    // It is worth noting that starting from Java 9 (JEP 264) Java platform classes no longer use
    // JUL directly, but instead they use a new System.Logger interface, so the LogManager issue
    // applies mainly to Java 8.
    // This means applications which require a custom LogManager may not have a chance to set the
    // global LogManager if one of those AgentListeners runs first: it will incorrectly
    // set the global LogManager to the default JVM one in cases where the instrumented application
    // sets the LogManager system property or when the custom LogManager class is not on the system
    // classpath.
    // Our solution is to delay the initialization of AgentListeners when we detect a custom
    // log manager being used.
    // Once we see the LogManager class loading, it's safe to run AgentListener#afterAgent() because
    // the application is already setting the global LogManager and AgentListener won't be able
    // to touch it due to classloader locking.
    boolean shouldForceSynchronousAgentListenersCalls =
        Config.get().getBoolean(FORCE_SYNCHRONOUS_AGENT_LISTENERS_CONFIG, false);
    if (!shouldForceSynchronousAgentListenersCalls
        && isJavaBefore9()
        && isAppUsingCustomLogManager()) {
      logger.debug("Custom JUL LogManager detected: delaying AgentListener#afterAgent() calls");
      registerClassLoadCallback(
          "java.util.logging.LogManager", new DelayedAfterAgentCallback(config, agentListeners));
    } else {
      for (AgentListener agentListener : agentListeners) {
        agentListener.afterAgent(config);
      }
    }
  }

  private static void addByteBuddyRawSetting() {
    String savedPropertyValue = System.getProperty(TypeDefinition.RAW_TYPES_PROPERTY);
    try {
      System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, "true");
      boolean rawTypes = TypeDescription.AbstractBase.RAW_TYPES;
      if (!rawTypes) {
        logger.debug("Too late to enable {}", TypeDefinition.RAW_TYPES_PROPERTY);
      }
    } finally {
      if (savedPropertyValue == null) {
        System.clearProperty(TypeDefinition.RAW_TYPES_PROPERTY);
      } else {
        System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, savedPropertyValue);
      }
    }
  }

  static class RedefinitionLoggingListener implements AgentBuilder.RedefinitionStrategy.Listener {

    private static final Logger logger = LoggerFactory.getLogger(RedefinitionLoggingListener.class);

    @Override
    public void onBatch(int index, List<Class<?>> batch, List<Class<?>> types) {}

    @Override
    public Iterable<? extends List<Class<?>>> onError(
        int index, List<Class<?>> batch, Throwable throwable, List<Class<?>> types) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Exception while retransforming {} classes: {}", batch.size(), batch, throwable);
      }
      return Collections.emptyList();
    }

    @Override
    public void onComplete(
        int amount, List<Class<?>> types, Map<List<Class<?>>, Throwable> failures) {}
  }

  static class TransformLoggingListener implements AgentBuilder.Listener {

    private static final TransformSafeLogger logger =
        TransformSafeLogger.getLogger(TransformLoggingListener.class);

    @Override
    public void onError(
        String typeName,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        Throwable throwable) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Failed to handle {} for transformation on classloader {}",
            typeName,
            classLoader,
            throwable);
      }
    }

    @Override
    public void onTransformation(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        DynamicType dynamicType) {
      logger.debug("Transformed {} -- {}", typeDescription.getName(), classLoader);
    }

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded) {}

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}

    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
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
      List<Runnable> callbacks =
          CLASS_LOAD_CALLBACKS.computeIfAbsent(className, k -> new ArrayList<>());
      callbacks.add(callback);
    }
  }

  private static class DelayedAfterAgentCallback implements Runnable {
    private final Iterable<AgentListener> agentListeners;
    private final Config config;

    private DelayedAfterAgentCallback(Config config, Iterable<AgentListener> agentListeners) {
      this.agentListeners = agentListeners;
      this.config = config;
    }

    @Override
    public void run() {
      /*
       * This callback is called from within bytecode transformer. This can be a problem if callback tries
       * to load classes being transformed. To avoid this we start a thread here that calls the callback.
       * This seems to resolve this problem.
       */
      Thread thread = new Thread(this::runAgentListeners);
      thread.setName("delayed-agent-listeners");
      thread.setDaemon(true);
      thread.start();
    }

    private void runAgentListeners() {
      for (AgentListener agentListener : agentListeners) {
        try {
          agentListener.afterAgent(config);
        } catch (RuntimeException e) {
          logger.error("Failed to execute {}", agentListener.getClass().getName(), e);
        }
      }
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

  private static class RedefinitionDiscoveryStrategy
      implements AgentBuilder.RedefinitionStrategy.DiscoveryStrategy {
    private static final AgentBuilder.RedefinitionStrategy.DiscoveryStrategy delegate =
        AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE;

    @Override
    public Iterable<Iterable<Class<?>>> resolve(Instrumentation instrumentation) {
      // filter out our agent classes and injected helper classes
      return () ->
          streamOf(delegate.resolve(instrumentation))
              .map(RedefinitionDiscoveryStrategy::filterClasses)
              .iterator();
    }

    private static Iterable<Class<?>> filterClasses(Iterable<Class<?>> classes) {
      return () -> streamOf(classes).filter(c -> !isIgnored(c)).iterator();
    }

    private static <T> Stream<T> streamOf(Iterable<T> iterable) {
      return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static boolean isIgnored(Class<?> c) {
      ClassLoader cl = c.getClassLoader();
      if (cl instanceof AgentClassLoader || cl instanceof ExtensionClassLoader) {
        return true;
      }
      // ignore generate byte buddy helper class
      if (c.getName().startsWith("java.lang.ClassLoader$ByteBuddyAccessor$")) {
        return true;
      }

      return HelperInjector.isInjectedClass(c);
    }
  }

  /** Detect if the instrumented application is using a custom JUL LogManager. */
  private static boolean isAppUsingCustomLogManager() {
    String jbossHome = System.getenv("JBOSS_HOME");
    if (jbossHome != null) {
      logger.debug("Found JBoss: {}; assuming app is using custom LogManager", jbossHome);
      // JBoss/Wildfly is known to set a custom log manager after startup.
      // Originally we were checking for the presence of a jboss class,
      // but it seems some non-jboss applications have jboss classes on the classpath.
      // This would cause AgentListener#afterAgent() calls to be delayed indefinitely.
      // Checking for an environment variable required by jboss instead.
      return true;
    }

    String customLogManager = System.getProperty("java.util.logging.manager");
    if (customLogManager != null) {
      logger.debug(
          "Detected custom LogManager configuration: java.util.logging.manager={}",
          customLogManager);
      boolean onSysClasspath =
          ClassLoader.getSystemResource(getResourceName(customLogManager)) != null;
      logger.debug(
          "Class {} is on system classpath: {}delaying AgentInstaller#afterAgent()",
          customLogManager,
          onSysClasspath ? "not " : "");
      // Some applications set java.util.logging.manager but never actually initialize the logger.
      // Check to see if the configured manager is on the system classpath.
      // If so, it should be safe to initialize AgentInstaller which will setup the log manager:
      // LogManager tries to load the implementation first using system CL, then falls back to
      // current context CL
      return !onSysClasspath;
    }

    return false;
  }

  private static void logVersionInfo() {
    VersionLogger.logAllVersions();
    logger.debug(
        "{} loaded on {}", AgentInstaller.class.getName(), AgentInstaller.class.getClassLoader());
  }

  private AgentInstaller() {}

  private static class StrictContextStressor implements ContextStorage, AutoCloseable {

    private final ContextStorage contextStorage;
    private final int sleepMillis;

    private StrictContextStressor(ContextStorage contextStorage, int sleepMillis) {
      this.contextStorage = contextStorage;
      this.sleepMillis = sleepMillis;
    }

    @Override
    public Scope attach(Context toAttach) {
      return wrap(contextStorage.attach(toAttach));
    }

    @Nullable
    @Override
    public Context current() {
      return contextStorage.current();
    }

    @Override
    public void close() throws Exception {
      if (contextStorage instanceof AutoCloseable) {
        ((AutoCloseable) contextStorage).close();
      }
    }

    private Scope wrap(Scope scope) {
      return () -> {
        try {
          Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        scope.close();
      };
    }
  }
}
