/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello

import org.apache.wicket.Page
import org.apache.wicket.RuntimeConfigurationType
import org.apache.wicket.protocol.http.WebApplication

class HelloApplication extends WebApplication {
  @Override
  Class<? extends Page> getHomePage() {
    HelloPage
  }

  @Override
  protected void init() {
    super.init()

    mountPage("/exception", ExceptionPage)
  }

  @Override
  RuntimeConfigurationType getConfigurationType() {
    return RuntimeConfigurationType.DEPLOYMENT
  }
}
