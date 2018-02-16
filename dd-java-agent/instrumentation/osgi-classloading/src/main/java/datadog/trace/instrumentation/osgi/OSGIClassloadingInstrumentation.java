package datadog.trace.instrumentation.osgi;

import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

@AutoService(Instrumenter.class)
public final class OSGIClassloadingInstrumentation extends Instrumenter.Configurable {
  private static final String[] BOOTSTRAP_PACKAGE_PREFIXES = {
    "io.opentracing", "datadog.slf4j", "datadog.trace"
  };

  public OSGIClassloadingInstrumentation() {
    super("osgi-classloading");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        // OSGI Bundle class loads the sys prop which defines bootstrap classes
        .type(named("org.osgi.framework.Bundle"))
        .transform(
            new AgentBuilder.Transformer() {
              @Override
              public DynamicType.Builder<?> transform(
                  DynamicType.Builder<?> builder,
                  TypeDescription typeDescription,
                  ClassLoader classLoader,
                  JavaModule javaModule) {
                // This instrumentation modifies no bytes.
                // Instead it sets a system prop to tell osgi to delegate
                // classloads for datadog bootstrap classes
                StringBuilder ddPrefixes = new StringBuilder("");
                for (int i = 0; i < BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
                  if (i > 0) {
                    // must append twice. Once for exact package and wildcard for child packages
                    ddPrefixes.append(",");
                  }
                  ddPrefixes.append(BOOTSTRAP_PACKAGE_PREFIXES[i]).append(".*,");
                  ddPrefixes.append(BOOTSTRAP_PACKAGE_PREFIXES[i]);
                }
                final String existing = System.getProperty("org.osgi.framework.bootdelegation");
                if (null == existing) {
                  System.setProperty("org.osgi.framework.bootdelegation", ddPrefixes.toString());
                } else if (!existing.contains(ddPrefixes)) {
                  System.setProperty(
                      "org.osgi.framework.bootdelegation", existing + "," + ddPrefixes.toString());
                }
                return builder;
              }
            })
        .asDecorator();
  }
}
