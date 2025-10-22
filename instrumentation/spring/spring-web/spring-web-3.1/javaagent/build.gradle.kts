plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-web")
    versions.set("[3.1.0.RELEASE,6)")
    // these versions depend on javax.faces:jsf-api:1.1 which was released as pom only
    skip("1.2.1", "1.2.2", "1.2.3", "1.2.4")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework:spring-web:3.1.0.RELEASE")

  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))

  latestDepTestLibrary("org.springframework:spring-web:5.+") // see spring-web-6.0 module
}

tasks.withType<Test>().configureEach {
}
