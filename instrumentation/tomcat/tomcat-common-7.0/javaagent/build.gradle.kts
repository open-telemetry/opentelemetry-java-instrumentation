plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
  compileOnly(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("org.apache.tomcat.embed:tomcat-embed-core:7.0.4")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation("org.apache.tomcat.embed:tomcat-embed-core:7.0.4")
        runtimeOnly("org.apache.tomcat:tomcat-juli:7.0.4")
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
