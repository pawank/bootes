resolvers += Resolver.jcenterRepo
resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.0.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
//addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.9.6")

