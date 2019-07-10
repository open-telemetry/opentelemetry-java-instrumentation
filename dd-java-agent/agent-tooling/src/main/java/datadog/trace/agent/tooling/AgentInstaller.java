package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.skipClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;

import datadog.trace.api.Config;
import datadog.trace.bootstrap.WeakMap;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

@Slf4j
public class AgentInstaller {
  private static final Map<String, Runnable> classLoadCallbacks = new ConcurrentHashMap<>();

  static {
    // WeakMap is used by other classes below, so we need to register the provider first.
    registerWeakMapProvider();
  }

  public static final DDLocationStrategy LOCATION_STRATEGY = new DDLocationStrategy();
  public static final AgentBuilder.PoolStrategy POOL_STRATEGY = new DDCachingPoolStrategy();
  private static volatile Instrumentation INSTRUMENTATION;

  public static Instrumentation getInstrumentation() {
    return INSTRUMENTATION;
  }

  public static void installBytebuddyAgent(final Instrumentation inst) {
    if (Config.get().isTraceEnabled()) {
      installBytebuddyAgent(inst, new AgentBuilder.Listener[0]);
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
      final Instrumentation inst, final AgentBuilder.Listener... listeners) {
    INSTRUMENTATION = inst;

    AgentBuilder agentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new RedefinitionLoggingListener())
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
            .with(POOL_STRATEGY)
            .with(new TransformLoggingListener())
            .with(new ClassLoadListener())
            .with(LOCATION_STRATEGY)
            // FIXME: we cannot enable it yet due to BB/JVM bug, see
            // https://github.com/raphw/byte-buddy/issues/558
            // .with(AgentBuilder.LambdaInstrumentationStrategy.ENABLED)
            .ignore(any(), skipClassLoader())
            // Unlikely to ever need to instrument an annotation:
            .or(ElementMatchers.<TypeDescription>isAnnotation())
            // Unlikely to ever need to instrument an enum:
            .or(ElementMatchers.<TypeDescription>isEnum())
            .or(
                nameStartsWith("datadog.trace.")
                    // FIXME: We should remove this once
                    // https://github.com/raphw/byte-buddy/issues/558 is fixed
                    .and(
                        not(
                            named(
                                    "datadog.trace.bootstrap.instrumentation.java.concurrent.RunnableWrapper")
                                .or(
                                    named(
                                        "datadog.trace.bootstrap.instrumentation.java.concurrent.CallableWrapper")))))
            .or(nameStartsWith("datadog.opentracing."))
            .or(nameStartsWith("datadog.slf4j."))
            .or(nameStartsWith("net.bytebuddy."))
            .or(
                nameStartsWith("java.")
                    .and(
                        not(
                            named("java.net.URL")
                                .or(named("java.net.HttpURLConnection"))
                                .or(nameStartsWith("java.util.concurrent."))
                                .or(
                                    nameStartsWith("java.util.logging.")
                                        // Concurrent instrumentation modifies the strucutre of
                                        // Cleaner class incompaibly with java9+ modules.
                                        // Working around until a long-term fix for modules can be
                                        // put in place.
                                        .and(not(named("java.util.logging.LogManager$Cleaner")))))))
            .or(
                nameStartsWith("com.sun.")
                    .and(
                        not(
                            nameStartsWith("com.sun.messaging.")
                                .or(nameStartsWith("com.sun.jersey.api.client")))))
            .or(
                nameStartsWith("sun.")
                    .and(
                        not(
                            nameStartsWith("sun.net.www.protocol.")
                                .or(named("sun.net.www.http.HttpClient")))))
            .or(nameStartsWith("jdk."))
            .or(nameStartsWith("org.aspectj."))
            .or(nameStartsWith("org.groovy."))
            .or(nameStartsWith("org.codehaus.groovy.macro."))
            .or(nameStartsWith("com.intellij.rt.debugger."))
            .or(nameStartsWith("com.p6spy."))
            .or(nameStartsWith("com.newrelic."))
            .or(nameStartsWith("com.dynatrace."))
            .or(nameStartsWith("com.jloadtrace."))
            .or(nameStartsWith("com.appdynamics."))
            .or(nameStartsWith("com.singularity."))
            .or(nameStartsWith("com.jinspired."))
            .or(nameStartsWith("org.jinspired."))
            .or(nameStartsWith("org.apache.log4j.").and(not(named("org.apache.log4j.MDC"))))
            .or(nameStartsWith("org.slf4j.").and(not(named("org.slf4j.MDC"))))
            .or(nameContains("$JaxbAccessor"))
            .or(nameContains("CGLIB$$"))
            .or(nameContains("javassist"))
            .or(nameContains(".asm."))
            .or(nameMatches("com\\.mchange\\.v2\\.c3p0\\..*Proxy"))
            .or(matchesConfiguredExcludes());

    for (final AgentBuilder.Listener listener : listeners) {
      agentBuilder = agentBuilder.with(listener);
    }
    int numInstrumenters = 0;
    for (final Instrumenter instrumenter : ServiceLoader.load(Instrumenter.class)) {
      log.debug("Loading instrumentation {}", instrumenter.getClass().getName());
      agentBuilder = instrumenter.instrument(agentBuilder);
      numInstrumenters++;
    }
    log.debug("Installed {} instrumenter(s)", numInstrumenters);

    return agentBuilder.installOn(inst);
  }

  private static ElementMatcher.Junction<Object> matchesConfiguredExcludes() {
    final List<String> excludedClasses = Config.get().getExcludedClasses();
    ElementMatcher.Junction matcher = none();
    for (String excludedClass : excludedClasses) {
      excludedClass = excludedClass.trim();
      if (excludedClass.endsWith("*")) {
        // remove the trailing *
        final String prefix = excludedClass.substring(0, excludedClass.length() - 1);
        matcher = matcher.or(nameStartsWith(prefix));
      } else {
        matcher = matcher.or(named(excludedClass));
      }
    }
    return matcher;
  }

  private static void registerWeakMapProvider() {
    if (!WeakMap.Provider.isProviderRegistered()) {
      WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent());
    }
    //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent.Inline());
    //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.Guava());
  }

  @Slf4j
  static class RedefinitionLoggingListener implements AgentBuilder.RedefinitionStrategy.Listener {

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

  @Slf4j
  static class TransformLoggingListener implements AgentBuilder.Listener {

    @Override
    public void onError(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final Throwable throwable) {
      log.debug(
          "Failed to handle {} for transformation on classloader {}: {}",
          typeName,
          classLoader,
          throwable.getMessage());
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
   * <p>Caveats: 1: This callback will be invoked by a jvm class transformer. 2: Classes filtered
   * out by {@link AgentInstaller}'s skip list will not be matched.
   *
   * @param className name of the class to match against
   * @param classLoadCallback runnable to invoke when class name matches
   */
  public static void registerClassLoadCallback(
      final String className, final Runnable classLoadCallback) {
    classLoadCallbacks.put(className, classLoadCallback);
  }

  private static class ClassLoadListener implements AgentBuilder.Listener {
    @Override
    public void onDiscovery(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule javaModule,
        final boolean b) {
      for (final Map.Entry<String, Runnable> entry : classLoadCallbacks.entrySet()) {
        if (entry.getKey().equals(typeName)) {
          entry.getValue().run();
        }
      }
    }

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
        final String s,
        final ClassLoader classLoader,
        final JavaModule javaModule,
        final boolean b) {}
  }

  private AgentInstaller() {}
}
