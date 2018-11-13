package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.failSafe;
import static datadog.trace.agent.tooling.Utils.getConfigEnabled;
import static net.bytebuddy.matcher.ElementMatchers.any;

import datadog.trace.agent.tooling.context.FieldBackedProvider;
import datadog.trace.agent.tooling.context.InstrumentationContextProvider;
import datadog.trace.agent.tooling.muzzle.Reference;
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

/**
 * Built-in bytebuddy-based instrumentation for the datadog javaagent.
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

  /** @return A type matcher used to match the class under transform. */
  ElementMatcher typeMatcher();

  /** @return A type matcher used to match the classloader under transform */
  ElementMatcher<? super ClassLoader> classLoaderMatcher();

  /** @return Class names of helpers to inject into the user's classloader */
  String[] helperClassNames();

  Map<? extends ElementMatcher, String> transformers();

  @Slf4j
  abstract class Default implements Instrumenter {
    private final Set<String> instrumentationNames;
    private final String instrumentationPrimaryName;
    private final InstrumentationContextProvider contextProvider;
    protected final boolean enabled;

    protected final String packageName =
        getClass().getPackage() == null ? "" : getClass().getPackage().getName();

    public Default(final String instrumentationName, final String... additionalNames) {
      instrumentationNames = new HashSet<>(Arrays.asList(additionalNames));
      instrumentationNames.add(instrumentationName);
      instrumentationPrimaryName = instrumentationName;

      // If default is enabled, we want to enable individually,
      // if default is disabled, we want to disable individually.
      final boolean defaultEnabled = defaultEnabled();
      boolean anyEnabled = defaultEnabled;
      for (final String name : instrumentationNames) {
        final boolean configEnabled =
            getConfigEnabled("dd.integration." + name + ".enabled", defaultEnabled);
        if (defaultEnabled) {
          anyEnabled &= configEnabled;
        } else {
          anyEnabled |= configEnabled;
        }
      }
      enabled = anyEnabled;
      contextProvider = new FieldBackedProvider(this);
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
              .and(new MuzzleMatcher())
              .transform(DDTransformers.defaultTransformers());
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
        agentBuilder = agentBuilder.transform(new HelperInjector(helperClassNames));
      }
      return agentBuilder;
    }

    private AgentBuilder.Identified.Extendable applyInstrumentationTransformers(
        AgentBuilder.Identified.Extendable agentBuilder) {
      for (final Map.Entry<? extends ElementMatcher, String> entry : transformers().entrySet()) {
        agentBuilder =
            agentBuilder.transform(
                new AgentBuilder.Transformer.ForAdvice()
                    .include(Utils.getAgentClassLoader())
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
          final List<Reference.Mismatch> mismatches =
              muzzle.getMismatchedReferenceSources(classLoader);
          if (mismatches.size() > 0) {
            if (log.isDebugEnabled()) {
              log.debug(
                  "Instrumentation muzzled: {} -- {} on {}",
                  instrumentationPrimaryName,
                  Instrumenter.Default.this.getClass().getName(),
                  classLoader);
              for (final Reference.Mismatch mismatch : mismatches) {
                log.debug("-- {}", mismatch);
              }
            }
          } else {
            log.debug(
                "Applying instrumentation: {} -- {} on {}",
                instrumentationPrimaryName,
                Instrumenter.Default.this.getClass().getName(),
                classLoader);
          }
          return mismatches.size() == 0;
        }
        return true;
      }
    }

    /**
     * This method is implemented dynamically by compile-time bytecode transformations.
     *
     * <p>{@see datadog.trace.agent.tooling.muzzle.MuzzleGradlePlugin}
     */
    protected ReferenceMatcher getInstrumentationMuzzle() {
      return null;
    }

    @Override
    public String[] helperClassNames() {
      return new String[0];
    }

    @Override
    public ElementMatcher<ClassLoader> classLoaderMatcher() {
      return any();
    }

    @Override
    public abstract ElementMatcher<? super TypeDescription> typeMatcher();

    @Override
    public abstract Map<? extends ElementMatcher, String> transformers();

    /**
     * A map of {class-name -> context-class-name}. Keys (and their subclasses) will be associated
     * with a context of the value.
     */
    public Map<String, String> contextStore() {
      return Collections.EMPTY_MAP;
    }

    protected boolean defaultEnabled() {
      return getConfigEnabled("dd.integrations.enabled", true);
    }

    // TODO: move common config helpers to Utils

    public static String getPropOrEnv(final String name) {
      return System.getProperty(name, System.getenv(propToEnvName(name)));
    }

    public static String propToEnvName(final String name) {
      return name.toUpperCase().replace(".", "_");
    }
  }
}
