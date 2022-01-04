/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker;
import io.opentelemetry.javaagent.instrumentation.internal.reflection.RealInterfaces;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.TransformSafeLogger;
import io.opentelemetry.javaagent.tooling.instrumentation.InstrumentationModuleInstaller;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/**
 * A {@link VirtualFieldImplementationInstaller} which stores context in a field that is injected
 * into a class and falls back to global map if field was not injected.
 *
 * <p>This is accomplished by
 *
 * <ol>
 *   <li>Injecting a Dynamic Interface that provides getter and setter for context field
 *   <li>Applying Dynamic Interface to a type needing context, implementing interface methods and
 *       adding context storage field
 *   <li>Injecting a Dynamic Class created from {@link
 *       VirtualFieldImplementationsGenerator.VirtualFieldImplementationTemplate} to use injected
 *       field or fall back to a static map
 *   <li>Rewriting calls to the context-store to access the specific dynamic {@link
 *       VirtualFieldImplementationsGenerator.VirtualFieldImplementationTemplate}
 * </ol>
 *
 * <p>Example:<br>
 * <em>VirtualField.find(Runnable.class, RunnableState.class)</em><br>
 * is rewritten to:<br>
 * <em>FieldBackedImplementation$VirtualField$Runnable$RunnableState12345.getVirtualField(Runnable.class,
 * RunnableState.class)</em>
 */
final class FieldBackedImplementationInstaller implements VirtualFieldImplementationInstaller {

  private static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(FieldBackedImplementationInstaller.class);

  private static final boolean FIELD_INJECTION_ENABLED =
      Config.get().getBoolean("otel.javaagent.experimental.field-injection.enabled", true);

  private final Class<?> instrumenterClass;
  private final VirtualFieldMappings virtualFieldMappings;

  private final FieldAccessorInterfaces fieldAccessorInterfaces;
  private final AgentBuilder.Transformer fieldAccessorInterfacesInjector;

  private final VirtualFieldImplementations virtualFieldImplementations;
  private final AgentBuilder.Transformer virtualFieldImplementationsInjector;

  private final Instrumentation instrumentation;

  public FieldBackedImplementationInstaller(
      Class<?> instrumenterClass, VirtualFieldMappings virtualFieldMappings) {
    this.instrumenterClass = instrumenterClass;
    this.virtualFieldMappings = virtualFieldMappings;
    // This class is used only when running with javaagent, thus this calls is safe
    this.instrumentation = InstrumentationHolder.getInstrumentation();

    ByteBuddy byteBuddy = new ByteBuddy();
    fieldAccessorInterfaces =
        new FieldAccessorInterfacesGenerator(byteBuddy)
            .generateFieldAccessorInterfaces(virtualFieldMappings);
    fieldAccessorInterfacesInjector =
        bootstrapHelperInjector(fieldAccessorInterfaces.getAllInterfaces());
    virtualFieldImplementations =
        new VirtualFieldImplementationsGenerator(byteBuddy)
            .generateClasses(virtualFieldMappings, fieldAccessorInterfaces);
    virtualFieldImplementationsInjector =
        bootstrapHelperInjector(virtualFieldImplementations.getAllClasses());
  }

  @Override
  public AgentBuilder.Identified.Extendable rewriteVirtualFieldsCalls(
      AgentBuilder.Identified.Extendable builder) {
    if (!virtualFieldMappings.isEmpty()) {
      /*
       * Install transformer that rewrites accesses to context store with specialized bytecode that
       * invokes appropriate storage implementation.
       */
      builder =
          builder.transform(
              getTransformerForAsmVisitor(
                  new VirtualFieldFindRewriter(
                      instrumenterClass, virtualFieldMappings, virtualFieldImplementations)));
      builder = injectHelpersIntoBootstrapClassloader(builder);
    }
    return builder;
  }

  private AgentBuilder.Identified.Extendable injectHelpersIntoBootstrapClassloader(
      AgentBuilder.Identified.Extendable builder) {
    /*
     * We inject into bootstrap classloader because field accessor interfaces are needed by virtual
     * field implementations. Unfortunately this forces us to remove stored type checking because
     * actual classes may not be available at this point.
     */
    builder = builder.transform(fieldAccessorInterfacesInjector);

    /*
     * We inject virtual field implementations into bootstrap classloader because same implementation
     * may be used by different instrumentations and it has to use same static map in case of
     * fallback to map-backed storage.
     */
    builder = builder.transform(virtualFieldImplementationsInjector);
    return builder;
  }

