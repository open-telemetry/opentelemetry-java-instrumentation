/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sofa.rpc;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import java.util.List;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SofaRpcInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SofaRpcInstrumentationModule() {
    super("sofa-rpc", "sofa-rpc");
  }

  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    helperResourceBuilder.register(
        "META-INF/services/sofa-rpc/com.alipay.sofa.rpc.filter.Filter",
        "sofa-rpc/META-INF/services/sofa-rpc/com.alipay.sofa.rpc.filter.Filter");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("com.alipay.sofa.rpc.filter.Filter");
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    injector
        .proxyBuilder(
            "io.opentelemetry.javaagent.instrumentation.sofa.rpc.OpenTelemetryClientFilter")
        .inject(InjectionMode.CLASS_ONLY);
    injector
        .proxyBuilder(
            "io.opentelemetry.javaagent.instrumentation.sofa.rpc.OpenTelemetryServerFilter")
        .inject(InjectionMode.CLASS_ONLY);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ResourceInjectingTypeInstrumentation());
  }

  // A type instrumentation is needed to trigger resource injection.
  public static class ResourceInjectingTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.alipay.sofa.rpc.ext.ExtensionLoader");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      // Nothing to transform, this type instrumentation is only used for injecting resources.
    }
  }
}

