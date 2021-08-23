/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.restlet.v1_1.AbstractRestletServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.restlet.Restlet

class RestletServerTest extends AbstractRestletServerTest implements AgentTestTrait {

  @Override
  Restlet wrapRestlet(Restlet restlet, String path){
    return restlet
  }

}
