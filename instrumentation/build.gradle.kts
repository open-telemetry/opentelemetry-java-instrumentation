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
