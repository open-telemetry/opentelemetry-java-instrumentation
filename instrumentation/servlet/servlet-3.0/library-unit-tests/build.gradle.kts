plugins {
  id("otel.library-instrumentation")
}

// These tests are split out so we can unit test against the actual shadowed jar.

dependencies {
  testImplementation(project(":instrumentation:servlet:servlet-3.0:library", configuration = "shadow"))
  testImplementation(project(":instrumentation:servlet:servlet-3.0:testing"))

  testLibrary("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  testLibrary("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:8.0.41")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:8.0.41")

  latestDepTestLibrary("org.eclipse.jetty:jetty-server:10.+") // see servlet-5.0 module
  latestDepTestLibrary("org.eclipse.jetty:jetty-servlet:10.+") // see servlet-5.0 module

  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-core:9.+") // see servlet-5.0 module
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:9.+") // see servlet-5.0 module
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17+ to allow tomcat to shutdown properly.
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }
}

// Servlet 3.0 in latest Jetty versions requires Java 11
// However, projects that depend on this module are still be using Java 8 in testLatestDeps mode
// Therefore, we need a separate project for servlet 3.0 tests
val latestDepTest = findProperty("testLatestDeps") as Boolean

if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
