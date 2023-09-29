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
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringBootActuatorInstrumentationModule extends InstrumentationModule {

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
    helperResourceBuilder.registerForAllClassLoaders(
        "io/opentelemetry/javaagent/instrumentation/spring/actuator/v2_0/OpenTelemetryMeterRegistryAutoConfiguration.class");
  }

  @Override
  public boolean isIndyModule() {
    // can not access OpenTelemetryMeterRegistryAutoConfiguration
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AutoConfigurationImportSelectorInstrumentation());
  }
}
