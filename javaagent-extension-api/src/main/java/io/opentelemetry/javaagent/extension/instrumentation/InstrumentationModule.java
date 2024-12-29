/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static net.bytebuddy.matcher.ElementMatchers.any;

import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation module groups several connected {@link TypeInstrumentation}s together, sharing
 * class loader matcher, helper classes, muzzle safety checks, etc. Ideally all types in a single
 * instrumented library should live in a single module.
 *
 * <p>Classes extending {@link InstrumentationModule} should be public and non-final so that it's
 * possible to extend and reuse them in vendor distributions.
 *
 * <p>{@link InstrumentationModule} is an SPI, you need to ensure that a proper {@code
 * META-INF/services/} provider file is created for it to be picked up by the agent. See {@link
 * java.util.ServiceLoader} for more details.
 */
public abstract class InstrumentationModule implements Ordered {
  private static final Logger logger = Logger.getLogger(InstrumentationModule.class.getName());

  private final Set<String> instrumentationNames;

  /**
   * Creates an instrumentation module. Note that all implementations of {@link
   * InstrumentationModule} must have a default constructor (for SPI), so they have to pass the
   * instrumentation names to the super class constructor.
   *
   * <p>When enabling or disabling the instrumentation module configuration property that
   * corresponds to the main instrumentation name is considered first, after that additional
   * instrumentation names are considered in the order they are listed here.
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
  protected InstrumentationModule(
      String mainInstrumentationName, String... additionalInstrumentationNames) {
    LinkedHashSet<String> names = new LinkedHashSet<>(additionalInstrumentationNames.length + 1);
    names.add(mainInstrumentationName);
    names.addAll(asList(additionalInstrumentationNames));
    this.instrumentationNames = unmodifiableSet(names);
  }

  /**
   * Returns all instrumentation names assigned to this module. See {@link
   * #InstrumentationModule(String, String...)} for more details about instrumentation names.
   */
  public final Set<String> instrumentationNames() {
    return instrumentationNames;
  }

  /**
   * Returns the main instrumentation name. See {@link #InstrumentationModule(String, String...)}
   * for more details about instrumentation names.
   */
  public final String instrumentationName() {
    return instrumentationNames.iterator().next();
  }

  /**
   * Allows instrumentation modules to disable themselves by default, or to additionally disable
   * themselves on some other condition.
   */
  public boolean defaultEnabled(ConfigProperties config) {
    return config.getBoolean("otel.instrumentation.common.default-enabled", true);
  }

  /**
   * Instrumentation modules can override this method to specify additional packages (or classes)
   * that should be treated as "library instrumentation" packages. Classes from those packages will
   * be treated by muzzle as instrumentation helper classes: they will be scanned for references and
   * automatically injected into the application class loader if they're used in any type
   * instrumentation. The classes for which this predicate returns {@code true} will be treated as
   * helper classes, in addition to the default ones defined in the {@code HelperClassPredicate}
   * class.
   *
   * @param className The name of the class that may or may not be a helper class.
   */
  public boolean isHelperClass(String className) {
    return false;
  }

  /**
   * Note this is an experimental feature until phase 1 of <a
   * href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8999">
   * implementing the invokedynamic based instrumentation mechanism</a> is complete. Instrumentation
   * modules that override this to true (recommended) must use the inline=false Invoke Dynamic style
   * of Byte Buddy advices which calls out to helper classes in their own classloader, thus enabling
   * better isolation, best practice code development, avoids shading and enables standard debugging
   * techniques. The non-inlining of advice will be enforced by muzzle (TODO)
   */
  public boolean isIndyModule() {
    return IndyConfigurationHolder.indyEnabled;
  }

  /** Register resource names to inject into the user's class loader. */
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {}

  /**
   * An instrumentation module can implement this method to make sure that the class loader contains
   * the particular library version. It is useful to implement that if the muzzle check does not
   * fail for versions out of the instrumentation's scope.
   *
   * <p>E.g. supposing version 1.0 has class {@code A}, but it was removed in version 2.0; A is not
   * used in the helper classes at all; this module is instrumenting 2.0: this method will return
   * {@code not(hasClassesNamed("A"))}.
   *
   * @return A type matcher used to match the class loader under transform
   */
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return any();
  }

  /** Returns a list of all individual type instrumentation in this module. */
  public abstract List<TypeInstrumentation> typeInstrumentations();

  /**
   * Returns a list of additional instrumentation helper classes, which are not automatically
   * detected during compile time.
   *
   * <p>If your instrumentation module does not apply and you see warnings about missing classes in
   * the logs, you may need to override this method and provide fully qualified classes names of
   * helper classes that your instrumentation uses.
   *
   * <p>These helper classes will be injected into the application class loader after automatically
   * detected ones.
   */
  public List<String> getAdditionalHelperClassNames() {
    return Collections.emptyList();
  }

  // InstrumentationModule is loaded before ExperimentalConfig is initialized
  private static class IndyConfigurationHolder {
    private static final boolean indyEnabled;

    static {
      indyEnabled = ExperimentalConfig.get().indyEnabled();
      if (indyEnabled) {
        logger.info("Enabled indy for instrumentation modules");
      }
    }
  }
}
