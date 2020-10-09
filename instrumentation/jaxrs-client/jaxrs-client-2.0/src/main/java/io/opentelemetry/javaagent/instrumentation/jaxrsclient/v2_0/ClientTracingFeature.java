/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jaxrsclient.v2_0;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientTracingFeature implements Feature {

  private static final Logger log = LoggerFactory.getLogger(ClientTracingFeature.class);

  @Override
  public boolean configure(FeatureContext context) {
    context.register(new ClientTracingFilter());
    log.debug("ClientTracingFilter registered");
    return true;
  }
}
