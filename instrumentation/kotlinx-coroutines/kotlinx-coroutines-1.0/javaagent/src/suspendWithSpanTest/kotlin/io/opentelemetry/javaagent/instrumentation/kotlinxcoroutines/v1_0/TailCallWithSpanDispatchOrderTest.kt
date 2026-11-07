/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

// When a tail call does not change the dispatcher, the caller resumes in the task that completes
// the callee, before work enqueued in the meantime, unless an undispatched withContext encloses the
// caller.
class TailCallWithSpanDispatchOrderTest {

  companion object {
    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()
  }

  @Test
  fun `tail delegated WithSpan on the same dispatcher resumes the caller before queued work`() {
    assertThat(order { unannotatedSameDispatcherTail(it) }).containsExactly("caller", "queued")
    assertThat(order { annotatedSameDispatcherTail(it) }).containsExactly("caller", "queued")

    testing.waitAndAssertTraces(
      { trace ->
        trace.hasSpansSatisfyingExactly(
          { it.hasName("same-dispatcher-tail").hasNoParent() },
        )
      },
    )
  }

  private fun order(call: suspend (CompletableDeferred<Unit>) -> Unit): List<String> {
    val events = mutableListOf<String>()
    runBlocking {
      val gate = CompletableDeferred<Unit>()
      launch {
        // Queues the callee's resumption, then a coroutine that runs after it.
        gate.complete(Unit)
        launch { events += "queued" }
      }
      call(gate)
      events += "caller"
    }
    return events
  }

  @WithSpan("same-dispatcher-tail")
  private suspend fun annotatedSameDispatcherTail(gate: CompletableDeferred<Unit>) = unannotatedSameDispatcherTail(gate)

  private suspend fun unannotatedSameDispatcherTail(gate: CompletableDeferred<Unit>) = withContext(NonCancellable) {
    gate.await()
  }
}
