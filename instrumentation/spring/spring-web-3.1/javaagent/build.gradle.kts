plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-web")
    versions.set("[3.1.0.RELEASE,]")
    // these versions depend on javax.faces:jsf-api:1.1 which was released as pom only
    skip("1.2.1", "1.2.2", "1.2.3", "1.2.4")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.springframework:spring-web:3.1.0.RELEASE")
}
