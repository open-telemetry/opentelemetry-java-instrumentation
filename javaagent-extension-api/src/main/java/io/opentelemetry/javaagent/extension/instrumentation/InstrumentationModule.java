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
   * <p>Names registered under {@code otel.instrumentation.common.v3-preview=true} follow this
   * ordered hierarchy:
   *
   * <ul>
   *   <li>The library family, e.g. {@code instrumented-library}, shared across its versions.
   *   <li>The family with its owning instrumentation baseline, e.g. {@code
   *       instrumented-library-1.0}. JDK instrumentations omit this version level.
   *   <li>When client and server instrumentation are independently selectable, optional versionless
   *       role selectors: {@code instrumented-library-client} or {@code
   *       instrumented-library-server}. Role-specific modules, including route enrichment, share
   *       its selectors. Support needed by both roles is selected separately.
   *   <li>Optional versionless feature names for independently useful behavior, e.g. {@code
   *       reactor-context-propagation-operator}. Several module classes can share the same feature
   *       name. Existing framework controls such as {@code cxf} and {@code cxf-3.2} may be shared
   *       across API families.
   *   <li>Optional product or ecosystem umbrellas, e.g. {@code vertx} for Vert.x HTTP and SQL
   *       clients. These follow all component selectors so narrower settings take precedence.
   * </ul>
   *
   * <p>Default-off features, e.g. {@code kafka-clients-metrics} or {@code jdbc-datasource}, use
   * independent feature selectors when their library's other instrumentation is default-on. They
   * must not share family, baseline, or umbrella selectors with default-on instrumentation. An
   * umbrella whose members are all default-off can enable them together.
   *
   * <p>Names use kebab-case. Umbrella membership follows product ownership, not directory nesting
   * or a shared prefix. Module classes may share all their public names; separate compatibility
   * ranges do not require separate public controls. Muzzle can select individual modules by fully
   * qualified class name. Outside preview, compatibility aliases retain their configuration
   * precedence.
   *
   * <p>These names apply to flat {@code otel.instrumentation.<name>.enabled} properties and
   * declarative enabled/disabled lists. They are troubleshooting escape hatches, not telemetry
   * tuning controls, and are independent of emitted instrumentation scope names.
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
   */
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
   * Returns a list of helper class names that must be defined in the class loader of the
   * instrumented library instead of an isolated instrumentation module class loader.
   *
   * <p>The agent automatically determines whether to inject all helper classes or load them in an
   * isolated class loader, typically based on whether the module uses inlined or non-inlined
   * advice. This method only has an effect when isolated loading is used; when all helper classes
   * are injected, they are already defined in the class loader of the instrumented library.
   *
   * <p>Override this method when a helper class must access package-private members of an
   * instrumented library class.
   */
  public List<String> injectedClassNames() {
    return emptyList();
  }

  /**
   * Returns a list of instrumentation helper class names that must be visible to the application
   * class loader while remaining loaded by an isolated instrumentation module class loader.
   *
   * <p>The agent automatically determines whether to inject all helper classes or load them in an
   * isolated class loader, typically based on whether the module uses inlined or non-inlined
   * advice. This method only has an effect when isolated loading is used; isolated helper classes
   * are not otherwise visible to the instrumented application.
   *
   * <p>Override this method when an isolated helper class must be loaded through the application
   * class loader, for example when providing an SPI implementation loaded by {@link
   * java.util.ServiceLoader}.
   */
  public List<String> exposedClassNames() {
    return emptyList();
  }
}
