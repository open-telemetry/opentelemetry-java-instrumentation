/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello

import org.apache.wicket.markup.html.WebPage
import org.apache.wicket.markup.html.basic.Label

class HelloPage extends WebPage {
  HelloPage() {
    add(new Label("message", "Hello World!"))
  }
}
