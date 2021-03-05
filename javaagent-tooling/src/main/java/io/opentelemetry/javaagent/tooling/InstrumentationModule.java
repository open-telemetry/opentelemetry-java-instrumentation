/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.failSafe;
import static java.util.Arrays.asList;
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
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationClassPredicate;
import io.opentelemetry.javaagent.tooling.muzzle.matcher.Mismatch;
import io.opentelemetry.javaagent.tooling.muzzle.matcher.ReferenceMatcher;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation module groups several connected {@link TypeInstrumentation}s together, sharing
 * classloader matcher, helper classes, muzzle safety checks, etc. Ideally all types in a single
 * instrumented library should live in a single module.
 *
 * <p>Classes extending {@link InstrumentationModule} should be public and non-final so that it's
 * possible to extend and reuse them in vendor distributions.
 */
public abstract class InstrumentationModule {
  private static final Logger log = LoggerFactory.getLogger(InstrumentationModule.class);
  private static final Logger muzzleLog = LoggerFactory.getLogger("muzzleMatcher");

  private static final String[] EMPTY = new String[0];

  // Added here instead of AgentInstaller's ignores because it's relatively
  // expensive. https://github.com/DataDog/dd-trace-java/pull/1045
  public static final ElementMatcher.Junction<AnnotationSource> NOT_DECORATOR_MATCHER =
      not(isAnnotatedWith(named("javax.decorator.Decorator")));

  private final Set<String> instrumentationNames;
  protected final boolean enabled;

  /**
   * Creates an instrumentation module. Note that all implementations of {@link
   * InstrumentationModule} must have a default constructor (for SPI), so they have to pass the
   * instrumentation names to the super class constructor.
   *
   * <p>The instrumentation names should follow several rules:
   *
   * <ul>
   *   <li>Instrumentation names should consist of hyphen-separated words, e.g. {@code
   *       instrumented-library};
   *   <li>In general, instrumentation names should be the as close as possible to the gradle module
   *       name - which in turn should be as close as possible to the instrumented library name;
   *   <li>The main instrumentation name should be the same as the gradle module name, minus the
   *       version if it's a part of the module name. When several versions of a library are
   *       instrumented they should all share the same main instrumentation name so that it's easy
   *       to enable/disable the instrumentation regardless of the runtime library version;
   *   <li>If the gradle module has a version as a part of its name, an additional instrumentation
   *       name containing the version should be passed, e.g. {@code instrumented-library-1.0}.
   * </ul>
   */
  public InstrumentationModule(
      String mainInstrumentationName, String... additionalInstrumentationNames) {
    this(toList(mainInstrumentationName, additionalInstrumentationNames));
  }

  /**
   * Creates an instrumentation module.
   *
   * @see #InstrumentationModule(String, String...)
   */
  public InstrumentationModule(List<String> instrumentationNames) {
    if (instrumentationNames.isEmpty()) {
      throw new IllegalArgumentException("InstrumentationModules must be named");
    }
    this.instrumentationNames = new LinkedHashSet<>(instrumentationNames);
    enabled = Config.get().isInstrumentationEnabled(this.instrumentationNames, defaultEnabled());
  }

  private static List<String> toList(String first, String[] rest) {
    List<String> instrumentationNames = new ArrayList<>(rest.length + 1);
    instrumentationNames.add(first);
    instrumentationNames.addAll(asList(rest));
    return instrumentationNames;
  }

