plugins {
  id("otel.javaagent-instrumentation")
}
muzzle {
  pass {
    group.set("javax.servlet")
    module.set("javax.servlet-api")
    versions.set("[3.0,)")
    assertInverse.set(true)
  }
  fail {
    group.set("javax.servlet")
    module.set("servlet-api")
    versions.set("(,)")
  }
}

dependencies {
  api(project(":instrumentation:servlet:servlet-common:javaagent"))
  api(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("javax.servlet:javax.servlet-api:3.0.1")

  testInstrumentation(project(":instrumentation:jetty:jetty-8.0:javaagent"))

  testLibrary("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  testLibrary("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:8.0.41")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:8.0.41")

  latestDepTestLibrary("org.eclipse.jetty:jetty-server:10.+") // see servlet-5.0 module
  latestDepTestLibrary("org.eclipse.jetty:jetty-servlet:10.+") // see servlet-5.0 module

  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-core:9.+") // see servlet-5.0 module
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:9.+") // see servlet-5.0 module
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-parameters=test-parameter")
}
