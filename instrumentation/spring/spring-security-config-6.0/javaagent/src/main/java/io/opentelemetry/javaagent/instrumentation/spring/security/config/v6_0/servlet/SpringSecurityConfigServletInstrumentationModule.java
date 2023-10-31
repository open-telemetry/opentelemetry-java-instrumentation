/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.servlet;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrumentation module for servlet-based applications that use spring-security-config. */
@AutoService(InstrumentationModule.class)
public class SpringSecurityConfigServletInstrumentationModule extends InstrumentationModule {
  public SpringSecurityConfigServletInstrumentationModule() {
    super("spring-security-config-servlet", "spring-security-config-servlet-6.0");
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    /*
     * Since the only thing this module currently does is capture enduser attributes,
     * the module can be completely disabled if enduser attributes are disabled.
     *
     * If any functionality not related to enduser attributes is added to this module,
     * then this check will need to move elsewhere to only guard the enduser attributes logic.
     */
    return CommonConfig.get().getEnduserConfig().isAnyEnabled();
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
            "org.springframework.security.config.annotation.web.builders.HttpSecurity")
        .and(
            hasClassesNamed(
                "org.springframework.security.web.access.intercept.AuthorizationFilter"))
        .and(hasClassesNamed("jakarta.servlet.Servlet"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpSecurityInstrumentation());
  }
}
