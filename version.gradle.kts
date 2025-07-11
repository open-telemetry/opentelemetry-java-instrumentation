val stableVersion = "2.18.0-SNAPSHOT"
val alphaVersion = "2.18.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
