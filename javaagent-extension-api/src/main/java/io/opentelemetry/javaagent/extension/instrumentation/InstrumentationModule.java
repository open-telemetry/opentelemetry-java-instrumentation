/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static net.bytebuddy.matcher.ElementMatchers.any;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.Ordered;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation module groups several connected {@link TypeInstrumentation}s together, sharing
 * classloader matcher, helper classes, muzzle safety checks, etc. Ideally all types in a single
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
  private static final boolean DEFAULT_ENABLED =
      Config.get().getBoolean("otel.instrumentation.common.default-enabled", true);

  private final Set<String> instrumentationNames;

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
  protected InstrumentationModule(
      String mainInstrumentationName, String... additionalInstrumentationNames) {
    LinkedHashSet<String> names = new LinkedHashSet<>(additionalInstrumentationNames.length + 1);
    names.add(mainInstrumentationName);
    names.addAll(asList(additionalInstrumentationNames));
    this.instrumentationNames = unmodifiableSet(names);
  }

  /**
   * Creates an instrumentation module.
   *
   * @see #InstrumentationModule(String, String...)
   * @deprecated Use {@link #InstrumentationModule(String, String...)} instead.
   */
  @Deprecated
  protected InstrumentationModule(List<String> instrumentationNames) {
    if (instrumentationNames.isEmpty()) {
      throw new IllegalArgumentException("InstrumentationModules must be named");
    }
    this.instrumentationNames = unmodifiableSet(new LinkedHashSet<>(instrumentationNames));
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
   * Returns true if this instrumentation module should be installed.
   *
   * @deprecated This method will be removed.
   */
  @Deprecated
  public final boolean isEnabled() {
    return Config.get().isInstrumentationEnabled(instrumentationNames, defaultEnabled());
  }

  /**
   * Allows instrumentation modules to disable themselves by default, or to additionally disable
   * themselves on some other condition.
   */
  public boolean defaultEnabled() {
    return DEFAULT_ENABLED;
  }

  /**
   * Instrumentation modules can override this method to specify additional packages (or classes)
   * that should be treated as "library instrumentation" packages. Classes from those packages will
   * be treated by muzzle as instrumentation helper classes: they will be scanned for references and
   * automatically injected into the application classloader if they're used in any type
   * instrumentation. The classes for which this predicate returns {@code true} will be treated as
   * helper classes, in addition to the default ones defined in the {@code HelperClassPredicate}
   * class.
   *
   * @param className The name of the class that may or may not be a helper class.
   */
  public boolean isHelperClass(String className) {
    return false;
  }

  /** Register resource names to inject into the user's class loader. */
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {}

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
   * Returns a list of additional instrumentation helper classes, which are not automatically
   * detected during compile time.
   *
   * <p>If your instrumentation module does not apply and you see warnings about missing classes in
   * the logs, you may need to override this method and provide fully qualified classes names of
   * helper classes that your instrumentation uses.
   *
   * <p>These helper classes will be injected into the application classloader after automatically
   * detected ones.
   */
  public List<String> getAdditionalHelperClassNames() {
    return Collections.emptyList();
  }
}