  /**
   * Add this instrumentation to an AgentBuilder.
   *
   * @param parentAgentBuilder AgentBuilder to base instrumentation config off of.
   * @return the original agentBuilder and this instrumentation
   */
  public final AgentBuilder instrument(AgentBuilder parentAgentBuilder) {
    if (!enabled) {
      log.debug("Instrumentation {} is disabled", mainInstrumentationName());
      return parentAgentBuilder;
    }

    List<String> helperClassNames = getAllHelperClassNames();
    List<String> helperResourceNames = asList(helperResourceNames());
    List<TypeInstrumentation> typeInstrumentations = typeInstrumentations();
    if (typeInstrumentations.isEmpty()) {
      if (!helperClassNames.isEmpty() || !helperResourceNames.isEmpty()) {
        log.warn(
            "Helper classes and resources won't be injected if no types are instrumented: {}",
            mainInstrumentationName());
      }

      return parentAgentBuilder;
    }

    ElementMatcher.Junction<ClassLoader> moduleClassLoaderMatcher = classLoaderMatcher();
    MuzzleMatcher muzzleMatcher = new MuzzleMatcher();
    HelperInjector helperInjector =
        new HelperInjector(mainInstrumentationName(), helperClassNames, helperResourceNames);
    InstrumentationContextProvider contextProvider = getContextProvider();

    AgentBuilder agentBuilder = parentAgentBuilder;
    for (TypeInstrumentation typeInstrumentation : typeInstrumentations) {
      AgentBuilder.Identified.Extendable extendableAgentBuilder =
          agentBuilder
              .type(
                  failSafe(
                      typeInstrumentation.typeMatcher(),
                      "Instrumentation type matcher unexpected exception: " + getClass().getName()),
                  failSafe(
                      moduleClassLoaderMatcher.and(typeInstrumentation.classLoaderOptimization()),
                      "Instrumentation class loader matcher unexpected exception: "
                          + getClass().getName()))
              .and(NOT_DECORATOR_MATCHER)
              .and(muzzleMatcher)
              .transform(AgentTransformers.defaultTransformers())
              .transform(helperInjector);
      extendableAgentBuilder = contextProvider.instrumentationTransformer(extendableAgentBuilder);
      extendableAgentBuilder =
          applyInstrumentationTransformers(
              typeInstrumentation.transformers(), extendableAgentBuilder);
      extendableAgentBuilder = contextProvider.additionalInstrumentation(extendableAgentBuilder);

      agentBuilder = extendableAgentBuilder;
    }

    return agentBuilder;
  }

  /**
   * Returns all helper classes that will be injected into the application classloader, both ones
   * provided by the implementation and ones that were collected by muzzle during compilation.
   */
  public final List<String> getAllHelperClassNames() {
    List<String> helperClassNames = new ArrayList<>();
    helperClassNames.addAll(asList(additionalHelperClassNames()));
    helperClassNames.addAll(asList(getMuzzleHelperClassNames()));
    return helperClassNames;
  }

  private AgentBuilder.Identified.Extendable applyInstrumentationTransformers(
      Map<? extends ElementMatcher<? super MethodDescription>, String> transformers,
      AgentBuilder.Identified.Extendable agentBuilder) {
    for (Map.Entry<? extends ElementMatcher<? super MethodDescription>, String> entry :
        transformers.entrySet()) {
      agentBuilder =
          agentBuilder.transform(
              new AgentBuilder.Transformer.ForAdvice()
                  .include(Utils.getBootstrapProxy(), Utils.getAgentClassLoader())
                  .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                  .advice(entry.getKey(), entry.getValue()));
    }
    return agentBuilder;
  }

  private InstrumentationContextProvider getContextProvider() {
    Map<String, String> contextStore = contextStore();
    if (!contextStore.isEmpty()) {
      return new FieldBackedProvider(getClass(), contextStore);
    } else {
      return NoopContextProvider.INSTANCE;
    }
  }

