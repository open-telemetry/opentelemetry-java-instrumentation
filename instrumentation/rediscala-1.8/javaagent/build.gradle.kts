plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.github.etaty")
    module.set("rediscala_2.11")
    versions.set("[1.5.0,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.etaty")
    module.set("rediscala_2.12")
    versions.set("[1.8.0,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.etaty")
    module.set("rediscala_2.13")
    versions.set("[1.9.0,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.Ma27")
    module.set("rediscala_2.11")
    versions.set("[1.8.1,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.Ma27")
    module.set("rediscala_2.12")
    versions.set("[1.8.1,)")
    assertInverse.set(true)
  }

  pass {
    group.set("com.github.Ma27")
    module.set("rediscala_2.13")
    versions.set("[1.9.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.github.etaty:rediscala_2.11:1.8.0")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
