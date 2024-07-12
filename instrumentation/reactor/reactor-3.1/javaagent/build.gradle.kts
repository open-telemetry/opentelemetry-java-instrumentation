plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor")
    module.set("reactor-core")
    versions.set("[3.1.0.RELEASE,)")
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    assertInverse.set(true)
    excludeInstrumentationName("opentelemetry-api")
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.reactor.experimental-span-attributes=true")
}

dependencies {
  // we compile against 3.4.0, so we could use reactor.util.context.ContextView
  // instrumentation is tested against 3.1.0.RELEASE
  compileOnly("io.projectreactor:reactor-core:3.4.0")
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  compileOnly(project(":javaagent-tooling"))
  compileOnly(project(":instrumentation-annotations-support"))
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  testInstrumentation(project(":instrumentation:opentelemetry-extension-annotations-1.0:javaagent"))

  testLibrary("io.projectreactor:reactor-core:3.1.0.RELEASE")
  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")
  testImplementation(project(":instrumentation-annotations-support-testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:testing"))
  testImplementation(project(":instrumentation-annotations"))
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
}

testing {
  suites {
    val testInitialization by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
        implementation(project(":instrumentation-annotations"))
        if (findProperty("testLatestDeps") as Boolean) {
          implementation("io.projectreactor:reactor-test:+")
        } else {
          implementation("io.projectreactor:reactor-test:3.1.0.RELEASE")
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
