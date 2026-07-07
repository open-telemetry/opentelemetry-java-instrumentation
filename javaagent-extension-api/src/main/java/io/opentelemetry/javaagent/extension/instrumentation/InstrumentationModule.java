/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableSet;
import static net.bytebuddy.matcher.ElementMatchers.any;

import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
  public boolean defaultEnabled() {
    return AgentDistributionConfig.get().isInstrumentationDefaultEnabled();
  }

  /**
   * Allows instrumentation modules to disable themselves by default, or to additionally disable
   * themselves on some other condition.
   *
   * @deprecated Use {@link #defaultEnabled()} instead.
   */
  @Deprecated // to be removed in 3.0
  public boolean defaultEnabled(ConfigProperties config) {
    return defaultEnabled();
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
  @Deprecated // to be removed in next release
  public boolean isIndyModule() {
    return false;
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
    return emptyList();
  }

  /**
   * Returns a list of helper classes that will be defined in the class loader of the instrumented
   * library.
   *
   * <p>This method only takes effect when using {@link HelperClassStrategy#ISOLATED} strategy, with
   * {@link HelperClassStrategy#INJECTED} strategy all helper classes are already injected into the
   * class loader of the instrumented library.
   */
  public List<String> injectedClassNames() {
    return emptyList();
  }

  /**
   * Returns a list of instrumentation helper classes that are exposed to the application class
   * loader.
   *
   * <p>When using {@link HelperClassStrategy#ISOLATED} strategy helper classes are loaded into a
   * separate class loader. Classes from that class loader aren't visible to the instrumented
   * application. This method can be used to expose some of the helper classes to the application
   * class loader, so that they can be loaded through the loadClass method of the application class
   * loader. This can for example be used to add a SPI implementation that can be loaded via the
   * ServiceLoader.
   */
  public List<String> exposedClassNames() {
    return emptyList();
  }

  /**
   * Allows instrumentation modules to choose whether the helper classes should be injected into the
   * same class loader as the instrumented library, or into an isolated class loader.
   */
  public HelperClassStrategy helperClassStrategy() {
    return HelperClassStrategy.DEFAULT;
  }

  /** Enum describing helper class handling strategies. */
  public enum HelperClassStrategy {
    /**
     * Depending on whether the instrumentation uses inline advice or not, helper classes are either
     * loaded in the same classloader as the instrumented library, or into an isolated classloader.
     */
    DEFAULT,
    /**
     * Helper classes are loaded in the same classloader as the instrumented library, and are
     * visible to the application.
     */
    INJECTED,
    /**
     * Helper classes are loaded into an isolated classloader, and aren't visible to the
     * application.
     */
    ISOLATED
  }
}
