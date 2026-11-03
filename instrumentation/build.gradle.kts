plugins {
  id("otel.java-conventions")
}

val instrumentationProjectTest = tasks.named("test")

val instrumentationProjectMuzzle = (1..8).map { tasks.register("muzzle$it") }

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
    instrumentationProjectMuzzle[counter++ % instrumentationProjectMuzzle.size].configure {
      dependsOn(subProj.tasks.named("muzzle"))
    }
  }
}
