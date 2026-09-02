plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:hbase:hbase-client-common-1.0:javaagent"))
  testImplementation("org.apache.hbase:hbase-client:1.0.0")
}

testing {
  suites {
    register<JvmTestSuite>("modernHbaseTest") {
      dependencies {
        implementation(project(":instrumentation-api-incubator"))
        implementation(project(":instrumentation:hbase:hbase-client-common-1.0:javaagent"))
        implementation("org.apache.hbase:hbase-client:2.4.18")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
