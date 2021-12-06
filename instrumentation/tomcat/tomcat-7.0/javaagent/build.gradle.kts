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
  compileOnly("org.apache.tomcat.embed:tomcat-embed-core:7.0.4")
  implementation(project(":instrumentation:tomcat:tomcat-common:javaagent"))
  implementation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  // Make sure nothing breaks due to both 7.0 and 10.0 modules being present together
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:8.0.41")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:8.0.41")
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-core:[9.+, 10)")
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:[9.+, 10)")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
}
