plugins {
  id("otel.java-conventions")
}

val instrumentationTest = tasks.named("test")
val instrumentationDeps = dependencies

subprojects {
  val subProj = this

  plugins.withId("java") {
    // Make it so all instrumentation subproject tests can be run with a single command.
    instrumentationTest.configure {
      dependsOn(subProj.tasks.named("test"))
    }
  }
}