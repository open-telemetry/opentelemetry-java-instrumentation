val stableVersion = "2.18.1"
val alphaVersion = "2.18.1-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
