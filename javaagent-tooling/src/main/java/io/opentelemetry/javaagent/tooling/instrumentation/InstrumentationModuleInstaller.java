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
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import io.opentelemetry.javaagent.tooling.HelperClassDefinition;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.ModuleOpener;
import io.opentelemetry.javaagent.tooling.TransformSafeLogger;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.bytebuddy.LoggingFailSafeMatcher;
import io.opentelemetry.javaagent.tooling.config.AgentConfig;
import io.opentelemetry.javaagent.tooling.field.VirtualFieldImplementationInstaller;
import io.opentelemetry.javaagent.tooling.field.VirtualFieldImplementationInstallerFactory;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.ClassInjectorImpl;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyModuleRegistry;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.IndyTypeTransformerImpl;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.PatchByteCodeVersionTransformer;
import io.opentelemetry.javaagent.tooling.muzzle.HelperResourceBuilderImpl;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.util.IgnoreFailedTypeMatcher;
import io.opentelemetry.javaagent.tooling.util.NamedMatcher;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

public final class InstrumentationModuleInstaller {

  static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(InstrumentationModule.class);

  // Added here instead of AgentInstaller's ignores because it's relatively
  // expensive. https://github.com/DataDog/dd-trace-java/pull/1045
  public static final ElementMatcher.Junction<AnnotationSource> NOT_DECORATOR_MATCHER =
      not(isAnnotatedWith(named("javax.decorator.Decorator")));

  private final Instrumentation instrumentation;
  private final VirtualFieldImplementationInstallerFactory virtualFieldInstallerFactory =
      VirtualFieldImplementationInstallerFactory.getInstance();

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

