/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

public class ApplicationServiceInitListener implements VaadinServiceInitListener {
  @Override
  public void serviceInit(ServiceInitEvent event) {
    event.addBootstrapListener(
        response -> {
          // ensure that there is no need to request favicon.ico
          response.getDocument().head().append("<link rel=\"icon\" href=\"data:,\">");
        });
  }
}
