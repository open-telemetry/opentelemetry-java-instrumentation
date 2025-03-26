/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data.v3_0

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository.CustomerRepository
import io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository.PersistenceConfig
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KotlinSpringDataTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()
  }

  private var applicationContext: ConfigurableApplicationContext? = null
  private var customerRepository: CustomerRepository? = null

  @BeforeAll
  fun setUp() {
    applicationContext = AnnotationConfigApplicationContext(PersistenceConfig::class.java)
    customerRepository = applicationContext!!.getBean(CustomerRepository::class.java)
  }

  @AfterAll
  fun cleanUp() {
    applicationContext!!.close()
  }

  @Test
  fun `trace findById`() {
    runBlocking {
      val customer = customerRepository?.findById(1)
      Assertions.assertThat(customer?.name).isEqualTo("Name")
    }

    testing.waitAndAssertTraces({ trace ->
      trace.hasSpansSatisfyingExactly({
        it.hasName("CustomerRepository.findById").hasNoParent()
      }, {
        it.hasName("SELECT db.customer").hasParent(trace.getSpan(0))
      })
    })
  }
}
