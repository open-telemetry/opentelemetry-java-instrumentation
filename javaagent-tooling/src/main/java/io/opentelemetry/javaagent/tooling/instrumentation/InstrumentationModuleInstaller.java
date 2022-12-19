/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.TransformSafeLogger;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.bytebuddy.LoggingFailSafeMatcher;
import io.opentelemetry.javaagent.tooling.config.AgentConfig;
import io.opentelemetry.javaagent.tooling.field.VirtualFieldImplementationInstaller;
import io.opentelemetry.javaagent.tooling.field.VirtualFieldImplementationInstallerFactory;
import io.opentelemetry.javaagent.tooling.muzzle.HelperResourceBuilderImpl;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.util.IgnoreFailedTypeMatcher;
import io.opentelemetry.javaagent.tooling.util.NamedMatcher;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.lang.instrument.Instrumentation;
import java.util.List;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public final class InstrumentationModuleInstaller {

  static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(InstrumentationModule.class);

  // Added here instead of AgentInstaller's ignores because it's relatively
  // expensive. https://github.com/DataDog/dd-trace-java/pull/1045
  public static final ElementMatcher.Junction<AnnotationSource> NOT_DECORATOR_MATCHER =
      not(isAnnotatedWith(named("javax.decorator.Decorator")));

  private final Instrumentation instrumentation;
  private final VirtualFieldImplementationInstallerFactory virtualFieldInstallerFactory =
      new VirtualFieldImplementationInstallerFactory();

  public InstrumentationModuleInstaller(Instrumentation instrumentation) {
    this.instrumentation = instrumentation;
  }

  AgentBuilder install(
      InstrumentationModule instrumentationModule,
      AgentBuilder parentAgentBuilder,
      ConfigProperties config) {
    if (!AgentConfig.isInstrumentationEnabled(
        config,
        instrumentationModule.instrumentationNames(),
        instrumentationModule.defaultEnabled(config))) {
      logger.log(
          FINE, "Instrumentation {0} is disabled", instrumentationModule.instrumentationName());
      return parentAgentBuilder;
    }
    List<String> helperClassNames =
        InstrumentationModuleMuzzle.getHelperClassNames(instrumentationModule);
    HelperResourceBuilderImpl helperResourceBuilder = new HelperResourceBuilderImpl();
    instrumentationModule.registerHelperResources(helperResourceBuilder);
    List<TypeInstrumentation> typeInstrumentations = instrumentationModule.typeInstrumentations();
    if (typeInstrumentations.isEmpty()) {
      if (!helperClassNames.isEmpty() || !helperResourceBuilder.getResources().isEmpty()) {
        logger.log(
            WARNING,
            "Helper classes and resources won't be injected if no types are instrumented: {0}",
            instrumentationModule.instrumentationName());
      }

      return parentAgentBuilder;
    }

    ElementMatcher.Junction<ClassLoader> moduleClassLoaderMatcher =
        instrumentationModule.classLoaderMatcher();
    MuzzleMatcher muzzleMatcher = new MuzzleMatcher(logger, instrumentationModule, config);
    AgentBuilder.Transformer helperInjector =
        new HelperInjector(
            instrumentationModule.instrumentationName(),
            helperClassNames,
            helperResourceBuilder.getResources(),
            Utils.getExtensionsClassLoader(),
            instrumentation);
    VirtualFieldImplementationInstaller contextProvider =
        virtualFieldInstallerFactory.create(instrumentationModule);

    AgentBuilder agentBuilder = parentAgentBuilder;
    for (TypeInstrumentation typeInstrumentation : typeInstrumentations) {
      ElementMatcher<TypeDescription> typeMatcher =
          new NamedMatcher<>(
              instrumentationModule.getClass().getSimpleName()
                  + "#"
                  + typeInstrumentation.getClass().getSimpleName(),
              new IgnoreFailedTypeMatcher(typeInstrumentation.typeMatcher()));
      ElementMatcher<ClassLoader> classLoaderMatcher =
          new NamedMatcher<>(
              instrumentationModule.getClass().getSimpleName()
                  + "#"
                  + typeInstrumentation.getClass().getSimpleName(),
              moduleClassLoaderMatcher.and(typeInstrumentation.classLoaderOptimization()));

      AgentBuilder.Identified.Extendable extendableAgentBuilder =
          agentBuilder
              .type(
                  new LoggingFailSafeMatcher<>(
                      typeMatcher,
                      "Instrumentation type matcher unexpected exception: " + typeMatcher),
                  new LoggingFailSafeMatcher<>(
                      classLoaderMatcher,
                      "Instrumentation class loader matcher unexpected exception: "
                          + classLoaderMatcher))
              .and(NOT_DECORATOR_MATCHER)
              .and(muzzleMatcher)
              .transform(ConstantAdjuster.instance())
              .transform(helperInjector);
      extendableAgentBuilder = contextProvider.rewriteVirtualFieldsCalls(extendableAgentBuilder);
      TypeTransformerImpl typeTransformer = new TypeTransformerImpl(extendableAgentBuilder);
      typeInstrumentation.transform(typeTransformer);
      extendableAgentBuilder = typeTransformer.getAgentBuilder();
      extendableAgentBuilder = contextProvider.injectFields(extendableAgentBuilder);

      agentBuilder = extendableAgentBuilder;
    }

    return agentBuilder;
  }
}
