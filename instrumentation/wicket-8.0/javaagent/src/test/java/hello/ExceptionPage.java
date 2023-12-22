/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello;

import org.apache.wicket.markup.html.WebPage;

public class ExceptionPage extends WebPage {
  public ExceptionPage() throws Exception {
    throw new Exception("test exception");
  }
}
