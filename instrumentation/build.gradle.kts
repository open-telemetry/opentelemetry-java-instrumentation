plugins {
  id("otel.java-conventions")
}

val instrumentationProjectTest = tasks.named("test")

val muzzleTasks = mutableListOf<Task>()

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

  plugins.withId("io.opentelemetry.instrumentation.muzzle-check") {
    muzzleTasks.add(subProj.tasks.named("muzzle").get())
  }
}

// TODO (trask) how to know that muzzleTasks populated above before used below?

// batching up the muzzle tasks alphabetically into 4 chunks
// to split them up into separate CI jobs (but not too many CI job)
val instrumentationProjectMuzzle1 = tasks.register("muzzle1")
instrumentationProjectMuzzle1.configure {
  dependsOn(muzzleTasks.sortedWith(compareBy { it.path }).subList(0, muzzleTasks.size / 4))
}
val instrumentationProjectMuzzle2 = tasks.register("muzzle2")
instrumentationProjectMuzzle2.configure {
  dependsOn(muzzleTasks.sortedWith(compareBy { it.path }).subList(muzzleTasks.size / 4, muzzleTasks.size / 2))
}
val instrumentationProjectMuzzle3 = tasks.register("muzzle3")
instrumentationProjectMuzzle3.configure {
  dependsOn(muzzleTasks.sortedWith(compareBy { it.path }).subList(muzzleTasks.size / 2, 3 * muzzleTasks.size / 4))
}
val instrumentationProjectMuzzle4 = tasks.register("muzzle4")
instrumentationProjectMuzzle4.configure {
  dependsOn(muzzleTasks.sortedWith(compareBy { it.path }).subList(3 * muzzleTasks.size / 4, muzzleTasks.size))
}
