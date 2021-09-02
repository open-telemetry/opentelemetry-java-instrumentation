/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpStatus
import io.opentelemetry.testing.internal.armeria.common.MediaType
import io.opentelemetry.testing.internal.armeria.server.ServerBuilder
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.ServerExtension
import org.glassfish.jersey.client.JerseyClientBuilder
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

import javax.ws.rs.client.Client

class JaxMultithreadedClientTest extends AgentInstrumentationSpecification {

  @Shared
  def server = new ServerExtension() {
    @Override
    protected void configure(ServerBuilder sb) throws Exception {
      sb.service("/success") { ctx, req ->
        HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT, "Hello.")
      }
    }
  }

  def setupSpec() {
    server.start()
  }

  def cleanupSpec() {
    server.stop()
  }

  def "multiple threads using the same builder works"() {
    given:
    def conds = new AsyncConditions(10)
    def uri = server.httpUri().resolve("/success")
    def builder = new JerseyClientBuilder()

    // Start 10 threads and do 50 requests each
    when:
    (1..10).each {
      Thread.start {
        boolean hadErrors = (1..50).any {
          try {
            Client client = builder.build()
            client.target(uri).request().get()
          } catch (Exception e) {
            e.printStackTrace()
            return true
          }
          return false
        }

        conds.evaluate {
          assert !hadErrors
        }
      }
    }

    then:
    conds.await(30)
  }
}
