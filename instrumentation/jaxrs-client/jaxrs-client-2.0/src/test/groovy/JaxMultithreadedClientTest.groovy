/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static io.opentelemetry.auto.test.server.http.TestHttpServer.httpServer

import io.opentelemetry.auto.test.AgentTestRunner
import javax.ws.rs.client.Client
import org.glassfish.jersey.client.JerseyClientBuilder
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

class JaxMultithreadedClientTest extends AgentTestRunner {

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      prefix("success") {
        String msg = "Hello."
        response.status(200).send(msg)
      }
    }
  }

  def "multiple threads using the same builder works"() {
    given:
    def conds = new AsyncConditions(10)
    def uri = server.address.resolve("/success")
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
