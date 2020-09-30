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

import static JMS2Test.consumerSpan
import static JMS2Test.producerSpan

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.instrumentation.auto.jms.Operation
import javax.jms.ConnectionFactory
import listener.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.jms.core.JmsTemplate

class SpringListenerJMS2Test extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.setProperty("otel.trace.classes.exclude", "org.springframework.jms.config.JmsListenerEndpointRegistry\$AggregatingCallback,org.springframework.context.support.DefaultLifecycleProcessor\$1")
    }
  }

  def cleanupSpec() {
    ConfigUtils.updateConfig {
      System.clearProperty("otel.trace.classes.exclude")
    }
  }

  def "receiving message in spring listener generates spans"() {
    setup:
    def context = new AnnotationConfigApplicationContext(Config)
    def factory = context.getBean(ConnectionFactory)
    def template = new JmsTemplate(factory)
    template.convertAndSend("SpringListenerJMS2", "a message")

    expect:
    assertTraces(2) {
      trace(0, 3) {
        producerSpan(it, 0, "queue", "SpringListenerJMS2")
        consumerSpan(it, 1, "queue", "SpringListenerJMS2", "", span(0), Operation.receive)
        consumerSpan(it, 2, "queue", "SpringListenerJMS2", "", span(0), Operation.process)
      }
      trace(1, 1) {
        consumerSpan(it, 0, "queue", "SpringListenerJMS2", null, null, Operation.receive)
      }
    }

    cleanup:
    context.close()
  }
}
