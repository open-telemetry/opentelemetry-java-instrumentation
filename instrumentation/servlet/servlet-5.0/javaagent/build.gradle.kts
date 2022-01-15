plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("jakarta.servlet")
    module.set("jakarta.servlet-api")
    versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testLibrary("org.eclipse.jetty:jetty-server:11.0.0")
  testLibrary("org.eclipse.jetty:jetty-servlet:11.0.0")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:10.0.0")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
}
