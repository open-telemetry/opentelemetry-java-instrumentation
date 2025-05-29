/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.webflux;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

/** Instrumentation module for webflux-based applications that use spring-security-config. */
@AutoService(InstrumentationModule.class)
public class SpringSecurityConfigWebFluxInstrumentationModule extends InstrumentationModule {

  public SpringSecurityConfigWebFluxInstrumentationModule() {
    super(
        "spring-security-config",
        "spring-security-config-6.0",
        "spring-security-config-webflux",
        "spring-security-config-webflux-6.0");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    return super.defaultEnabled(config)
        /*
         * Since the only thing this module currently does is capture enduser attributes,
         * the module can be completely disabled if enduser attributes are disabled.
         *
         * If any functionality not related to enduser attributes is added to this module,
         * then this check will need to move elsewhere to only guard the enduser attributes logic.
         */
        && AgentCommonConfig.get().getEnduserConfig().isAnyEnabled();
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ServerHttpSecurityInstrumentation());
  }
}
