/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.examples.springwar;

import java.security.CodeSource;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.DispatcherServlet;

@RestController
public final class ProbeController {
  @GetMapping(value = "/hello", produces = "text/plain")
  public String hello() {
    return "hello from Spring MVC\n";
  }

  @GetMapping(value = "/origins", produces = "text/plain")
  public String origins(HttpServletRequest request) {
    StringBuilder result = new StringBuilder();
    ClassLoader loader = DispatcherServlet.class.getClassLoader();
    for (String name :
        new String[] {
          "org.springframework.web.context.ContextLoaderListener",
          "org.springframework.web.servlet.DispatcherServlet",
          "org.springframework.web.util.ServletRequestPathUtils",
          "org.springframework.http.server.RequestPath"
        }) {
      try {
        describe(result, Class.forName(name, false, loader));
      } catch (ClassNotFoundException e) {
        result.append(name).append(" unavailable=").append(e).append('\n');
      }
    }
    WebApplicationContext root =
        WebApplicationContextUtils.getRequiredWebApplicationContext(request.getServletContext());
    boolean present = root.containsBean("otelAutoDispatcherFilter");
    result.append("otelAutoDispatcherFilter=").append(present).append('\n');
    if (present) {
      describe(result, root.getBean("otelAutoDispatcherFilter").getClass());
    }
    return result.toString();
  }

  private static void describe(StringBuilder result, Class<?> type) {
    ClassLoader loader = type.getClassLoader();
    CodeSource source = type.getProtectionDomain().getCodeSource();
    result
        .append(type.getName())
        .append(" loader=")
        .append(loader == null ? "bootstrap" : loader.getClass().getName())
        .append('@')
        .append(Integer.toHexString(System.identityHashCode(loader)))
        .append(" source=")
        .append(source == null ? "none" : source.getLocation())
        .append('\n');
  }
}
