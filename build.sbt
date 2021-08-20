name := "bootes"

version := "0.1"

//scalaVersion := "3.0.1"
scalaVersion := "2.13.6"

organization := "bootes"

val zioVersion        = "1.0.10"
val zioPreludeVersion = "1.0.0-RC5"
val zioLoggingVersion = "0.5.8"
val zioHttpVersion    = "1.0.0.0-RC17"
val zioJsonVersion    = "0.2.0-M1"
val zioOpticsVersion  = "0.1.0"
val zioOpenTracingVersion = "0.8.1"
val zioSchemaVersion  = "0.0.5"
val zQueryVersion     = "0.2.9"
val quillVersion      = "3.7.1"
val flywayVersion     = "7.9.1"
val zioConfigVersion  = "1.0.5"
val tapirVersion      = "0.18.0-M11"
val chimneyVersion    = "0.6.1"
val sttpVersion       = "3.3.9"

scalacOptions += "-Ymacro-annotations"

libraryDependencies ++= Seq(
  "dev.zio"                       %% "zio"                      % zioVersion,
  "dev.zio"                       %% "zio-macros"               % zioVersion,
  "dev.zio"                       %% "zio-prelude"              % zioPreludeVersion,
  "dev.zio"                       %% "zio-json"                 % zioJsonVersion,
  "dev.zio"                       %% "zio-logging-slf4j"        % zioLoggingVersion,
  "dev.zio"                       %% "zio-config"               % zioConfigVersion,
  "dev.zio"                       %% "zio-config-magnolia"      % zioConfigVersion,
  "dev.zio"                       %% "zio-config-typesafe"      % zioConfigVersion,
  "dev.zio"                       %% "zio-query"                % zQueryVersion,
  "dev.zio"                       %% "zio-schema"               % zioSchemaVersion,
  "io.github.kitlangton"          %% "zio-magic"                % "0.3.2",
  "dev.zio"                       %% "zio-optics"               % zioOpticsVersion,
  "dev.zio"                       %% "zio-opentracing"          % zioOpenTracingVersion,
  "dev.zio"                       %% "zio-streams"              % zioVersion,
  "dev.zio"                       %% "zio-kafka"                % "0.16.0",
  "dev.zio"                       % "zio-json-interop-refined_2.13" % "0.2.0-M1",
  "io.d11"                        %% "zhttp"                    % zioHttpVersion,
  "io.getquill"                   %% "quill-jdbc-zio"           % quillVersion,
  "org.flywaydb"                   % "flyway-core"              % flywayVersion,
  "com.softwaremill.sttp.tapir"   %% "tapir-zio"                % tapirVersion,
  "io.scalaland"                  %% "chimney"                  % chimneyVersion,
  "com.softwaremill.sttp.client3" %% "httpclient-backend-zio"   % sttpVersion,
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion,
  "com.github.jwt-scala"          %% "jwt-core"                 % "8.0.2",
  "org.postgresql"                 % "postgresql"               % "42.2.8",
  "ch.qos.logback"                 % "logback-classic"          % "1.2.3",
  "net.logstash.logback"           % "logstash-logback-encoder" % "6.5",
  "com.github.mvv.zilog"          %% "zilog"                    % "0.1-M13",
  "com.github.mvv.sredded"        %% "sredded-generic"          % "0.1-M2" % Provided,
  //"com.fullfacing"                %% "keycloak4s-core"          % "3.1.0",
  //"com.fullfacing"                %% "keycloak4s-admin"         % "3.1.0",
  //"com.fullfacing"                %% "keycloak4s-admin-monix"   % "3.1.0",
  //"com.fullfacing"                %% "keycloak4s-admin-monix-bio" % "3.1.0",
  //"com.fullfacing"                %% "keycloak4s-auth-akka-http"  % "3.1.0",
  //"org.keycloak"                  % "keycloak-admin-client"     % "14.0.0",
  "nl.vroste"                     %% "rezilience"               % "0.6.2",
  "com.outr"                      %% "scribe"                   % "3.4.0",
  "com.outr"                      %% "scribe-slf4j"             % "3.4.0",
  "com.softwaremill.sttp.client3" %% "scribe-backend"           % "3.3.13",
  "commons-validator"             % "commons-validator"         % "1.7",
  "dev.zio"                       %% "zio-test"                 % zioVersion % Test,
  "dev.zio"                       %% "zio-test-sbt"             % zioVersion % Test,
  "dev.zio"                       %% "zio-test-magnolia"        % zioVersion % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

lazy val app = (project in file("."))
  .settings(
    assembly / mainClass := Some("com.bootes.server.UserServer")
)