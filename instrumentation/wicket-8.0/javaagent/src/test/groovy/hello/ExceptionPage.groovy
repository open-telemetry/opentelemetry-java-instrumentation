/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello

import org.apache.wicket.markup.html.WebPage

class ExceptionPage extends WebPage {
  ExceptionPage() {
    throw new Exception("test exception")
  }
}
