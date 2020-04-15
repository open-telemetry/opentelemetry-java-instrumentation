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
package io.opentelemetry.auto.instrumentation.servlet.v2_3;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class StatusSavingHttpServletResponseWrapper extends HttpServletResponseWrapper {
  public int status = 200;

  public StatusSavingHttpServletResponseWrapper(final HttpServletResponse response) {
    super(response);
  }

  @Override
  public void sendError(final int status) throws IOException {
    this.status = status;
    super.sendError(status);
  }

  @Override
  public void sendError(final int status, final String message) throws IOException {
    this.status = status;
    super.sendError(status, message);
  }

  @Override
  public void sendRedirect(final String location) throws IOException {
    status = 302;
    super.sendRedirect(location);
  }

  @Override
  public void setStatus(final int status) {
    this.status = status;
    super.setStatus(status);
  }

  @Override
  public void setStatus(final int status, final String message) {
    this.status = status;
    super.setStatus(status, message);
  }
}
