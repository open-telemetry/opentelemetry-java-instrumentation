plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  compileOnly(project(":javaagent-extension-api"))
  library("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation(project(":instrumentation:log4j:log4j-context-data:log4j-context-data-common:testing"))
}

tasks {
  test {
    filter {
      excludeTestsMatching("LibraryLog4j2BaggageTest")
    }
    systemProperty("otel.instrumentation.common.logging.keys.trace_id", "trace_id")
    systemProperty("otel.instrumentation.common.logging.keys.span_id", "span_id")
    systemProperty("otel.instrumentation.common.logging.keys.trace_flags", "trace_flags")
  }

  val testAddBaggage by registering(Test::class) {
    filter {
      includeTestsMatching("LibraryLog4j2BaggageTest")
    }
    jvmArgs("-Dotel.instrumentation.log4j-context-data.add-baggage=true")
    systemProperty("otel.instrumentation.common.logging.keys.trace_id", "trace_id")
    systemProperty("otel.instrumentation.common.logging.keys.span_id", "span_id")
    systemProperty("otel.instrumentation.common.logging.keys.trace_flags", "trace_flags")
  }

  named("check") {
    dependsOn(testAddBaggage)
  }
}
