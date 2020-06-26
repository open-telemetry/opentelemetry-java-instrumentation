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

import org.glassfish.grizzly.IOStrategy
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.grizzly.strategies.LeaderFollowerNIOStrategy
import org.glassfish.grizzly.strategies.SameThreadIOStrategy
import org.glassfish.grizzly.strategies.SimpleDynamicNIOStrategy
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.glassfish.jersey.server.ResourceConfig

abstract class GrizzlyIOStrategyTest extends GrizzlyTest {

  static {
    System.setProperty("ota.integration.grizzly-http.enabled", "true")
  }

  @Override
  HttpServer startServer(int port) {
    ResourceConfig rc = new ResourceConfig()
    rc.register(SimpleExceptionMapper)
    rc.register(ServiceResource)
    def server = GrizzlyHttpServerFactory.createHttpServer(new URI("http://localhost:$port"), rc)
    server.getListener("grizzly").getTransport().setIOStrategy(strategy())
    // Default in NIOTransportBuilder is WorkerThreadIOStrategy, so don't need to retest that.s
    return server
  }

  abstract IOStrategy strategy()
}

class LeaderFollowerTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return LeaderFollowerNIOStrategy.instance
  }
}

class SameThreadTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return SameThreadIOStrategy.instance
  }
}

class SimpleDynamicTest extends GrizzlyIOStrategyTest {

  @Override
  IOStrategy strategy() {
    return SimpleDynamicNIOStrategy.instance
  }
}
