plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.shenyu")
    module.set("shenyu")
    versions.set("[2.4.3,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.springframework:spring-webflux:5.0.0.RELEASE")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.shenyu.experimental-span-attributes=true")
}
