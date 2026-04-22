val stableVersion = "2.28.0-SNAPSHOT"
val alphaVersion = "2.28.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
