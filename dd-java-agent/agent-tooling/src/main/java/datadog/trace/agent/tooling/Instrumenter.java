package datadog.trace.agent.tooling;

import static datadog.trace.agent.tooling.Utils.getConfigEnabled;
import static net.bytebuddy.matcher.ElementMatchers.any;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

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

  Map<ElementMatcher, String> transformers();

  @Slf4j
  abstract class Default implements Instrumenter {
    private final Set<String> instrumentationNames;
    protected final boolean enabled;

    public Default(final String instrumentationName, final String... additionalNames) {
      this.instrumentationNames = new HashSet<>(Arrays.asList(additionalNames));
      instrumentationNames.add(instrumentationName);

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
    }

    @Override
    public AgentBuilder instrument(final AgentBuilder agentBuilder) {
      if (!enabled) {
        log.debug("Instrumentation {} is disabled", this);
        return agentBuilder;
      }

      AgentBuilder.Identified.Extendable advice =
          agentBuilder
              .type(typeMatcher(), classLoaderMatcher())
              .transform(DDTransformers.defaultTransformers());
      final String[] helperClassNames = helperClassNames();
      if (helperClassNames.length > 0) {
        advice = advice.transform(new HelperInjector(helperClassNames));
      }
      for (Map.Entry<ElementMatcher, String> entry : transformers().entrySet()) {
        advice = advice.transform(DDAdvice.create().advice(entry.getKey(), entry.getValue()));
      }
      return advice.asDecorator();
    }

    @Override
    public String[] helperClassNames() {
      return new String[0];
    }

    @Override
    public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
      return any();
    }

    @Override
    public abstract ElementMatcher<? super TypeDescription> typeMatcher();

    @Override
    public abstract Map<ElementMatcher, String> transformers();

    protected boolean defaultEnabled() {
      return getConfigEnabled("dd.integrations.enabled", true);
    }

    protected static String getPropOrEnv(final String name) {
      return System.getProperty(name, System.getenv(propToEnvName(name)));
    }

    private static String propToEnvName(final String name) {
      return name.toUpperCase().replace(".", "_");
    }
  }
}
