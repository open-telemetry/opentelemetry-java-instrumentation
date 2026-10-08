/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.servlet;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrumentation module for servlet-based applications that use spring-security-config. */
@AutoService(InstrumentationModule.class)
public class SpringSecurityConfigServletInstrumentationModule extends InstrumentationModule {
  public SpringSecurityConfigServletInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview()
            ? "spring-security-config-6.0"
            : "spring-security-config",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-security-config"}
            : new String[] {
              "spring-security-config-6.0",
              "spring-security-config-servlet",
              "spring-security-config-servlet-6.0"
            });
  }

  @Override
  public boolean defaultEnabled() {
    return super.defaultEnabled()
        /*
         * Since the only thing this module currently does is capture user identity attributes,
         * the module can be completely disabled if user identity capture is disabled.
         *
         * If any functionality not related to user identity attributes is added to this module,
         * then this check will need to move elsewhere to only guard the identity capture logic.
         */
        && AgentCommonConfig.get().getUserConfig().isAnyEnabled();
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    /*
     * Ensure this module is only applied to Spring Security >= 6.0,
     * since Spring Security >= 6.0 uses Jakarta EE rather than Java EE,
     * and this instrumentation module uses Jakarta EE.
     */
    // added in 6.0
    return hasClassesNamed(
        "org.springframework.security.authentication.ObservationAuthenticationManager");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpSecurityInstrumentation());
  }
}
