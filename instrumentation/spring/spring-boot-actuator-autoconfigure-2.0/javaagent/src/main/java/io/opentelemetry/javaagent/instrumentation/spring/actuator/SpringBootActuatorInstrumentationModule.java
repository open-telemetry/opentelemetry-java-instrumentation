/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.actuator;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringBootActuatorInstrumentationModule extends InstrumentationModule {

  public SpringBootActuatorInstrumentationModule() {
    super("spring-boot-actuator-autoconfigure", "spring-boot-actuator-autoconfigure-2.0");
  }

  @Override
  public void registerHelperResources(HelperResourceBuilder helperResourceBuilder) {
    // autoconfigure classes are loaded as resources using ClassPathResource
    // this line will make OpenTelemetryMeterRegistryAutoConfiguration available to all
    // classloaders, so that the bean classloader (different than the instrumented classloader) can
    // load it
    helperResourceBuilder.registerForAllClassLoaders(
        "io/opentelemetry/javaagent/instrumentation/spring/actuator/OpenTelemetryMeterRegistryAutoConfiguration.class");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.micrometer1shim.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AutoConfigurationImportSelectorInstrumentation());
  }
}
