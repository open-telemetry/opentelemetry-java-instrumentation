/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.actuator.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringBootActuatorInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public SpringBootActuatorInstrumentationModule() {
    super(
        "spring-boot-actuator-autoconfigure",
        "spring-boot-actuator-autoconfigure-2.0",
        // share the instrumentation name with MicrometerInstrumentationModule to lessen the users'
        // confusion
        "micrometer");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in micrometer-core 1.5
    return hasClassesNamed("io.micrometer.core.instrument.config.validate.Validated");
  }

  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    // autoconfigure classes are loaded as resources using ClassPathResource
    // this line will make OpenTelemetryMeterRegistryAutoConfiguration available to all
    // classloaders, so that the bean class loader (different from the instrumented class loader)
    // can load it
    if (!isIndyModule()) {
      // For indy module the proxy-bytecode will be injected as resource by injectClasses()
      helperResourceBuilder.registerForAllClassLoaders(
          "io/opentelemetry/javaagent/instrumentation/spring/actuator/v2_0/OpenTelemetryMeterRegistryAutoConfiguration.class");
    }
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    injector
        .proxyBuilder(
            "io.opentelemetry.javaagent.instrumentation.spring.actuator.v2_0.OpenTelemetryMeterRegistryAutoConfiguration")
        .inject(InjectionMode.CLASS_AND_RESOURCE);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AutoConfigurationImportSelectorInstrumentation());
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // produces a lot of metrics that are already captured - e.g. JVM memory usage
    return false;
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