    if (instrumentationModule.isIndyModule()) {
      return installIndyModule(instrumentationModule, parentAgentBuilder, config);
    } else {
      return installInjectingModule(instrumentationModule, parentAgentBuilder, config);
    }
  }

  private AgentBuilder installIndyModule(
      InstrumentationModule instrumentationModule,
      AgentBuilder parentAgentBuilder,
      ConfigProperties config) {
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

    List<String> injectedHelperClassNames;
    if (instrumentationModule instanceof ExperimentalInstrumentationModule) {
      ExperimentalInstrumentationModule experimentalInstrumentationModule =
          (ExperimentalInstrumentationModule) instrumentationModule;
      injectedHelperClassNames = experimentalInstrumentationModule.injectedClassNames();
    } else {
      injectedHelperClassNames = Collections.emptyList();
    }

    ClassInjectorImpl injectedClassesCollector = new ClassInjectorImpl(instrumentationModule);
    if (instrumentationModule instanceof ExperimentalInstrumentationModule) {
      ((ExperimentalInstrumentationModule) instrumentationModule)
          .injectClasses(injectedClassesCollector);
    }

    MuzzleMatcher muzzleMatcher = new MuzzleMatcher(logger, instrumentationModule, config);

    Function<ClassLoader, List<HelperClassDefinition>> helperGenerator =
        cl -> {
          List<HelperClassDefinition> helpers =
              new ArrayList<>(injectedClassesCollector.getClassesToInject(cl));
          for (String helperName : injectedHelperClassNames) {
            helpers.add(
                HelperClassDefinition.create(
                    helperName,
                    instrumentationModule.getClass().getClassLoader(),
                    InjectionMode.CLASS_ONLY));
          }
          return helpers;
        };

    AgentBuilder.Transformer helperInjector =
        new HelperInjector(
            instrumentationModule.instrumentationName(),
            helperGenerator,
            helperResourceBuilder.getResources(),
            instrumentationModule.getClass().getClassLoader(),
            instrumentation);

    VirtualFieldImplementationInstaller contextProvider =
        virtualFieldInstallerFactory.create(instrumentationModule);

    AgentBuilder agentBuilder = parentAgentBuilder;
    for (TypeInstrumentation typeInstrumentation : instrumentationModule.typeInstrumentations()) {
      AgentBuilder.Identified.Extendable extendableAgentBuilder =
          setTypeMatcher(agentBuilder, instrumentationModule, typeInstrumentation)
              .and(muzzleMatcher)
              .transform(new PatchByteCodeVersionTransformer());

      // TODO (Jonas): we are not calling
      // contextProvider.rewriteVirtualFieldsCalls(extendableAgentBuilder) anymore
      // As a result the advices should store `VirtualFields` as static variables instead of having
      // the lookup inline
      // We need to update our documentation on that
      extendableAgentBuilder =
          IndyModuleRegistry.initializeModuleLoaderOnMatch(
              instrumentationModule, extendableAgentBuilder);
      extendableAgentBuilder = extendableAgentBuilder.transform(helperInjector);
      extendableAgentBuilder = contextProvider.injectHelperClasses(extendableAgentBuilder);
      IndyTypeTransformerImpl typeTransformer =
          new IndyTypeTransformerImpl(extendableAgentBuilder, instrumentationModule);
      typeInstrumentation.transform(typeTransformer);
      extendableAgentBuilder = typeTransformer.getAgentBuilder();
      // TODO (Jonas): make instrumentation of bytecode older than 1.4 opt-in via a config option
      extendableAgentBuilder = contextProvider.injectFields(extendableAgentBuilder);

      agentBuilder = extendableAgentBuilder;
    }
    return agentBuilder;
  }

  private AgentBuilder installInjectingModule(
      InstrumentationModule instrumentationModule,
      AgentBuilder parentAgentBuilder,
      ConfigProperties config) {
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

    AtomicBoolean openerRun = new AtomicBoolean();
    AgentBuilder agentBuilder = parentAgentBuilder;
    for (TypeInstrumentation typeInstrumentation : typeInstrumentations) {
      AgentBuilder.Identified.Extendable extendableAgentBuilder =
          setTypeMatcher(agentBuilder, instrumentationModule, typeInstrumentation)
              .and(muzzleMatcher)
              .transform(ConstantAdjuster.instance())
              .transform(
                  (builder, typeDescription, classLoader, module, protectionDomain) -> {
                    if (JavaModule.isSupported()
                        && instrumentationModule instanceof ExperimentalInstrumentationModule
                        && !openerRun.get()) {
                      ExperimentalInstrumentationModule experimentalModule =
                          (ExperimentalInstrumentationModule) instrumentationModule;
                      experimentalModule
                          .jpmsModulesToOpen()
                          .forEach(
                              (javaModule, packages) -> {
                                ModuleOpener.open(
                                    instrumentation, javaModule, classLoader, packages);
                              });
                      openerRun.set(true);
                    }

                    return builder;
                  })
              .transform(helperInjector);
      extendableAgentBuilder = contextProvider.injectHelperClasses(extendableAgentBuilder);
      extendableAgentBuilder = contextProvider.rewriteVirtualFieldsCalls(extendableAgentBuilder);
      TypeTransformerImpl typeTransformer = new TypeTransformerImpl(extendableAgentBuilder);
      typeInstrumentation.transform(typeTransformer);
      extendableAgentBuilder = typeTransformer.getAgentBuilder();
      extendableAgentBuilder = contextProvider.injectFields(extendableAgentBuilder);

      agentBuilder = extendableAgentBuilder;
    }

    return agentBuilder;
  }

  private static AgentBuilder.Identified.Narrowable setTypeMatcher(
      AgentBuilder agentBuilder,
      InstrumentationModule instrumentationModule,
      TypeInstrumentation typeInstrumentation) {

    ElementMatcher.Junction<ClassLoader> moduleClassLoaderMatcher =
        instrumentationModule.classLoaderMatcher();

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

    return agentBuilder
        .type(
            new LoggingFailSafeMatcher<>(
                typeMatcher, "Instrumentation type matcher unexpected exception: " + typeMatcher),
            new LoggingFailSafeMatcher<>(
                classLoaderMatcher,
                "Instrumentation class loader matcher unexpected exception: " + classLoaderMatcher))
        .and(
            (typeDescription, classLoader, module, classBeingRedefined, protectionDomain) ->
                classLoader == null || NOT_DECORATOR_MATCHER.matches(typeDescription));
  }
}
