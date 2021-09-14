/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_0

import io.opentelemetry.instrumentation.restlet.v1_0.AbstractRestletServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.restlet.Restlet

class RestletServerTest extends AbstractRestletServerTest implements AgentTestTrait {

  @Override
  Restlet wrapRestlet(Restlet restlet, String path){
    return restlet
  }

}
