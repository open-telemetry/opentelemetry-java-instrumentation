plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("org.mockito:mockito-core")
  api("org.mockito:mockito-junit-jupiter")

  compileOnly("org.apache.nifi:nifi-framework-core:1.24.0")
}

tasks.withType<JavaCompile>().configureEach {
  with(options) {
    release.set(17)
  }
}