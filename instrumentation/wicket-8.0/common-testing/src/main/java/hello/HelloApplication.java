/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello;

import org.apache.wicket.Page;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.protocol.http.WebApplication;

public class HelloApplication extends WebApplication {
  @Override
  public Class<? extends Page> getHomePage() {
    return HelloPage.class;
  }

  @Override
  protected void init() {
    super.init();

    mountPage("/exception", ExceptionPage.class);
  }

  @Override
  public RuntimeConfigurationType getConfigurationType() {
    return RuntimeConfigurationType.DEPLOYMENT;
  }
}
