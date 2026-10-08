plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":instrumentation:couchbase:couchbase-common-3.1:javaagent"))
  testImplementation("com.couchbase.client:java-client:3.1.4")
}

testing {
  suites {
    register<JvmTestSuite>("protostellarTest") {
      dependencies {
        implementation(project(":instrumentation-api-incubator"))
        implementation(project(":javaagent-extension-api"))
        implementation(project(":instrumentation:couchbase:couchbase-common-3.1:javaagent"))
        implementation("com.couchbase.client:java-client:3.4.3")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
