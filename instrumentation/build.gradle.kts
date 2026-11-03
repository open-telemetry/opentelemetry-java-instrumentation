plugins {
  id("otel.java-conventions")
}

val instrumentationProjectTest = tasks.named("test")

val muzzleSlices = providers.gradleProperty("muzzleSlices")
  .map { it.toIntOrNull() ?: 0 }
  .getOrElse(4)
require(muzzleSlices == 4 || muzzleSlices == 8) { "muzzleSlices must be 4 or 8" }
val instrumentationProjectMuzzle = (1..muzzleSlices).map { tasks.register("muzzle$it") }

var counter = 0
subprojects {
  val subProj = this
  plugins.withId("java") {
    instrumentationProjectTest.configure {
      dependsOn(subProj.tasks.named("test"))
    }
  }

  plugins.withId("io.opentelemetry.instrumentation.muzzle-check") {
    // relying on predictable ordering of subprojects
    // (see https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#N14CB4)
    // since we are splitting these muzzleX tasks across different github action jobs
    instrumentationProjectMuzzle[counter++ % muzzleSlices].configure {
      dependsOn(subProj.tasks.named("muzzle"))
    }
  }
}
