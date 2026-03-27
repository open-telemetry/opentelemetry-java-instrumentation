/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.webflux;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

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
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Ensure this module is only applied to Spring Security >= 6.0. This instrumentation might
    // work with older versions of Spring Security, but since it is bundled together with the
    // servlet based instrumentation, that does not work with oder versions, we also limit this
    // module to only work with Spring Security >= 6.0.
    return hasClassesNamed(
        "org.springframework.security.authentication.ObservationAuthenticationManager");
  }

  @Override
  public boolean defaultEnabled() {
    return super.defaultEnabled()
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
