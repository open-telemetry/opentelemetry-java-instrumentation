plugins {
  id("otel.javaagent-instrumentation")
}

tasks.withType<JavaCompile> {
  options.compilerArgs.addAll(listOf("-Xlint:-processing", "-Xlint:-classfile"))
}

muzzle {
  // === SERVER MUZZLE CONFIGURATION ===
  pass {
    group.set("io.vertx")
    module.set("vertx-web")
    versions.set("[3.9.0,4.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[3.9.0,4.0.0)")
    assertInverse.set(true)
  }
  // === RESTEASY MUZZLE CONFIGURATION ===
  pass {
    group.set("org.jboss.resteasy")
    module.set("resteasy-vertx")
    versions.set("[3.0.0,4.0.0)")
    assertInverse.set(true)
  }

  // === DREAM11 CUSTOM CLASSES MUZZLE CONFIGURATION ===
  // Note: These are custom classes, so we use assertInverse=false
  pass {
    group.set("com.dream11")
    module.set("rest")
    versions.set("[1.0.0,)")
    assertInverse.set(false)
  }

  // === CLIENT MUZZLE CONFIGURATION ===
  pass {
    group.set("io.vertx")
    module.set("vertx-sql-client")
    versions.set("[3.9.0,4.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[3.9.0,4.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.vertx")
    module.set("vertx-web-client")
    versions.set("[3.9.0,4.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("io.vertx")
    module.set("vertx-cassandra-client")
    versions.set("[3.9.0,4.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  // === SERVER DEPENDENCIES ===
  // Vertx Web Framework (RESTEasy foundation)
  compileOnly("io.vertx:vertx-web:3.9.2")

  // RESTEasy Vertx Integration
  compileOnly("org.jboss.resteasy:resteasy-vertx:3.15.0.Final")

  // Vertx Reactivex (for reactive extensions)
  compileOnly("io.vertx:vertx-rx-java2:3.9.2") {
    exclude(group = "io.vertx", module = "vertx-docgen")
  }

  // Vertx Core (HTTP Server, Context Management)
  compileOnly("io.vertx:vertx-core:3.9.2")
  compileOnly("io.vertx:vertx-codegen:3.9.2")

  // === CLIENT DEPENDENCIES ===
  // SQL Client
  compileOnly("io.vertx:vertx-sql-client:3.9.2")

  // Redis Client
  compileOnly("io.vertx:vertx-redis-client:3.9.2")

  // Web Client
  compileOnly("io.vertx:vertx-web-client:3.9.2")

  // Cassandra Client
  compileOnly("io.vertx:vertx-cassandra-client:3.9.2")
}
