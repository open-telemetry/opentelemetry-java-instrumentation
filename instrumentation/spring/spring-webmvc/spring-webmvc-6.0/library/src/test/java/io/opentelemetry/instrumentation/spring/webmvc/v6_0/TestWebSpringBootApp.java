/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v6_0;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import jakarta.servlet.Filter;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

@SpringBootApplication
class TestWebSpringBootApp {
  static final ServerEndpoint ASYNC_ENDPOINT = new ServerEndpoint("ASYNC", "async", 200, "success");

  static ConfigurableApplicationContext start(int port, String contextPath) {
    Properties props = new Properties();
    props.put("server.port", port);
    props.put("server.servlet.contextPath", contextPath);

    SpringApplication app = new SpringApplication(TestWebSpringBootApp.class);
    app.setDefaultProperties(props);
    return app.run();
  }

  @Bean
  Filter telemetryFilter() {
    return SpringWebMvcTelemetry.builder(GlobalOpenTelemetry.get())
        .setCapturedRequestHeaders(singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
        .build()
        .createServletFilter();
  }

  @Controller
  static class TestController {

    @RequestMapping("/success")
    @ResponseBody
    String success() {
      return controller(SUCCESS, SUCCESS::getBody);
    }

    @RequestMapping("/query")
    @ResponseBody
    String query_param(@RequestParam("some") String param) {
      return controller(QUERY_PARAM, () -> "some=" + param);
    }

    @RequestMapping("/redirect")
    @ResponseBody
    RedirectView redirect() {
      return controller(REDIRECT, () -> new RedirectView(REDIRECT.getBody()));
    }

    @RequestMapping("/error-status")
    ResponseEntity<String> error() {
      return controller(
          ERROR,
          () -> new ResponseEntity<>(ERROR.getBody(), HttpStatus.valueOf(ERROR.getStatus())));
    }

    @RequestMapping("/exception")
    ResponseEntity<String> exception() {
      return controller(
          EXCEPTION,
          () -> {
            throw new RuntimeException(EXCEPTION.getBody());
          });
    }

    @RequestMapping("/captureHeaders")
    ResponseEntity<String> capture_headers(
        @RequestHeader("X-Test-Request") String testRequestHeader) {
      return controller(
          CAPTURE_HEADERS,
          () ->
              ResponseEntity.ok()
                  .header("X-Test-Response", testRequestHeader)
                  .body(CAPTURE_HEADERS.getBody()));
    }

    @RequestMapping("/path/{id}/param")
    @ResponseBody
    String path_param(@PathVariable("id") int id) {
      return controller(PATH_PARAM, () -> String.valueOf(id));
    }

    @RequestMapping("/child")
    @ResponseBody
    String indexed_child(@RequestParam("id") String id) {
      return controller(
          INDEXED_CHILD,
          () -> {
            INDEXED_CHILD.collectSpanAttributes(name -> name.equals("id") ? id : null);
            return INDEXED_CHILD.getBody();
          });
    }

    @RequestMapping("/async")
    @ResponseBody
    CompletableFuture<String> async() {
      Context context = Context.current();
      return CompletableFuture.supplyAsync(
          () -> {
            // Sleep a bit so that the future completes after the controller method. This helps to
            // verify whether request ends after the future has completed not after when the
            // controller method has completed.
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            try (Scope ignored = context.makeCurrent()) {
              return controller(ASYNC_ENDPOINT, ASYNC_ENDPOINT::getBody);
            }
          });
    }

    @ExceptionHandler
    ResponseEntity<String> handleException(Throwable throwable) {
      return new ResponseEntity<>(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
