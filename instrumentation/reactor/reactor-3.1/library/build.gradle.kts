plugins {
  id("otel.library-instrumentation")
}

dependencies {
  // we compile against 3.4.0, so we could use reactor.util.context.ContextView
  // instrumentation is expected it to work with 3.1.0.RELEASE
  compileOnly("io.projectreactor:reactor-core:3.4.0")
  compileOnly(project(":muzzle")) // For @NoMuzzle
  implementation(project(":instrumentation-annotations-support"))
  testLibrary("io.projectreactor:reactor-core:3.1.0.RELEASE")
  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")

  testImplementation(project(":instrumentation:reactor:reactor-3.1:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}
