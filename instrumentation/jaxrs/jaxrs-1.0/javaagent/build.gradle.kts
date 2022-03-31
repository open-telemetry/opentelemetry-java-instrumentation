plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.ws.rs")
    module.set("jsr311-api")
    versions.set("[0.5,)")
  }
  fail {
    group.set("javax.ws.rs")
    module.set("javax.ws.rs-api")
    versions.set("[,]")
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("javax.ws.rs:jsr311-api:1.1.1")

  // Jackson, used by the test, dropped support for jax 1.x in 2.13+
  testImplementation(enforcedPlatform("com.fasterxml.jackson:jackson-bom:2.12.6"))
  testImplementation("io.dropwizard:dropwizard-testing:0.7.1")
  testImplementation("javax.xml.bind:jaxb-api:2.2.3")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.jaxrs.enabled=true")
}
