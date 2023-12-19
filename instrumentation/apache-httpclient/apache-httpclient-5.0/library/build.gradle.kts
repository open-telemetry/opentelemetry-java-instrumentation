plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.animalsniffer-conventions")
}

dependencies {
  library("org.apache.httpcomponents.client5:httpclient5:5.2.1")
  library("jakarta.annotation:jakarta.annotation-api:2.1.1")

  testImplementation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:testing"))

  latestDepTestLibrary("org.apache.httpcomponents.client5:httpclient5:5+") // see apache-httpclient-5.0 module
}

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=http")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
