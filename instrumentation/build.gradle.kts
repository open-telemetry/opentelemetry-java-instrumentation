plugins {
  id("otel.java-conventions")
}

val instrumentationProjectTest = tasks.named("test")

subprojects {
  val subProj = this
  plugins.withId("java") {
    instrumentationProjectTest.configure {
      dependsOn(subProj.tasks.named("test"))
    }

    // this only exists to make Intellij happy since it doesn't (currently at least) understand our
    // inclusion of this artifact inside :testing-common
    dependencies {
      compileOnly(project(path = ":testing:armeria-shaded-for-testing", configuration = "shadow"))
      testCompileOnly(project(path = ":testing:armeria-shaded-for-testing", configuration = "shadow"))
    }
  }
}

tasks {
  register("listInstrumentations") {
    group = "Help"
    description = "List all available instrumentation modules"
    doFirst {
      subprojects
        .filter { it.plugins.hasPlugin("io.opentelemetry.instrumentation.muzzle-check") }
        .map { it.path }
        .forEach { println(it) }
    }
  }
}
