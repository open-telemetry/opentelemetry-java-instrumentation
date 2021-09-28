/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.TransformSafeLogger;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.bytebuddy.LoggingFailSafeMatcher;
import io.opentelemetry.javaagent.tooling.context.FieldBackedProvider;
import io.opentelemetry.javaagent.tooling.context.InstrumentationContextProvider;
import io.opentelemetry.javaagent.tooling.context.NoopContextProvider;
import io.opentelemetry.javaagent.tooling.muzzle.ContextStoreMappings;
import io.opentelemetry.javaagent.tooling.muzzle.HelperResourceBuilderImpl;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationContextBuilderImpl;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.Mismatch;
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceMatcher;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstrumentationModuleInstaller {
  private static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(InstrumentationModule.class);
  private static final Logger muzzleLogger = LoggerFactory.getLogger("muzzleMatcher");
  private final Instrumentation instrumentation;

  // Added here instead of AgentInstaller's ignores because it's relatively
  // expensive. https://github.com/DataDog/dd-trace-java/pull/1045
  public static final ElementMatcher.Junction<AnnotationSource> NOT_DECORATOR_MATCHER =
      not(isAnnotatedWith(named("javax.decorator.Decorator")));

  public InstrumentationModuleInstaller(Instrumentation instrumentation) {
    this.instrumentation = instrumentation;
  }

  AgentBuilder install(
      InstrumentationModule instrumentationModule, AgentBuilder parentAgentBuilder) {
    if (!instrumentationModule.isEnabled()) {
      logger.debug("Instrumentation {} is disabled", instrumentationModule.instrumentationName());
      return parentAgentBuilder;
    }
    List<String> helperClassNames = instrumentationModule.getMuzzleHelperClassNames();
    HelperResourceBuilderImpl helperResourceBuilder = new HelperResourceBuilderImpl();
    List<String> helperResourceNames = instrumentationModule.helperResourceNames();
    for (String helperResourceName : helperResourceNames) {
      helperResourceBuilder.register(helperResourceName);
    }
    instrumentationModule.registerHelperResources(helperResourceBuilder);
    List<TypeInstrumentation> typeInstrumentations = instrumentationModule.typeInstrumentations();
    if (typeInstrumentations.isEmpty()) {
      if (!helperClassNames.isEmpty() || !helperResourceBuilder.getResources().isEmpty()) {
        logger.warn(
            "Helper classes and resources won't be injected if no types are instrumented: {}",
            instrumentationModule.instrumentationName());
      }

      return parentAgentBuilder;
    }

    ElementMatcher.Junction<ClassLoader> moduleClassLoaderMatcher =
        instrumentationModule.classLoaderMatcher();
    MuzzleMatcher muzzleMatcher = new MuzzleMatcher(instrumentationModule);
    AgentBuilder.Transformer helperInjector =
        new HelperInjector(
            instrumentationModule.instrumentationName(),
            helperClassNames,
            helperResourceBuilder.getResources(),
            Utils.getExtensionsClassLoader(),
            instrumentation);
    InstrumentationContextProvider contextProvider =
        createInstrumentationContextProvider(instrumentationModule);

    AgentBuilder agentBuilder = parentAgentBuilder;
    for (TypeInstrumentation typeInstrumentation : typeInstrumentations) {
      AgentBuilder.Identified.Extendable extendableAgentBuilder =
          agentBuilder
              .type(
                  new LoggingFailSafeMatcher<>(
                      typeInstrumentation.typeMatcher(),
                      "Instrumentation type matcher unexpected exception: "
                          + typeInstrumentation.typeMatcher()),
                  new LoggingFailSafeMatcher<>(
                      moduleClassLoaderMatcher.and(typeInstrumentation.classLoaderOptimization()),
                      "Instrumentation class loader matcher unexpected exception: "
                          + typeInstrumentation.classLoaderOptimization()))
              .and(NOT_DECORATOR_MATCHER)
              .and(muzzleMatcher)
              .transform(ConstantAdjuster.instance())
              .transform(helperInjector);
      extendableAgentBuilder = contextProvider.instrumentationTransformer(extendableAgentBuilder);
      TypeTransformerImpl typeTransformer = new TypeTransformerImpl(extendableAgentBuilder);
      typeInstrumentation.transform(typeTransformer);
      extendableAgentBuilder = typeTransformer.getAgentBuilder();
      extendableAgentBuilder = contextProvider.additionalInstrumentation(extendableAgentBuilder);

      agentBuilder = extendableAgentBuilder;
    }

    return agentBuilder;
  }

  private static InstrumentationContextProvider createInstrumentationContextProvider(
      InstrumentationModule instrumentationModule) {

    if (instrumentationModule instanceof InstrumentationModuleMuzzle) {
      InstrumentationContextBuilderImpl builder = new InstrumentationContextBuilderImpl();
      ((InstrumentationModuleMuzzle) instrumentationModule)
          .registerMuzzleContextStoreClasses(builder);
      ContextStoreMappings mappings = builder.build();
      if (!mappings.isEmpty()) {
        return FieldBackedProviderFactory.get(instrumentationModule.getClass(), mappings);
      }
    } else {
      logger.debug(
          "Found InstrumentationModule which does not implement InstrumentationModuleMuzzle: {}",
          instrumentationModule);
    }

    return NoopContextProvider.INSTANCE;
  }

  private static class FieldBackedProviderFactory {
    static {
      InstrumentationContext.internalSetContextStoreSupplier(FieldBackedProvider::getContextStore);
    }

    static FieldBackedProvider get(
        Class<?> instrumenterClass, ContextStoreMappings contextStoreMappings) {
      return new FieldBackedProvider(instrumenterClass, contextStoreMappings);
    }
  }

  /**
   * A ByteBuddy matcher that decides whether this instrumentation should be applied. Calls
   * generated {@link ReferenceMatcher}: if any mismatch with the passed {@code classLoader} is
   * found this instrumentation is skipped.
   */
  private static class MuzzleMatcher implements AgentBuilder.RawMatcher {
    private final InstrumentationModule instrumentationModule;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile ReferenceMatcher referenceMatcher;

    private MuzzleMatcher(InstrumentationModule instrumentationModule) {
      this.instrumentationModule = instrumentationModule;
    }

    @Override
    public boolean matches(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain) {
      ReferenceMatcher muzzle = getReferenceMatcher();
      if (classLoader == BOOTSTRAP_LOADER) {
        classLoader = Utils.getBootstrapProxy();
      }
      boolean isMatch = muzzle.matches(classLoader);

      if (!isMatch) {
        if (muzzleLogger.isWarnEnabled()) {
          muzzleLogger.warn(
              "Instrumentation skipped, mismatched references were found: {} [class {}] on {}",
              instrumentationModule.instrumentationName(),
              instrumentationModule.getClass().getName(),
              classLoader);
          List<Mismatch> mismatches = muzzle.getMismatchedReferenceSources(classLoader);
          for (Mismatch mismatch : mismatches) {
            muzzleLogger.warn("-- {}", mismatch);
          }
        }
      } else {
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Applying instrumentation: {} [class {}] on {}",
              instrumentationModule.instrumentationName(),
              instrumentationModule.getClass().getName(),
              classLoader);
        }
      }

      return isMatch;
    }

    // ReferenceMatcher internally caches the muzzle check results per classloader, that's why we
    // keep its instance in a field
    // it is lazily created to avoid unnecessarily loading the muzzle references from the module
    // during the agent setup
    private ReferenceMatcher getReferenceMatcher() {
      if (initialized.compareAndSet(false, true)) {
        referenceMatcher = ReferenceMatcher.of(instrumentationModule);
      }
      return referenceMatcher;
    }
  }
}
