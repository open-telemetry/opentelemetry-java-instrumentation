/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class DubboInstrumentationModule extends InstrumentationModule {
  public DubboInstrumentationModule() {
    super("apache-dubbo", "apache-dubbo-2.7");
  }

  @Override
  public String[] helperResourceNames() {
    return new String[] {
      "META-INF/services/org.apache.dubbo.rpc.Filter",
    };
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.dubbo.rpc.Filter");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new ResourceInjectingTypeInstrumentation());
  }

  // A type instrumentation is needed to trigger resource injection.
  public static class ResourceInjectingTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.dubbo.common.extension.ExtensionLoader");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      // Nothing to transform, this type instrumentation is only used for injecting resources.
      return Collections.emptyMap();
    }
  }
}
