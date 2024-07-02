plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tomcat.embed")
    module.set("tomcat-embed-core")
    versions.set("[10,)")
  }
}

dependencies {
  implementation(project(":instrumentation:tomcat:tomcat-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")

  latestDepTestLibrary("org.apache.tomcat:jakartaee-migration:+")

  // Make sure nothing breaks due to both 7.0 and 10.0 modules being present together
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
  // testing whether instrumentation still works when javax servlet api is also present
  testImplementation("javax.servlet:javax.servlet-api:3.0.1")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}

// Tomcat 10 uses deprecation annotation methods `forRemoval()` and `since()`
// in jakarta.servlet.http.HttpServlet that don't work with Java 8
if (findProperty("testLatestDeps") as Boolean) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
