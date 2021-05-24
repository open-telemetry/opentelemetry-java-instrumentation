/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.eclipse.jetty.client.api.Request

class JettyHttpClient9AgentTest extends AbstractJettyClient9Test implements AgentTestTrait {

  @Override
  void attachInterceptor(Request jettyRequest, Context parentCtx) {
    //Do nothing
  }
}
