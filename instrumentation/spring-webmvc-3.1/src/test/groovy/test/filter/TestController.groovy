/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test.filter

import io.opentelemetry.auto.test.base.HttpServerTest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

@Controller
class TestController {

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

  @RequestMapping("/path/{id}/param")
  @ResponseBody
  String path_param(@PathVariable Integer id) {
    HttpServerTest.controller(PATH_PARAM) {
      "$id"
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

  @ExceptionHandler
  ResponseEntity handleException(Throwable throwable) {
    new ResponseEntity(throwable.message, HttpStatus.INTERNAL_SERVER_ERROR)
  }
}
