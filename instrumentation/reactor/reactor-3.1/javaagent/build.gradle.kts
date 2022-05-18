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
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.reactor.experimental-span-attributes=true")
}

dependencies {
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))
  library("io.projectreactor:reactor-core:3.1.0.RELEASE")

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

  compileOnly(project(":javaagent-tooling"))
  compileOnly(project(":instrumentation-api-annotation-support"))
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")
  testImplementation(project(":instrumentation:opentelemetry-annotations-1.0:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:testing"))
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")

  // Looks like later versions on reactor need this dependency for some reason even though it is marked as optional.
  latestDepTestLibrary("io.micrometer:micrometer-core:1.+")
}

testing {
  suites {
    val testInitialization by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
        implementation("io.opentelemetry:opentelemetry-extension-annotations")
        implementation("io.projectreactor:reactor-test:3.1.0.RELEASE")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
