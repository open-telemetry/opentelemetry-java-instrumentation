plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("javax.servlet:javax.servlet-api:3.0.1")

  // FIXME: These dependencies need to be shadowed into the library.
  library(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  library(project(":instrumentation:servlet:servlet-common:javaagent"))
  library(project(":instrumentation:servlet:servlet-common:bootstrap"))
  library(project(":javaagent-extension-api"))

//  testImplementation(project(":testing:agent-exporter"))

  testLibrary("org.eclipse.jetty:jetty-server:8.0.0.v20110901")
  testLibrary("org.eclipse.jetty:jetty-servlet:8.0.0.v20110901")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:8.0.41")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:8.0.41")
}


tasks {
  withType<Test>().configureEach {
    // required on jdk17 to allow tomcat to shutdown properly.
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }
}