  /** Get transformer that forces helper injection onto bootstrap classloader. */
  private AgentBuilder.Transformer bootstrapHelperInjector(
      Collection<DynamicType.Unloaded<?>> helpers) {
    // TODO: Better to pass through the context of the Instrumenter
    return new AgentBuilder.Transformer() {
      final HelperInjector injector =
          HelperInjector.forDynamicTypes(getClass().getSimpleName(), helpers, instrumentation);

      @Override
      public DynamicType.Builder<?> transform(
          DynamicType.Builder<?> builder,
          TypeDescription typeDescription,
          ClassLoader classLoader,
          JavaModule module) {
        return injector.transform(
            builder,
            typeDescription,
            // virtual field implementation classes will always go to the bootstrap
            null,
            module);
      }
    };
  }

  /*
  Set of pairs (type name, field type name) for which we have matchers installed.
  We use this to make sure we do not install matchers repeatedly for cases when same
  context class is used by multiple instrumentations.
   */
  private static final Set<Map.Entry<String, String>> INSTALLED_VIRTUAL_FIELD_MATCHERS =
      new HashSet<>();

  @Override
  public AgentBuilder.Identified.Extendable injectFields(
      AgentBuilder.Identified.Extendable builder) {

    if (FIELD_INJECTION_ENABLED) {
      for (Map.Entry<String, String> entry : virtualFieldMappings.entrySet()) {
        /*
         * For each virtual field defined in a current instrumentation we create an agent builder
         * that injects necessary fields.
         * Note: this synchronization should not have any impact on performance
         * since this is done when agent builder is being made, it doesn't affect actual
         * class transformation.
         */
        synchronized (INSTALLED_VIRTUAL_FIELD_MATCHERS) {
          if (INSTALLED_VIRTUAL_FIELD_MATCHERS.contains(entry)) {
            logger.trace("Skipping builder for {} {}", instrumenterClass.getName(), entry);
            continue;
          }

          logger.trace("Making builder for {} {}", instrumenterClass.getName(), entry);
          INSTALLED_VIRTUAL_FIELD_MATCHERS.add(entry);

          /*
           * For each virtual field defined in a current instrumentation we create an agent builder
           * that injects necessary fields.
           */
          builder =
              builder
                  .type(not(isAbstract()).and(hasSuperType(named(entry.getKey()))))
                  .and(safeToInjectFieldsMatcher())
                  .and(InstrumentationModuleInstaller.NOT_DECORATOR_MATCHER)
                  .transform(NoOpTransformer.INSTANCE);

          /*
           * We inject helpers here as well as when instrumentation is applied to ensure that
           * helpers are present even if instrumented classes are not loaded, but classes with state
           * fields added are loaded (e.g. sun.net.www.protocol.https.HttpsURLConnectionImpl).
           */
          builder = injectHelpersIntoBootstrapClassloader(builder);

          builder =
              builder.transform(
                  getTransformerForAsmVisitor(
                      new RealFieldInjector(
                          fieldAccessorInterfaces, entry.getKey(), entry.getValue())));
        }
      }
    }
    return builder;
  }

  private static AgentBuilder.RawMatcher safeToInjectFieldsMatcher() {
    return (typeDescription, classLoader, module, classBeingRedefined, protectionDomain) -> {
      /*
       * The idea here is that we can add fields if class is just being loaded
       * (classBeingRedefined == null) and we have to add same fields again if class we added
       * fields before is being transformed again. As we instrument Class#getInterfaces() to remove
       * interfaces added by us, we need to use our own helper class to get all the interfaces that
       * the class directly implements.
       */
      return classBeingRedefined == null
          || Arrays.asList(RealInterfaces.get(classBeingRedefined))
              .contains(VirtualFieldInstalledMarker.class);
    };
  }

  private static AgentBuilder.Transformer getTransformerForAsmVisitor(AsmVisitorWrapper visitor) {
    return (builder, typeDescription, classLoader, module) -> builder.visit(visitor);
  }

  // Originally found in AgentBuilder.Transformer.NoOp, but removed in 1.10.7
  enum NoOpTransformer implements AgentBuilder.Transformer {
    INSTANCE;

    @Override
    public DynamicType.Builder<?> transform(
        DynamicType.Builder<?> builder,
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module) {
      return builder;
    }
  }
}
