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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer
import org.springframework.context.annotation.Bean
import reactor.ipc.netty.resources.LoopResources
import server.SpringWebFluxTestApplication

/**
 * Run all Webflux tests under netty event loop having only 1 thread.
 * Some of the bugs are better visible in this setup because same thread is reused
 * for different requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [SpringWebFluxTestApplication, ForceSingleThreadedNettyAutoConfiguration])
class SingleThreadedSpringWebfluxTest extends SpringWebfluxTest {

  @TestConfiguration
  static class ForceSingleThreadedNettyAutoConfiguration {
    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      def factory = new NettyReactiveWebServerFactory()
      NettyServerCustomizer customizer = { builder -> builder.loopResources(LoopResources.create("my-http", 1, true)) }
      factory.addServerCustomizers(customizer)
      return factory
    }
  }

}
