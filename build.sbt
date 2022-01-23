import sbtassembly.AssemblyPlugin.defaultUniversalScript

name := "bootes"

version := "0.1"

//scalaVersion := "3.0.1"
scalaVersion := "2.13.8"

organization := "bootes"

val zioVersion        = "1.0.13"
val zioPreludeVersion = "1.0.0-RC6"
val zioLoggingVersion = "0.5.12"
val zioHttpVersion    = "1.0.0.0-RC17"
val zioJsonVersion    = "0.2.0-M1"
val zioOpticsVersion  = "0.1.0"
val telemetry         = "0.8.1"
val openTracing       = "0.8.1"
val zioSchemaVersion  = "0.0.6"
val zQueryVersion     = "0.2.9"
val quillVersion      = "3.7.1"
val flywayVersion     = "7.9.1"
val zioConfigVersion  = "1.0.10"
val tapirVersion      = "0.18.0-M11"
val chimneyVersion    = "0.6.1"
val sttpVersion       = "3.3.15"
val jaeger            = "1.6.0"
val zipkin            = "2.16.3"
val zioInteropCats    = "2.5.1.0"
val opentelemetry     = "1.5.0"
val opencensus        = "0.28.3"
val opentracing       = "0.33.0"
val betterfiles       = "3.9.1"

scalacOptions += "-Ymacro-annotations"

lazy val opentracingLibs = Seq(
  "io.opentracing"          % "opentracing-api"         % opentracing,
  "io.opentracing"          % "opentracing-noop"        % opentracing,
  "io.opentracing"          % "opentracing-mock"        % opentracing % Test,
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.3.1"
)

lazy val opentelemetryLibs = Seq(
  "io.opentelemetry"        % "opentelemetry-api"         % opentelemetry,
  "io.opentelemetry"        % "opentelemetry-context"     % opentelemetry,
  "io.opentelemetry"        % "opentelemetry-sdk-testing" % opentelemetry % Test,
  "org.scala-lang.modules" %% "scala-collection-compat"   % "2.5.0"
)

lazy val opencensusLibs = Seq(
  "io.opencensus" % "opencensus-api"               % opencensus,
  "io.opencensus" % "opencensus-impl"              % opencensus,
  "io.opencensus" % "opencensus-contrib-http-util" % opencensus
)

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
  "dev.zio"                       %% "zio-opentelemetry"            % telemetry,
  "dev.zio"                       %% "zio-opentracing"          % openTracing,
  "dev.zio"                       %% "zio-streams"              % zioVersion,
  "dev.zio"                       %% "zio-kafka"                % "0.16.0",
  "dev.zio"                       %% "zio-zmx"                  % "0.0.8",
  "dev.zio"                       % "zio-json-interop-refined_2.13" % "0.2.0-M1",
  "io.d11"                        %% "zhttp"                    % zioHttpVersion,
  "io.getquill"                   %% "quill-jdbc-zio"           % quillVersion,
  "org.flywaydb"                   % "flyway-core"              % flywayVersion,
  "com.softwaremill.sttp.tapir"   %% "tapir-zio"                % tapirVersion,
  "io.scalaland"                  %% "chimney"                  % chimneyVersion,
  "com.softwaremill.sttp.client3" %% "httpclient-backend-zio"   % sttpVersion,
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "slf4j-backend"            % "3.3.13",
  "com.softwaremill.sttp.client3" %% "scribe-backend" 		      % "3.3.13",
  "com.softwaremill.sttp.client3" %% "zio-json"                 % "3.3.13",
  "com.github.jwt-scala"          %% "jwt-core"                 % "8.0.2",
  "org.postgresql"                 % "postgresql"               % "42.2.8",
  "ch.qos.logback"                 % "logback-classic"          % "1.2.3",
  "net.logstash.logback"           % "logstash-logback-encoder" % "6.5",
  //"com.github.mvv.zilog"          %% "zilog"                    % "0.1-M13",
  //"com.github.mvv.sredded"        %% "sredded-generic"          % "0.1-M2" % Provided,
  //"com.fullfacing"                %% "keycloak4s-core"          % "3.1.0",
  //"com.fullfacing"                %% "keycloak4s-admin"         % "3.1.0",
  //"com.fullfacing"                %% "keycloak4s-admin-monix"   % "3.1.0",
  //"com.fullfacing"                %% "keycloak4s-admin-monix-bio" % "3.1.0",
  //"com.fullfacing"                %% "keycloak4s-auth-akka-http"  % "3.1.0",
  //"org.keycloak"                  % "keycloak-admin-client"     % "14.0.0",
  "nl.vroste"                     %% "rezilience"               % "0.6.2",
  //"com.outr"                      %% "scribe"                   % "3.4.0",
  //"com.outr"                      %% "scribe-slf4j"             % "3.4.0",
  //"com.softwaremill.sttp.client3" %% "scribe-backend"           % "3.3.13",
  "commons-validator"             % "commons-validator"         % "1.7",
  "io.jaegertracing"              % "jaeger-core"               % jaeger,
  "io.jaegertracing"              % "jaeger-client"             % jaeger,
  "io.jaegertracing"              % "jaeger-zipkin"             % jaeger,
  "dev.zio"                      %% "zio-interop-cats"          % zioInteropCats,
  "io.zipkin.reporter2"           % "zipkin-reporter"           % zipkin,
  "io.zipkin.reporter2"           % "zipkin-sender-okhttp3"     % zipkin,
  "io.opentelemetry"              % "opentelemetry-exporter-jaeger" % opentelemetry,
  "io.opentelemetry"              % "opentelemetry-sdk" % opentelemetry,
  "io.grpc" % "grpc-netty-shaded" % "1.40.1",
  "io.grpc"                       % "grpc-netty-shaded"             % "1.40.1",
  "com.github.pathikrit"          %% "better-files"             % betterfiles, 
  "dev.doamaral"                  %% "zemail"                   % "0.1.0",
  "io.getquill" %% "quill-orientdb" % quillVersion,
  "dev.zio"                       %% "zio-test"                 % zioVersion % Test,
  "dev.zio"                       %% "zio-test-sbt"             % zioVersion % Test,
  "dev.zio"                       %% "zio-test-magnolia"        % zioVersion % Test
) ++ opentracingLibs ++ opentelemetryLibs ++ opencensusLibs


