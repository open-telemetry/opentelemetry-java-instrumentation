plugins {
  id("otel.java-conventions")
}

val instrumentationProjectTest = tasks.named("test")

// batching up the muzzle tasks alphabetically into 4 chunks
// to split them up into separate CI jobs (but not too many CI job)
val instrumentationProjectMuzzle = listOf(
  tasks.create("muzzle1"),
  tasks.create("muzzle2"),
  tasks.create("muzzle3"),
  tasks.create("muzzle4")
)

var counter = 0
subprojects {
  val subProj = this
  plugins.withId("java") {
    instrumentationProjectTest.configure {
      dependsOn(subProj.tasks.named("test"))
    }

    // this only exists to make Intellij happy since it doesn't (currently at least) understand our
    // inclusion of this artifact inside :testing-common
    dependencies {
      compileOnly(project(":testing:armeria-shaded-for-testing", configuration = "shadow"))
      testCompileOnly(project(":testing:armeria-shaded-for-testing", configuration = "shadow"))
    }
  }

  plugins.withId("io.opentelemetry.instrumentation.muzzle-check") {
    // relying on predictable ordering of subprojects
    // (see https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#N14CB4)
    // since we are splitting these muzzleX tasks across different github action jobs
    instrumentationProjectMuzzle[counter++ % 4].dependsOn(subProj.tasks.named("muzzle"))
  }
}
