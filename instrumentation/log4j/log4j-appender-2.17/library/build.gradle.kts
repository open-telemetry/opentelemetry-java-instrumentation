plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.17.0")

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

  if (findProperty("testLatestDeps") as Boolean) {
    testCompileOnly("biz.aQute.bnd:biz.aQute.bnd.annotation:7.0.0")
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
