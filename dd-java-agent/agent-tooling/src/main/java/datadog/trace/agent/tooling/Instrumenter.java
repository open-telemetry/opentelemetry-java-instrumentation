package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.any;

import datadog.trace.agent.tooling.context.FieldBackedProvider;
import datadog.trace.agent.tooling.context.InstrumentationContextProvider;
import datadog.trace.agent.tooling.muzzle.Reference;
import datadog.trace.agent.tooling.muzzle.ReferenceMatcher;
import datadog.trace.api.Config;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
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

  @Slf4j
  abstract class Default implements Instrumenter {
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

      enabled = Config.integrationEnabled(instrumentationNames, defaultEnabled());
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
              .and(new PostMatchHook())
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

    private class PostMatchHook implements AgentBuilder.RawMatcher {
      @Override
      public boolean matches(
          final TypeDescription typeDescription,
          final ClassLoader classLoader,
          final JavaModule module,
          final Class<?> classBeingRedefined,
          final ProtectionDomain protectionDomain) {
        postMatch(typeDescription, classLoader, module, classBeingRedefined, protectionDomain);
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

    /**
     * A hook invoked after matching has succeeded and before transformers have run.
     *
     * <p>Implementation note: This hook runs inside of the bytebuddy matching phase.
     *
     * @param typeDescription type description of the matched type
     * @param classLoader classloader loading the class under transform
     * @param module java module
     * @param classBeingRedefined null when the matched class is being loaded for the first time.
     *     The instance of the active class during retransforms.
     * @param protectionDomain protection domain of the class under load.
     */
    public void postMatch(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final Class<?> classBeingRedefined,
        final ProtectionDomain protectionDomain) {}

    /** @return A map of matcher->advice */
    public abstract Map<? extends ElementMatcher<? super MethodDescription>, String> transformers();

    /**
     * A map of {class-name -> context-class-name}. Keys (and their subclasses) will be associated
     * with a context of the value.
     */
    public Map<String, String> contextStore() {
      return Collections.EMPTY_MAP;
    }

    protected boolean defaultEnabled() {
      return Config.getBooleanSettingFromEnvironment("integrations.enabled", true);
    }
  }
}
