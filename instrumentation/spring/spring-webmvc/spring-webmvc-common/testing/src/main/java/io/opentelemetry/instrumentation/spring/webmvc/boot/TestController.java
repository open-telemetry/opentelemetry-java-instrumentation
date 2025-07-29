/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.boot;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import java.util.Objects;
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

@Controller
public class TestController {

  @RequestMapping("/basicsecured/endpoint")
  @ResponseBody
  String secureEndpoint() {
    return controller(SUCCESS, SUCCESS::getBody);
  }

  @RequestMapping("/success")
  @ResponseBody
  String success() {
    return controller(SUCCESS, SUCCESS::getBody);
  }

  @RequestMapping("/query")
  @ResponseBody
  String queryParam(@RequestParam("some") String param) {
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
        () ->
            ResponseEntity.status(HttpStatus.valueOf(ERROR.getStatus()).value())
                .body(ERROR.getBody()));
  }

  @SuppressWarnings("ThrowSpecificExceptions")
  @RequestMapping("/exception")
  ResponseEntity<String> exception() {
    return controller(
        EXCEPTION,
        () -> {
          throw new RuntimeException(EXCEPTION.getBody());
        });
  }

  @RequestMapping("/captureHeaders")
  ResponseEntity<String> captureHeaders(@RequestHeader("X-Test-Request") String testRequestHeader) {
    return controller(
        CAPTURE_HEADERS,
        () ->
            ResponseEntity.ok()
                .header("X-Test-Response", testRequestHeader)
                .body(CAPTURE_HEADERS.getBody()));
  }

  @RequestMapping("/path/{id}/param")
  @ResponseBody
  String pathParam(@PathVariable("id") int id) {
    return controller(PATH_PARAM, () -> String.valueOf(id));
  }

  @RequestMapping("/child")
  @ResponseBody
  String indexedChild(@RequestParam("id") String id) {
    return controller(
        INDEXED_CHILD,
        () -> {
          INDEXED_CHILD.collectSpanAttributes(it -> Objects.equals(it, "id") ? id : null);
          return INDEXED_CHILD.getBody();
        });
  }

  @ExceptionHandler
  ResponseEntity<String> handleException(Throwable throwable) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
        .body(throwable.getMessage());
  }
}
