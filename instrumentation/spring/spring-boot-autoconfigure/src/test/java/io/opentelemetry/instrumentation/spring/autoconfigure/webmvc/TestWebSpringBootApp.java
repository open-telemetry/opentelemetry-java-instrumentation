/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.webmvc;

import static java.util.Collections.singleton;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootApplication
class TestWebSpringBootApp {

  static ConfigurableApplicationContext start(int port) {
    Properties props = new Properties();
    props.put("server.port", port);
    props.put("server.servlet.contextPath", "/test-app");

    SpringApplication app = new SpringApplication(TestWebSpringBootApp.class);
    app.setDefaultProperties(props);
    app.addPrimarySources(singleton(WebMvcFilterAutoConfiguration.class));
    return app.run();
  }

  @Bean
  OpenTelemetry openTelemetry() {
    return GlobalOpenTelemetry.get();
  }

  @Controller
  static class TestController {

    @RequestMapping("/test-route/{id}")
    @ResponseBody
    String testEndpoint(@PathVariable("id") int id) {
      return String.valueOf(id);
    }
  }
}
