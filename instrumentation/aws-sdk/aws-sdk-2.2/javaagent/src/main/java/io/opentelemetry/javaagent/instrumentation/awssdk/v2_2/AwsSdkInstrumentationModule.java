/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class AwsSdkInstrumentationModule extends AbstractAwsSdkInstrumentationModule
    implements ExperimentalInstrumentationModule {
  public AwsSdkInstrumentationModule() {
    super("aws-sdk-2.2-core");
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> getAdditionalHelperClassNames() {
    if (isIndyModule()) {
      // With the invokedynamic approach, the SnsInstrumentationModule and SqsInstrumentationModule
      // are not required anymore, because we don't inject the helpers which reference potentially
      // missing classes
      // Instead, those are loaded by the InstrumentationModuleClassloader and LinkageErrors are
      // caught just like when using those classes as library instrumentation
      List<String> helpers = new ArrayList<>();
      InstrumentationModule[] modules = {
        new SnsInstrumentationModule(), new SqsInstrumentationModule()
      };
      for (InstrumentationModule include : modules) {
        try {
          List<String> moduleRefs =
              (List<String>)
                  include.getClass().getDeclaredMethod("getMuzzleHelperClassNames").invoke(include);
          helpers.addAll(moduleRefs);
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }
      return helpers;
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Injects resource file with reference to our {@link TracingExecutionInterceptor} to allow SDK's
   * service loading mechanism to pick it up.
   */
  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    helperResourceBuilder.register("software/amazon/awssdk/global/handlers/execution.interceptors");
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    injector
        .proxyBuilder(
            "io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor",
            "io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor")
        .inject(InjectionMode.CLASS_ONLY);
  }

  @Override
  void doTransform(TypeTransformer transformer) {
    // Nothing to transform, this type instrumentation is only used for injecting resources.
  }
}
