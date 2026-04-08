plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
  implementation(project(":instrumentation:servlet:servlet-common:library"))

  testImplementation(project(":instrumentation:servlet:servlet-5.0:testing"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:10.0.0")
}
