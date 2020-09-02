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

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView

/**
 * None of the methods in this controller should be called because they are intercepted
 * by the filter
 */
@Controller
class TestController {

  @RequestMapping("/success")
  @ResponseBody
  String success() {
    throw new Exception("This should not be called")
  }

  @RequestMapping("/query")
  @ResponseBody
  String query_param(@RequestParam("some") String param) {
    throw new Exception("This should not be called")
  }

  @RequestMapping("/path/{id}/param")
  @ResponseBody
  String path_param(@PathVariable Integer id) {
    throw new Exception("This should not be called")
  }

  @RequestMapping("/redirect")
  @ResponseBody
  RedirectView redirect() {
    throw new Exception("This should not be called")
  }

  @RequestMapping("/error-status")
  ResponseEntity error() {
    throw new Exception("This should not be called")
  }

  @RequestMapping("/exception")
  ResponseEntity exception() {
    throw new Exception("This should not be called")
  }

  @ExceptionHandler
  ResponseEntity handleException(Throwable throwable) {
    new ResponseEntity(throwable.message, HttpStatus.INTERNAL_SERVER_ERROR)
  }
}
