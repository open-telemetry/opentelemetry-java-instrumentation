/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.tooling.bytebuddy.AgentTransformers;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import io.opentelemetry.javaagent.tooling.context.FieldBackedProvider;
import io.opentelemetry.javaagent.tooling.context.InstrumentationContextProvider;
import io.opentelemetry.javaagent.tooling.context.NoopContextProvider;
import io.opentelemetry.javaagent.tooling.muzzle.Reference;
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceMatcher;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in bytebuddy-based instrumentation for the agent.
 *
 * <p>It is strongly recommended to extend {@link Default} rather than implement this interface
 * directly.
 */
public interface Instrumenter {
  /**
   * Add this instrumentation to an AgentBuilder.
   *
   * @param agentBuilder AgentBuilder to base instrumentation config off of.
   * @return the original agentBuilder and this instrumentation
   */
  AgentBuilder instrument(AgentBuilder agentBuilder);

  /**
   * Order of adding instrumentation to ByteBuddy. For example instrumentation with order 1 runs
   * after an instrumentation with order 0 (default) matched on the same API.
   *
   * @return the order of adding an instrumentation to ByteBuddy. Default value is 0 - no order.
   */
  int getOrder();

  abstract class Default implements Instrumenter {

    private static final Logger log = LoggerFactory.getLogger(Default.class);

    private static final String[] EMPTY = new String[0];

    // Added here instead of AgentInstaller's ignores because it's relatively
    // expensive. https://github.com/DataDog/dd-trace-java/pull/1045
    public static final Junction<AnnotationSource> NOT_DECORATOR_MATCHER =
        not(isAnnotatedWith(named("javax.decorator.Decorator")));

    private final SortedSet<String> instrumentationNames;
    private final String instrumentationPrimaryName;
    private final InstrumentationContextProvider contextProvider;
    protected final boolean enabled;

    protected final String packageName =
        getClass().getPackage() == null ? "" : getClass().getPackage().getName();

    public Default(String instrumentationName, String... additionalNames) {
      instrumentationNames = new TreeSet<>(Arrays.asList(additionalNames));
      instrumentationNames.add(instrumentationName);
      instrumentationPrimaryName = instrumentationName;

      enabled = Config.get().isIntegrationEnabled(instrumentationNames, defaultEnabled());
      Map<String, String> contextStore = contextStore();
      if (!contextStore.isEmpty()) {
        contextProvider = new FieldBackedProvider(this, contextStore);
      } else {
        contextProvider = NoopContextProvider.INSTANCE;
      }
    }

    @Override
    public final AgentBuilder instrument(AgentBuilder parentAgentBuilder) {
      if (!enabled) {
        log.debug("Instrumentation {} is disabled", this);
        return parentAgentBuilder;
      }

      AgentBuilder.Identified.Extendable agentBuilder =
          parentAgentBuilder
              .type(
                  failSafe(
                      typeMatcher(),
                      "Instrumentation type matcher unexpected exception: " + getClass().getName()),
                  failSafe(
                      classLoaderMatcher(),
                      "Instrumentation class loader matcher unexpected exception: "
                          + getClass().getName()))
              .and(NOT_DECORATOR_MATCHER)
              .and(new MuzzleMatcher())
              .transform(AgentTransformers.defaultTransformers());
      agentBuilder = injectHelperClasses(agentBuilder);
      agentBuilder = contextProvider.instrumentationTransformer(agentBuilder);
      agentBuilder = applyInstrumentationTransformers(agentBuilder);
      agentBuilder = contextProvider.additionalInstrumentation(agentBuilder);
      return agentBuilder;
    }

    private AgentBuilder.Identified.Extendable injectHelperClasses(
        AgentBuilder.Identified.Extendable agentBuilder) {
      String[] helperClassNames = helperClassNames();
      String[] helperResourceNames = helperResourceNames();
      if (helperClassNames.length > 0 || helperResourceNames.length > 0) {
        agentBuilder =
            agentBuilder.transform(
                new HelperInjector(
                    getClass().getSimpleName(),
                    Arrays.asList(helperClassNames),
                    Arrays.asList(helperResourceNames)));
      }
      return agentBuilder;
    }

    private AgentBuilder.Identified.Extendable applyInstrumentationTransformers(
        AgentBuilder.Identified.Extendable agentBuilder) {
      for (Map.Entry<? extends ElementMatcher, String> entry : transformers().entrySet()) {
        agentBuilder =
            agentBuilder.transform(
                new AgentBuilder.Transformer.ForAdvice()
                    .include(Utils.getBootstrapProxy(), Utils.getAgentClassLoader())
                    .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                    .advice(entry.getKey(), entry.getValue()));
      }
      return agentBuilder;
    }

    /** @return 0 - default order. */
    @Override
    public int getOrder() {
      return 0;
    }

    /** Matches classes for which instrumentation is not muzzled. */
    private class MuzzleMatcher implements AgentBuilder.RawMatcher {
      @Override
      public boolean matches(
          TypeDescription typeDescription,
          ClassLoader classLoader,
          JavaModule module,
          Class<?> classBeingRedefined,
          ProtectionDomain protectionDomain) {
        /* Optimization: calling getInstrumentationMuzzle() inside this method
         * prevents unnecessary loading of muzzle references during agentBuilder
         * setup.
         */
        ReferenceMatcher muzzle = getInstrumentationMuzzle();
        if (null != muzzle) {
          boolean isMatch = muzzle.matches(classLoader);
          if (!isMatch) {
            if (log.isDebugEnabled()) {
              List<Reference.Mismatch> mismatches =
                  muzzle.getMismatchedReferenceSources(classLoader);
              if (log.isDebugEnabled()) {
                log.debug(
                    "Instrumentation muzzled: {} -- {} on {}",
                    instrumentationNames,
                    Instrumenter.Default.this.getClass().getName(),
                    classLoader);
              }
              for (Reference.Mismatch mismatch : mismatches) {
                log.debug("-- {}", mismatch);
              }
            }
          } else {
            if (log.isDebugEnabled()) {
              log.debug(
                  "Applying instrumentation: {} -- {} on {}",
                  instrumentationPrimaryName,
                  Instrumenter.Default.this.getClass().getName(),
                  classLoader);
            }
          }
          return isMatch;
        }
        return true;
      }
    }

    /**
     * This method is implemented dynamically by compile-time bytecode transformations.
     *
     * <p>{@see io.opentelemetry.javaagent.tooling.muzzle.MuzzleGradlePlugin}
     */
    protected ReferenceMatcher getInstrumentationMuzzle() {
      return null;
    }

    /** @return Class names of helpers to inject into the user's classloader */
    public String[] helperClassNames() {
      return EMPTY;
    }

    /** @return Resource names to inject into the user's classloader */
    public String[] helperResourceNames() {
      return EMPTY;
    }

    /** @return A type matcher used to match the classloader under transform */
    public ElementMatcher<ClassLoader> classLoaderMatcher() {
      return any();
    }

    /** @return A type matcher used to match the class under transform. */
    public abstract ElementMatcher<? super TypeDescription> typeMatcher();

    /** @return A map of matcher->advice */
    public abstract Map<? extends ElementMatcher<? super MethodDescription>, String> transformers();

    /**
     * Context stores to define for this instrumentation.
     *
     * <p>A map of {class-name -> context-class-name}. Keys (and their subclasses) will be
     * associated with a context of the value.
     */
    public Map<String, String> contextStore() {
      return Collections.emptyMap();
    }

    protected boolean defaultEnabled() {
      return Config.get().getBooleanProperty("otel.integrations.enabled", true);
    }
  }
}
