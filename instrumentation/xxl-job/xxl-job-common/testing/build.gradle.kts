plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation(project(":testing-common"))

  compileOnly("com.xuxueli:xxl-job-core:2.1.2") {
    exclude("org.codehaus.groovy", "groovy")
  }
}