//enablePlugins(GraalVMNativeImagePlugin)
//enablePlugins(UniversalPlugin)
//graalVMNativeImageGraalVersion := Some("20.0.0-java11")



testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

lazy val app = (project in file("."))
  .settings(
    mainClass in (Compile, packageBin) := Some("com.bootes.server.UserServer"),
    assembly / mainClass := Some("com.bootes.server.UserServer"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar"
)

ThisBuild / assemblyPrependShellScript := Some(defaultUniversalScript(shebang = false))

ThisBuild / assemblyMergeStrategy := {
  case PathList("io", "netty", xs @ _*)         => MergeStrategy.first
  case PathList("com", "fasterxml", xs @ _*)         => MergeStrategy.first
  case PathList("org", "reactivestreams", xs @ _*)         => MergeStrategy.first
  case PathList("ch", "qos", xs @ _*)         => MergeStrategy.first
  case PathList("com", "outr", xs @ _*)         => MergeStrategy.first
  case PathList("javax", "annotation", xs @ _*)         => MergeStrategy.first
  //case PathList("org", "apache", "tomcat-annotations-api", xs @ _*)         => MergeStrategy.first
  //case PathList("javax", "activation", "activation", xs @ _*)         => MergeStrategy.discard
  case PathList("com", "sun", "activation", xs @ _*)         => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "io.netty.versions.properties" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "native-image.properties" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "reflection-config.json" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "module-info.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "StaticLoggerBinder.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "StaticMDCBinder.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "ActivationDataFlavor.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "CommandInfo.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "CommandMap.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "CommandObject.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "DataContentHandler.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "DataHandlerDataSource.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "DataSource.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "DataSourceDataContentHandler.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "FileDataSource.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "FileTypeMap.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "MailcapCommandMap.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "MimeTypeParameterList.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "MimeTypeParseException.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "MimetypesFileTypeMap.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "SecuritySupport$3.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "SecuritySupport$4.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "SecuritySupport$5.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "SecuritySupport.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "URLDataSource.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "UnsupportedDataTypeException.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "ObjectDataContentHandler.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "UnsupportedDataTypeException.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "LineTokenizer.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "DataHandlerDataSource.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "FileDataSource.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "FileTypeMap.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "MailcapCommandMap.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "MimeTypeParseException.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "SecuritySupport$1.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "SecuritySupport$2.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "DataContentHandlerFactory.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "DataHandler$1.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "DataHandler.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith "MimeType.class" => MergeStrategy.last
  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
  case "application.conf"                            => MergeStrategy.concat
  case "unwanted.txt"                                => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
