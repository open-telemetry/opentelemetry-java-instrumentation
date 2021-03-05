/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.javaagent.tooling.matcher.GlobalIgnoresMatcher.globalIgnoresMatcher;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.instrumentation.api.SafeServiceLoader;
import io.opentelemetry.javaagent.instrumentation.api.internal.BootstrapPackagePrefixesHolder;
import io.opentelemetry.javaagent.spi.BootstrapPackagesProvider;
import io.opentelemetry.javaagent.spi.ByteBuddyAgentCustomizer;
import io.opentelemetry.javaagent.spi.ComponentInstaller;
import io.opentelemetry.javaagent.spi.IgnoreMatcherProvider;
import io.opentelemetry.javaagent.tooling.config.ConfigInitializer;
import io.opentelemetry.javaagent.tooling.context.FieldBackedProvider;
import io.opentelemetry.javaagent.tooling.matcher.GlobalClassloaderIgnoresMatcher;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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

  private static final Logger log;

  private static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";
  private static final String EXCLUDED_CLASSES_CONFIG = "otel.javaagent.exclude-classes";

  // We set this system property when running the agent with unit tests to allow verifying that we
  // don't ignore libraries that we actually attempt to instrument. It means either the list is
  // wrong or a type matcher is.
  private static final String ADDITIONAL_LIBRARY_IGNORES_ENABLED =
      "otel.javaagent.testing.additional-library-ignores.enabled";

  private static final Map<String, List<Runnable>> CLASS_LOAD_CALLBACKS = new HashMap<>();
  private static volatile Instrumentation INSTRUMENTATION;

  public static Instrumentation getInstrumentation() {
    return INSTRUMENTATION;
  }

  static {
    LoggingConfigurer.configureLogger();
    log = LoggerFactory.getLogger(AgentInstaller.class);

    addByteBuddyRawSetting();
    BootstrapPackagePrefixesHolder.setBoostrapPackagePrefixes(loadBootstrapPackagePrefixes());
    // WeakMap is used by other classes below, so we need to register the provider first.
    AgentTooling.registerWeakMapProvider();
    // Instrumentation can use a bounded cache, so register here.
    AgentTooling.registerBoundedCacheProvider();
    // this needs to be done as early as possible - before the first Config.get() call
    ConfigInitializer.initialize();
  }

  public static void installBytebuddyAgent(Instrumentation inst) {
    logVersionInfo();
    if (Config.get().getBooleanProperty(JAVAAGENT_ENABLED_CONFIG, true)) {
      Iterable<ComponentInstaller> componentInstallers = loadComponentProviders();
      installBytebuddyAgent(inst, componentInstallers);
    } else {
      log.debug("Tracing is disabled, not installing instrumentations.");
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
      Instrumentation inst, Iterable<ComponentInstaller> componentInstallers) {

    installComponentsBeforeByteBuddy(componentInstallers);

    INSTRUMENTATION = inst;

    FieldBackedProvider.resetContextMatchers();

    IgnoreMatcherProvider ignoreMatcherProvider = loadIgnoreMatcherProvider();
    log.debug(
        "Ignore matcher provider {} will be used", ignoreMatcherProvider.getClass().getName());

    AgentBuilder.Ignored ignoredAgentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new RedefinitionDiscoveryStrategy())
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
            .with(AgentTooling.poolStrategy())
            .with(new ClassLoadListener())
            .with(AgentTooling.locationStrategy())
            // FIXME: we cannot enable it yet due to BB/JVM bug, see
            // https://github.com/raphw/byte-buddy/issues/558
            // .with(AgentBuilder.LambdaInstrumentationStrategy.ENABLED)
            .ignore(any(), GlobalClassloaderIgnoresMatcher.skipClassLoader(ignoreMatcherProvider));

    ignoredAgentBuilder =
        ignoredAgentBuilder.or(
            globalIgnoresMatcher(
                Config.get().getBooleanProperty(ADDITIONAL_LIBRARY_IGNORES_ENABLED, true),
                ignoreMatcherProvider));

    ignoredAgentBuilder = ignoredAgentBuilder.or(matchesConfiguredExcludes());

    AgentBuilder agentBuilder = ignoredAgentBuilder;
    if (log.isDebugEnabled()) {
      agentBuilder =
          agentBuilder
              .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
              .with(new RedefinitionDiscoveryStrategy())
              .with(new RedefinitionLoggingListener())
              .with(new TransformLoggingListener());
    }

    int numInstrumenters = 0;

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

    agentBuilder = customizeByteBuddyAgent(agentBuilder);
    log.debug("Installed {} instrumenter(s)", numInstrumenters);
    ResettableClassFileTransformer resettableClassFileTransformer = agentBuilder.installOn(inst);
    installComponentsAfterByteBuddy(componentInstallers);
    return resettableClassFileTransformer;
  }

  private static void installComponentsBeforeByteBuddy(
      Iterable<ComponentInstaller> componentInstallers) {
    Thread.currentThread().setContextClassLoader(AgentInstaller.class.getClassLoader());
    for (ComponentInstaller componentInstaller : componentInstallers) {
      componentInstaller.beforeByteBuddyAgent();
    }
  }

  private static void installComponentsAfterByteBuddy(
      Iterable<ComponentInstaller> componentInstallers) {
    /*
     * java.util.logging.LogManager maintains a final static LogManager, which is created during class initialization.
     *
     * JMXFetch uses jre bootstrap classes which touch this class. This means applications which require a custom log
     * manager may not have a chance to set the global log manager if jmxfetch runs first. JMXFetch will incorrectly
     * set the global log manager in cases where the app sets the log manager system property or when the log manager
     * class is not on the system classpath.
     *
     * Our solution is to delay the initialization of jmxfetch when we detect a custom log manager being used.
     *
     * Once we see the LogManager class loading, it's safe to start jmxfetch because the application is already setting
     * the global log manager and jmxfetch won't be able to touch it due to classloader locking.
     */

    /*
     * Similar thing happens with AgentTracer on (at least) zulu-8 because it uses OkHttp which indirectly loads JFR
     * events which in turn loads LogManager. This is not a problem on newer JDKs because there JFR uses different
     * logging facility.
     */
    boolean appUsingCustomLogManager = isAppUsingCustomLogManager();
    if (isJavaBefore9WithJfr() && appUsingCustomLogManager) {
      log.debug("Custom logger detected. Delaying Agent Tracer initialization.");
      registerClassLoadCallback(
          "java.util.logging.LogManager",
          new InstallComponentAfterByteBuddyCallback(componentInstallers));
    } else {
      for (ComponentInstaller componentInstaller : componentInstallers) {
        componentInstaller.afterByteBuddyAgent();
      }
    }
  }

  private static AgentBuilder customizeByteBuddyAgent(AgentBuilder agentBuilder) {
    Iterable<ByteBuddyAgentCustomizer> agentCustomizers = loadByteBuddyAgentCustomizers();
    for (ByteBuddyAgentCustomizer agentCustomizer : agentCustomizers) {
      log.debug("Applying agent builder customizer {}", agentCustomizer.getClass().getName());
      agentBuilder = agentCustomizer.customize(agentBuilder);
    }
    return agentBuilder;
  }

  private static Iterable<ComponentInstaller> loadComponentProviders() {
    return ServiceLoader.load(ComponentInstaller.class, AgentInstaller.class.getClassLoader());
  }

  private static IgnoreMatcherProvider loadIgnoreMatcherProvider() {
    ServiceLoader<IgnoreMatcherProvider> ignoreMatcherProviders =
        ServiceLoader.load(IgnoreMatcherProvider.class, AgentInstaller.class.getClassLoader());

    Iterator<IgnoreMatcherProvider> iterator = ignoreMatcherProviders.iterator();
    if (iterator.hasNext()) {
      return iterator.next();
    }
    return new NoopIgnoreMatcherProvider();
  }

  private static Iterable<ByteBuddyAgentCustomizer> loadByteBuddyAgentCustomizers() {
    return ServiceLoader.load(
        ByteBuddyAgentCustomizer.class, AgentInstaller.class.getClassLoader());
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

    private static final TransformSafeLogger log =
        TransformSafeLogger.getLogger(TransformLoggingListener.class);

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

  protected static class InstallComponentAfterByteBuddyCallback extends ClassLoadCallBack {

    private final Iterable<ComponentInstaller> componentInstallers;

    protected InstallComponentAfterByteBuddyCallback(
        Iterable<ComponentInstaller> componentInstallers) {
      this.componentInstallers = componentInstallers;
    }

    @Override
    public String getName() {
      return componentInstallers.getClass().getName();
    }

    @Override
    public void execute() {
      for (ComponentInstaller componentInstaller : componentInstallers) {
        componentInstaller.afterByteBuddyAgent();
      }
    }
  }

  protected abstract static class ClassLoadCallBack implements Runnable {

    @Override
    public void run() {
      /*
       * This callback is called from within bytecode transformer. This can be a problem if callback tries
       * to load classes being transformed. To avoid this we start a thread here that calls the callback.
       * This seems to resolve this problem.
       */
      Thread thread =
          new Thread(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    execute();
                  } catch (Exception e) {
                    log.error("Failed to run class loader callback {}", getName(), e);
                  }
                }
              });
      thread.setName("agent-startup-" + getName());
      thread.setDaemon(true);
      thread.start();
    }

    public abstract String getName();

    public abstract void execute();
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
      return () -> streamOf(delegate.resolve(instrumentation)).map(this::filterClasses).iterator();
    }

    private Iterable<Class<?>> filterClasses(Iterable<Class<?>> classes) {
      return () -> streamOf(classes).filter(c -> !isIgnored(c)).iterator();
    }

    private static <T> Stream<T> streamOf(Iterable<T> iterable) {
      return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static boolean isIgnored(Class<?> c) {
      ClassLoader cl = c.getClassLoader();
      if (cl != null && cl.getClass() == AgentClassLoader.class) {
        return true;
      }

      return HelperInjector.isInjectedClass(c);
    }
  }

  /**
   * Search for java or agent-tracer sysprops which indicate that a custom log manager will be used.
   * Also search for any app classes known to set a custom log manager.
   *
   * @return true if we detect a custom log manager being used.
   */
  private static boolean isAppUsingCustomLogManager() {
    String tracerCustomLogManSysprop = "otel.app.customlogmanager";
    String customLogManagerProp = System.getProperty(tracerCustomLogManSysprop);
    String customLogManagerEnv =
        System.getenv(tracerCustomLogManSysprop.replace('.', '_').toUpperCase());

    if (customLogManagerProp != null || customLogManagerEnv != null) {
      log.debug("Prop - customlogmanager: " + customLogManagerProp);
      log.debug("Env - customlogmanager: " + customLogManagerEnv);
      // Allow setting to skip these automatic checks:
      return Boolean.parseBoolean(customLogManagerProp)
          || Boolean.parseBoolean(customLogManagerEnv);
    }

    String jbossHome = System.getenv("JBOSS_HOME");
    if (jbossHome != null) {
      log.debug("Env - jboss: " + jbossHome);
      // JBoss/Wildfly is known to set a custom log manager after startup.
      // Originally we were checking for the presence of a jboss class,
      // but it seems some non-jboss applications have jboss classes on the classpath.
      // This would cause jmxfetch initialization to be delayed indefinitely.
      // Checking for an environment variable required by jboss instead.
      return true;
    }

    String logManagerProp = System.getProperty("java.util.logging.manager");
    if (logManagerProp != null) {
      boolean onSysClasspath =
          ClassLoader.getSystemResource(logManagerProp.replaceAll("\\.", "/") + ".class") != null;
      log.debug("Prop - logging.manager: " + logManagerProp);
      log.debug("logging.manager on system classpath: " + onSysClasspath);
      // Some applications set java.util.logging.manager but never actually initialize the logger.
      // Check to see if the configured manager is on the system classpath.
      // If so, it should be safe to initialize jmxfetch which will setup the log manager.
      return !onSysClasspath;
    }

    return false;
  }

  private static boolean isJavaBefore9WithJfr() {
    if (!AgentInitializer.isJavaBefore9()) {
      return false;
    }
    // FIXME: this is quite a hack because there maybe jfr classes on classpath somehow that have
    // nothing to do with JDK but this should be safe because only thing this does is to delay
    // tracer install
    String jfrClassResourceName = "jdk.jfr.Recording".replace('.', '/') + ".class";
    return Thread.currentThread().getContextClassLoader().getResource(jfrClassResourceName) != null;
  }

  private static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }

  private static class NoopIgnoreMatcherProvider implements IgnoreMatcherProvider {
    @Override
    public Result classloader(ClassLoader classLoader) {
      return Result.DEFAULT;
    }

    @Override
    public Result type(TypeDescription target) {
      return Result.DEFAULT;
    }
  }

  private AgentInstaller() {}
}
