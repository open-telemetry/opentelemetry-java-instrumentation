/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.filter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

/**
 * None of the methods in this controller should be called because they are intercepted by the
 * filter
 */
@Controller
public class TestController {

  @RequestMapping("/success")
  @ResponseBody
  String success() throws Exception {
    throw new Exception("This should not be called");
  }

  @RequestMapping("/query")
  @ResponseBody
  String queryParam(@RequestParam("some") String param) throws Exception {
    throw new Exception("This should not be called");
  }

  @RequestMapping("/path/{id}/param")
  @ResponseBody
  String pathParam(@PathVariable Integer id) throws Exception {
    throw new Exception("This should not be called");
  }

  @RequestMapping("/redirect")
  @ResponseBody
  RedirectView redirect() throws Exception {
    throw new Exception("This should not be called");
  }

  @RequestMapping("/error-status")
  ResponseEntity<Object> error() throws Exception {
    throw new Exception("This should not be called");
  }

  @RequestMapping("/exception")
  ResponseEntity<Object> exception() throws Exception {
    throw new Exception("This should not be called");
  }

  @RequestMapping("/captureHeaders")
  ResponseEntity<Object> captureHeaders() throws Exception {
    throw new Exception("This should not be called");
  }

  @RequestMapping("/child")
  @ResponseBody
  ResponseEntity<Object> indexedChild(@RequestParam("id") String id) throws Exception {
    throw new Exception("This should not be called");
  }

  @ExceptionHandler
  ResponseEntity<String> handleException(Throwable throwable) {
    return new ResponseEntity<>(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
