import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-3.0:testing")) {
    exclude(group = "org.apache.tomcat.embed", module = "tomcat-embed-core")
    exclude(group = "org.apache.tomcat.embed", module = "tomcat-embed-jasper")
  }
  testImplementation(project(":instrumentation:servlet:servlet-common:bootstrap"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:7.0.109")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:7.0.109")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-logging-juli:7.0.109")
}

testing.suites {
  executions("test") {
    configureEach {
      jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    }
  }
}
