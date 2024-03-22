/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

public class HelloPage extends WebPage {
  private static final long serialVersionUID = 1L;

  public HelloPage() {
    add(new Label("message", "Hello World!"));
  }
}
