plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("javax.servlet:javax.servlet-api:3.0.1")
  implementation(project(":instrumentation:servlet:servlet-common:library"))
  api(project(":instrumentation:servlet:servlet-javax-common:library"))

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
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }
}
