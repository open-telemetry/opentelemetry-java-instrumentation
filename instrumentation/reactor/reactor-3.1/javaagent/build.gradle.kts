plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    name.set("Reactor 3.1 instrumentation")
    group.set("io.projectreactor")
    module.set("reactor-core")
    versions.set("[3.1.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    excludeInstrumentationName("opentelemetry-api")
    excludeInstrumentationName("reactor-3.4-context-propagation-operator")
  }
  pass {
    name.set("Reactor 3.4 ContextView instrumentation")
    group.set("io.projectreactor")
    module.set("reactor-core")
    versions.set("[3.4.0,)")
    assertInverse.set(true)
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    excludeInstrumentationName("opentelemetry-api")
    excludeInstrumentationName("reactor-3.1-core")
    excludeInstrumentationName("reactor-3.1-context-propagation-operator")
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.reactor.experimental-span-attributes=true")
}

dependencies {
  // we compile against 3.4.0, so we could use reactor.util.context.ContextView
  // instrumentation is tested against 3.1.0.RELEASE
  compileOnly("io.projectreactor:reactor-core:3.4.0")
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))

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
    register<JvmTestSuite>("testInitialization") {
      dependencies {
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
        implementation(project(":instrumentation-annotations"))
        val version = baseVersion("3.1.0.RELEASE").orLatest()
        implementation("io.projectreactor:reactor-test:$version")
      }
    }
    register<JvmTestSuite>("version34Test") {
      dependencies {
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
        implementation("io.projectreactor:reactor-core:${baseVersion("3.4.0").orLatest()}")
      }
    }
  }
}

tasks {

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=code")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=code/dup")
  }

  check {
    dependsOn(testing.suites, testStableSemconv, testBothSemconv)
  }
}
