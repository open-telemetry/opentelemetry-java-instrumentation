plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:hbase:hbase-client-common-1.0:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation("io.opentelemetry:opentelemetry-api")
  testImplementation("org.apache.hbase:hbase-client:1.0.0")
}

testing {
  suites {
    register<JvmTestSuite>("hbase14Test") {
      dependencies {
        implementation(project(":instrumentation-api-incubator"))
        implementation(project(":instrumentation:hbase:hbase-client-common-1.0:javaagent"))
        implementation("org.apache.hbase:hbase-client:${baseVersion("1.4.0").orLatest("1.+")}")
      }
    }

    register<JvmTestSuite>("hbase20Test") {
      dependencies {
        implementation(project(":instrumentation-api-incubator"))
        implementation(project(":instrumentation:hbase:hbase-client-common-1.0:javaagent"))
        implementation("org.apache.hbase:hbase-client:${baseVersion("2.0.0").orLatest("2.2.+")}")
      }
    }

    register<JvmTestSuite>("hbase24Test") {
      dependencies {
        implementation(project(":instrumentation-api-incubator"))
        implementation(project(":instrumentation:hbase:hbase-client-common-1.0:javaagent"))
        implementation("org.apache.hbase:hbase-client:${baseVersion("2.4.18").orLatest("2.4.+")}")
      }
    }
  }
}

sourceSets.named("hbase14Test") {
  resources.srcDir("src/test/resources")
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
