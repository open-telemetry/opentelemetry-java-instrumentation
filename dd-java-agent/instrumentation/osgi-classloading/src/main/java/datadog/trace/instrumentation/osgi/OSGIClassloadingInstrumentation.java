package datadog.trace.instrumentation.osgi;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.NameMatcher;
import net.bytebuddy.matcher.StringMatcher;

@AutoService(Instrumenter.class)
public final class OSGIClassloadingInstrumentation extends Instrumenter.Default {
  public OSGIClassloadingInstrumentation() {
    super("osgi-classloading");
  }

  @Override
  public ElementMatcher typeMatcher() {
    // OSGI Bundle class loads the sys prop which defines bootstrap classes
    return new NameMatcher(
        new StringMatcher("org.osgi.framework.Bundle", StringMatcher.Mode.EQUALS_FULLY) {
          @Override
          public boolean matches(String target) {
            if (super.matches(target)) {
              // This instrumentation modifies no bytes.
              // Instead it sets a system prop to tell osgi to delegate
              // classloads for datadog bootstrap classes
              StringBuilder ddPrefixes = new StringBuilder("");
              for (int i = 0; i < Utils.BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
                if (i > 0) {
                  // must append twice. Once for exact package and wildcard for child packages
                  ddPrefixes.append(",");
                }
                ddPrefixes.append(Utils.BOOTSTRAP_PACKAGE_PREFIXES[i]).append(".*,");
                ddPrefixes.append(Utils.BOOTSTRAP_PACKAGE_PREFIXES[i]);
              }
              final String existing = System.getProperty("org.osgi.framework.bootdelegation");
              if (null == existing) {
                System.setProperty("org.osgi.framework.bootdelegation", ddPrefixes.toString());
              } else if (!existing.contains(ddPrefixes)) {
                System.setProperty(
                    "org.osgi.framework.bootdelegation", existing + "," + ddPrefixes.toString());
              }
            }
            return false;
          }
        });
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.emptyMap();
  }
}
