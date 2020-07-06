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

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView

@Controller
class TestController {

  @RequestMapping("/success")
  @ResponseBody
  String success() {
    // stub to test that route is captured when intercepted by filter
  }

  @RequestMapping("/query")
  @ResponseBody
  String query_param(@RequestParam("some") String param) {
    // stub to test that route is captured when intercepted by filter
  }

  @RequestMapping("/path/{id}/param")
  @ResponseBody
  String path_param(@PathVariable Integer id) {
    // stub to test that route is captured when intercepted by filter
  }

  @RequestMapping("/redirect")
  @ResponseBody
  RedirectView redirect() {
    // stub to test that route is captured when intercepted by filter
  }

  @RequestMapping("/error-status")
  ResponseEntity error() {
    // stub to test that route is captured when intercepted by filter
  }

  @RequestMapping("/exception")
  ResponseEntity exception() {
    // stub to test that route is captured when intercepted by filter
  }

  @ExceptionHandler
  ResponseEntity handleException(Throwable throwable) {
    // stub to test that route is captured when intercepted by filter
  }
}
