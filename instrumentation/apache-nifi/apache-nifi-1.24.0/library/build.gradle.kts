plugins {
  id("otel.library-instrumentation")
}

dependencies {

  library("org.apache.nifi:nifi-bin-manager:1.24.0")
  library("org.apache.nifi:nifi-framework-core:1.24.0")
  library("org.apache.nifi:nifi-standard-processors:1.24.0")
  library("javax.servlet:javax.servlet-api:3.1.0")
  library("com.squareup.okhttp3:okhttp:4.10.0")

  compileOnly(project(":muzzle"))

  testImplementation(project(":instrumentation:apache-nifi:apache-nifi-1.24.0:testing"))
}

tasks.withType<JavaCompile>().configureEach {
  with(options) {
    release.set(17)
  }
}