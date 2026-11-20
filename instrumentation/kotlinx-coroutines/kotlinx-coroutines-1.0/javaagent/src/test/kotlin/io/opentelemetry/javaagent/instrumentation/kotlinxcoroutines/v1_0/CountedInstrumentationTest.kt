/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.instrumentation.annotations.Counted
import io.opentelemetry.instrumentation.annotations.WithSpan
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.lang.module.ModuleFinder
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class CountedInstrumentationTest {
  companion object {
    @JvmStatic
    @RegisterExtension
    val testing = AgentInstrumentationExtension.create()

    private fun assertCounter(name: String, value: Long) {
      testing.waitAndAssertMetrics(
        "io.opentelemetry.opentelemetry-instrumentation-annotations-1.16",
        name,
        { metrics ->
          // The test exporter uses delta temporality, so include every collection.
          metrics.satisfies({ actual ->
            assertThat(actual.flatMap { it.longSumData.points }.sumOf { it.value }).isEqualTo(value)
          })
        }
      )
    }
  }

  @Test
  fun `counts initial entry without counting resumptions`() = runBlocking {
    val first = CompletableDeferred<Unit>()
    val second = CompletableDeferred<Unit>()
    val resumed = CompletableDeferred<Unit>()
    val result = async(start = CoroutineStart.UNDISPATCHED) { suspending(first, second, resumed) }

    assertCounter("suspending.count", 1)
    first.complete(Unit)
    resumed.await()
    assertCounter("suspending.count", 1)
    second.complete(Unit)
    assertThat(result.await()).isEqualTo(42)
    assertCounter("suspending.count", 1)
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_9)
  fun `counts a suspend method in a closed module once`(@TempDir directory: Path) = runBlocking<Unit> {
    val packageName = "io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.closedmodule"
    val className = "$packageName.ClosedModuleCountedMethods"
    val classResource = className.replace('.', '/')
    val moduleName = "counted.fixture"
    val moduleInfo = ClassWriter(0)
    moduleInfo.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null)
    moduleInfo.visitModule(moduleName, 0, null).apply {
      visitRequire("java.base", Opcodes.ACC_MANDATED, null)
      visitExport(packageName.replace('.', '/'), 0)
      visitEnd()
    }
    moduleInfo.visitEnd()
    val moduleJar = directory.resolve("counted-fixture.jar")
    JarOutputStream(Files.newOutputStream(moduleJar)).use { jar ->
      jar.putNextEntry(JarEntry("module-info.class"))
      jar.write(moduleInfo.toByteArray())
      jar.closeEntry()
      for (resource in listOf("$classResource.class", "$classResource\$suspending\$1.class")) {
        jar.putNextEntry(JarEntry(resource))
        javaClass.classLoader.getResourceAsStream(resource)!!.use { it.copyTo(jar) }
        jar.closeEntry()
      }
    }

    val parent = ModuleLayer.boot()
    val configuration = parent.configuration().resolve(ModuleFinder.of(moduleJar), ModuleFinder.of(), setOf(moduleName))
    val controller = ModuleLayer.defineModulesWithOneLoader(configuration, listOf(parent), javaClass.classLoader)
    val layer = controller.layer()
    val fixtureModule = layer.findModule(moduleName).get()
    controller.addReads(fixtureModule, javaClass.module)
    val fixture = layer.findLoader(moduleName).loadClass(className)
    assertThat(fixture.module).isSameAs(fixtureModule)
    assertThat(fixtureModule.isOpen(packageName, javaClass.module)).isFalse()

    val instance = fixture.getConstructor().newInstance()
    val method = fixture.getMethod("suspending", CompletableDeferred::class.java, Continuation::class.java)
    val gate = CompletableDeferred<Unit>()
    val result = async(start = CoroutineStart.UNDISPATCHED) {
      suspendCoroutineUninterceptedOrReturn<Int> { continuation ->
        method.invoke(instance, gate, continuation)
      }
    }
    assertCounter("closed.module.count", 1)
    gate.complete(Unit)
    assertThat(result.await()).isEqualTo(42)
    assertCounter("closed.module.count", 1)
    assertThat(fixtureModule.isOpen(packageName, javaClass.module)).isFalse()
  }

  @Test
  fun `counts repeated calls that do not suspend`() = runBlocking {
    repeat(2) {
      assertThat(immediate(21L, 2.0)).isEqualTo(42L)
    }
    assertCounter("immediate.count", 2)
  }

  @Test
  fun `counts a tail call once`() = runBlocking {
    val gate = CompletableDeferred<Unit>()
    val result = async(start = CoroutineStart.UNDISPATCHED) { tailCall(gate) }
    assertCounter("tail.count", 1)

    gate.complete(Unit)
    result.await()
    assertCounter("tail.count", 1)
  }

  @Test
  fun `counts recursive calls separately from resumptions`() = runBlocking {
    val gate = CompletableDeferred<Unit>()
    val result = async(start = CoroutineStart.UNDISPATCHED) { recursive(2, gate) }
    assertCounter("recursive.count", 3)

    gate.complete(Unit)
    assertThat(result.await()).isEqualTo(3)
    assertCounter("recursive.count", 3)
  }

  @Test
  fun `counts nested calls alongside WithSpan`() = runBlocking {
    val gate = CompletableDeferred<Unit>()
    val result = async(start = CoroutineStart.UNDISPATCHED) { outer(gate) }
    assertCounter("outer.count", 1)
    assertCounter("inner.count", 1)

    gate.complete(Unit)
    assertThat(result.await()).isEqualTo(42)
    assertCounter("outer.count", 1)
    assertCounter("inner.count", 1)
    testing.waitAndAssertTraces({ trace ->
      trace.hasSpansSatisfyingExactly({ span -> span.hasName("CountedInstrumentationTest.inner") })
    })
  }

  @Test
  fun `counts a cancelled invocation once`() = runBlocking {
    val gate = CompletableDeferred<Unit>()
    val result = launch(start = CoroutineStart.UNDISPATCHED) { cancellable(gate) }
    assertCounter("cancelled.count", 1)

    result.cancelAndJoin()
    assertCounter("cancelled.count", 1)
  }

  @Counted("suspending.count")
  private suspend fun suspending(
    first: CompletableDeferred<Unit>,
    second: CompletableDeferred<Unit>,
    resumed: CompletableDeferred<Unit>
  ): Int {
    first.await()
    resumed.complete(Unit)
    second.await()
    return 42
  }

  @Counted("immediate.count")
  private suspend fun immediate(value: Long, multiplier: Double): Long = (value * multiplier).toLong()

  @Counted("tail.count")
  private suspend fun tailCall(gate: CompletableDeferred<Unit>) = gate.await()

  @Counted("recursive.count")
  private suspend fun recursive(depth: Int, gate: CompletableDeferred<Unit>): Int {
    if (depth == 0) {
      gate.await()
      return 1
    }
    return recursive(depth - 1, gate) + 1
  }

  @Counted("outer.count")
  private suspend fun outer(gate: CompletableDeferred<Unit>): Int = inner(gate) + 1

  @WithSpan
  @Counted("inner.count")
  private suspend fun inner(gate: CompletableDeferred<Unit>): Int {
    gate.await()
    return 41
  }

  @Counted("cancelled.count")
  private suspend fun cancellable(gate: CompletableDeferred<Unit>) {
    gate.await()
    error("should have been cancelled")
  }
}