  /**
   * A ByteBuddy matcher that decides whether this instrumentation should be applied. Calls
   * generated {@link ReferenceMatcher}: if any mismatch with the passed {@code classLoader} is
   * found this instrumentation is skipped.
   */
  private class MuzzleMatcher implements AgentBuilder.RawMatcher {
    @Override
    public boolean matches(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain) {
      /* Optimization: calling getMuzzleReferenceMatcher() inside this method
       * prevents unnecessary loading of muzzle references during agentBuilder
       * setup.
       */
      ReferenceMatcher muzzle = getMuzzleReferenceMatcher();
      if (muzzle != null) {
        boolean isMatch = muzzle.matches(classLoader);

        if (!isMatch) {
          if (muzzleLog.isWarnEnabled()) {
            muzzleLog.warn(
                "Instrumentation skipped, mismatched references were found: {} -- {} on {}",
                mainInstrumentationName(),
                InstrumentationModule.this.getClass().getName(),
                classLoader);
            List<Mismatch> mismatches = muzzle.getMismatchedReferenceSources(classLoader);
            for (Mismatch mismatch : mismatches) {
              muzzleLog.warn("-- {}", mismatch);
            }
          }
        } else {
          if (muzzleLog.isDebugEnabled()) {
            log.debug(
                "Applying instrumentation: {} -- {} on {}",
                mainInstrumentationName(),
                InstrumentationModule.this.getClass().getName(),
                classLoader);
          }
        }

        return isMatch;
      }
      return true;
    }
  }

  private String mainInstrumentationName() {
    return instrumentationNames.iterator().next();
  }

  /**
   * The actual implementation of this method is generated automatically during compilation by the
   * {@link io.opentelemetry.javaagent.tooling.muzzle.collector.MuzzleCodeGenerationPlugin}
   * ByteBuddy plugin.
   *
   * <p><b>This method is generated automatically, do not override it.</b>
   */
  protected ReferenceMatcher getMuzzleReferenceMatcher() {
    return null;
  }

  /**
   * Returns a list of instrumentation helper classes, automatically detected by muzzle during
   * compilation. Those helpers will be injected into the application classloader.
   *
   * <p>The actual implementation of this method is generated automatically during compilation by
   * the {@link io.opentelemetry.javaagent.tooling.muzzle.collector.MuzzleCodeGenerationPlugin}
   * ByteBuddy plugin.
   *
   * <p><b>This method is generated automatically, do not override it.</b>
   */
  protected String[] getMuzzleHelperClassNames() {
    return EMPTY;
  }

  /**
   * Instrumentation modules can override this method to provide additional helper classes that are
   * not located in instrumentation packages described in {@link InstrumentationClassPredicate} (and
   * not automatically detected by muzzle). These additional classes will be injected into the
   * application classloader first.
   */
  protected String[] additionalHelperClassNames() {
    return EMPTY;
  }

  /**
   * Order of adding instrumentation to ByteBuddy. For example instrumentation with order 1 runs
   * after an instrumentation with order 0 (default) matched on the same API.
   *
   * @return the order of adding an instrumentation to ByteBuddy. Default value is 0 - no order.
   */
  public int getOrder() {
    return 0;
  }

  /** Returns resource names to inject into the user's classloader. */
  public String[] helperResourceNames() {
    return EMPTY;
  }

  /**
   * An instrumentation module can implement this method to make sure that the classloader contains
   * the particular library version. It is useful to implement that if the muzzle check does not
   * fail for versions out of the instrumentation's scope.
   *
   * <p>E.g. supposing version 1.0 has class {@code A}, but it was removed in version 2.0; A is not
   * used in the helper classes at all; this module is instrumenting 2.0: this method will return
   * {@code not(hasClassesNamed("A"))}.
   *
   * @return A type matcher used to match the classloader under transform
   */
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return any();
  }

  /** Returns a list of all individual type instrumentation in this module. */
  public abstract List<TypeInstrumentation> typeInstrumentations();

  /**
   * Context stores to define for this instrumentation.
   *
   * <p>A map of {@code class-name to context-class-name}. Keys (and their subclasses) will be
   * associated with a context of the value.
   */
  protected Map<String, String> contextStore() {
    return Collections.emptyMap();
  }

  protected boolean defaultEnabled() {
    return Config.get().getBooleanProperty("otel.instrumentation.common.default-enabled", true);
  }
}
