/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import io.opentelemetry.instrumentation.test.base.HttpServerTest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

@Controller
class TestController {

  @RequestMapping("/basicsecured/endpoint")
  @ResponseBody
  String secureEndpoint() {
    HttpServerTest.controller(SUCCESS) {
      SUCCESS.body
    }
  }

  @RequestMapping("/success")
  @ResponseBody
  String success() {
    HttpServerTest.controller(SUCCESS) {
      SUCCESS.body
    }
  }

  @RequestMapping("/query")
  @ResponseBody
  String query_param(@RequestParam("some") String param) {
    HttpServerTest.controller(QUERY_PARAM) {
      "some=$param"
    }
  }

  @RequestMapping("/redirect")
  @ResponseBody
  RedirectView redirect() {
    HttpServerTest.controller(REDIRECT) {
      new RedirectView(REDIRECT.body)
    }
  }

  @RequestMapping("/error-status")
  ResponseEntity error() {
    HttpServerTest.controller(ERROR) {
      new ResponseEntity(ERROR.body, HttpStatus.valueOf(ERROR.status))
    }
  }

  @RequestMapping("/exception")
  ResponseEntity exception() {
    HttpServerTest.controller(EXCEPTION) {
      throw new Exception(EXCEPTION.body)
    }
  }

  @RequestMapping("/captureHeaders")
  ResponseEntity capture_headers(@RequestHeader("X-Test-Request") String testRequestHeader) {
    HttpServerTest.controller(CAPTURE_HEADERS) {
      ResponseEntity.ok()
        .header("X-Test-Response", testRequestHeader)
        .body(CAPTURE_HEADERS.body)
    }
  }

  @RequestMapping("/path/{id}/param")
  @ResponseBody
  String path_param(@PathVariable("id") int id) {
    HttpServerTest.controller(PATH_PARAM) {
      id
    }
  }

  @RequestMapping("/child")
  @ResponseBody
  String indexed_child(@RequestParam("id") String id) {
    HttpServerTest.controller(INDEXED_CHILD) {
      INDEXED_CHILD.collectSpanAttributes { it == "id" ? id : null }
      INDEXED_CHILD.body
    }
  }

  @ExceptionHandler
  ResponseEntity handleException(Throwable throwable) {
    new ResponseEntity(throwable.message, HttpStatus.INTERNAL_SERVER_ERROR)
  }
}
