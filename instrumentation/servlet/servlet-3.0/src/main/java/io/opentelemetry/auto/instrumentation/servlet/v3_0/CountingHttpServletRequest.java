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

package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * TODO(anuraaga): Implement counting, for now it just ensures startAsync is called with the wrapped
 * objects.
 */
public class CountingHttpServletRequest extends HttpServletRequestWrapper {

  private final HttpServletResponse response;

  public CountingHttpServletRequest(HttpServletRequest request, HttpServletResponse response) {
    super(request);
    this.response = response;
  }

  @Override
  public AsyncContext startAsync() throws IllegalStateException {
    return startAsync(this, response);
  }
}
