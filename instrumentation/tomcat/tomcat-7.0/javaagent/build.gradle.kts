plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tomcat.embed")
    module.set("tomcat-embed-core")
    // Tomcat 10 is about servlet 5.0
    // 7.0.4 added Request.isAsync, which is needed
    versions.set("[7.0.4, 10)")
  }
}

dependencies {
  implementation(project(":instrumentation:tomcat:tomcat-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("org.apache.tomcat.embed:tomcat-embed-core:7.0.4")

  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  // Make sure nothing breaks due to both 7.0 and 10.0 modules being present together
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))
  // testing whether instrumentation still works when jakarta servlet api is also present
  testImplementation("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:8.0.41")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:8.0.41")

  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-core:9.+") // see tomcat-10.0 module
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:9.+") // see tomcat-10.0 module
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}
