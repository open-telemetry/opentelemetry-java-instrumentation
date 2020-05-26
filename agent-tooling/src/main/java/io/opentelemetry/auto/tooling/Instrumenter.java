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

import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.tooling.bytebuddy.AgentTransformers;
import io.opentelemetry.auto.tooling.bytebuddy.ExceptionHandlers;
import io.opentelemetry.auto.tooling.context.FieldBackedProvider;
import io.opentelemetry.auto.tooling.context.InstrumentationContextProvider;
import io.opentelemetry.auto.tooling.context.NoopContextProvider;
import io.opentelemetry.auto.tooling.muzzle.Reference;
import io.opentelemetry.auto.tooling.muzzle.ReferenceMatcher;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.utility.JavaModule;

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

  @Slf4j
  abstract class Default implements Instrumenter {

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

    public Default(final String instrumentationName, final String... additionalNames) {
      instrumentationNames = new TreeSet<>(Arrays.asList(additionalNames));
      instrumentationNames.add(instrumentationName);
      instrumentationPrimaryName = instrumentationName;

      enabled = Config.get().isIntegrationEnabled(instrumentationNames, defaultEnabled());
      if (!contextStore().isEmpty()) {
        contextProvider = new FieldBackedProvider(this);
      } else {
        contextProvider = NoopContextProvider.INSTANCE;
      }
    }

    @Override
    public final AgentBuilder instrument(final AgentBuilder parentAgentBuilder) {
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
      final String[] helperClassNames = helperClassNames();
      if (helperClassNames.length > 0) {
        agentBuilder =
            agentBuilder.transform(
                new HelperInjector(getClass().getSimpleName(), helperClassNames));
      }
      return agentBuilder;
    }

    private AgentBuilder.Identified.Extendable applyInstrumentationTransformers(
        AgentBuilder.Identified.Extendable agentBuilder) {
      for (final Map.Entry<? extends ElementMatcher, String> entry : transformers().entrySet()) {
        agentBuilder =
            agentBuilder.transform(
                new AgentBuilder.Transformer.ForAdvice()
                    .include(Utils.getBootstrapProxy(), Utils.getAgentClassLoader())
                    .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                    .advice(entry.getKey(), entry.getValue()));
      }
      return agentBuilder;
    }

    /** Matches classes for which instrumentation is not muzzled. */
    private class MuzzleMatcher implements AgentBuilder.RawMatcher {
      @Override
      public boolean matches(
          final TypeDescription typeDescription,
          final ClassLoader classLoader,
          final JavaModule module,
          final Class<?> classBeingRedefined,
          final ProtectionDomain protectionDomain) {
        /* Optimization: calling getInstrumentationMuzzle() inside this method
         * prevents unnecessary loading of muzzle references during agentBuilder
         * setup.
         */
        final ReferenceMatcher muzzle = getInstrumentationMuzzle();
        if (null != muzzle) {
          final boolean isMatch = muzzle.matches(classLoader);
          if (!isMatch) {
            if (log.isDebugEnabled()) {
              final List<Reference.Mismatch> mismatches =
                  muzzle.getMismatchedReferenceSources(classLoader);
              if (log.isDebugEnabled()) {
                log.debug(
                    "Instrumentation muzzled: {} -- {} on {}",
                    instrumentationNames,
                    Instrumenter.Default.this.getClass().getName(),
                    classLoader);
              }
              for (final Reference.Mismatch mismatch : mismatches) {
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
     * <p>{@see io.opentelemetry.auto.tooling.muzzle.MuzzleGradlePlugin}
     */
    protected ReferenceMatcher getInstrumentationMuzzle() {
      return null;
    }

    /** @return Class names of helpers to inject into the user's classloader */
    public String[] helperClassNames() {
      return new String[0];
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
      return Config.get().isIntegrationsEnabled();
    }
  }
}
