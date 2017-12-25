lazy val akkaHttpVersion = "10.0.10"
lazy val akkaVersion    = "2.5.4"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "io.github.negokaz",
      scalaVersion    := "2.12.3"
    )),
    name := "akka-stability-patterns",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"   %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka"   %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka"   %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka"   %% "akka-stream"          % akkaVersion,
      "com.typesafe.akka"   %% "akka-slf4j"           % akkaVersion,
      "ch.qos.logback"      %  "logback-classic"      % "1.2.3",
      "com.typesafe.slick"  %% "slick"                % "3.2.1",
      "com.github.takezoe"  %% "blocking-slick-32"    % "0.0.10",
      "com.h2database"      %  "h2"                   % "1.4.196",

      "com.typesafe.akka"   %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka"   %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka"   %% "akka-stream-testkit"  % akkaVersion     % Test,
      "org.scalatest"       %% "scalatest"            % "3.0.1"         % Test
    ),
    h2Server := {
      System.setProperty("h2.bindAddress", "localhost")
      val server = org.h2.tools.Server.createTcpServer().start()
      streams.value.log.info(s"Running h2 server: ${server.getURL}")
      server.run()
    },
    fullRunTask(futureTimeout, Compile, "io.github.negokaz.FutureTimeoutApp"),
    fullRunTask(userQueryService, Compile, "io.github.negokaz.UserQueryServiceApp"),
    fullRunTask(userRegistryService, Compile, "io.github.negokaz.UserRegistryServiceApp"),
    fullRunTask(slowUserRegistryService, Compile, "io.github.negokaz.UserRegistryServiceApp", "high-load")
  )

lazy val h2Server = TaskKey[Unit]("h2-server", "start h2 server")
lazy val futureTimeout = TaskKey[Unit]("future-timeout", "run Future Timeout sample")
lazy val userQueryService = TaskKey[Unit]("user-query-service", "start User Query Service")
lazy val userRegistryService = TaskKey[Unit]("user-registry-service", "start User Registry Service")
lazy val slowUserRegistryService = TaskKey[Unit]("slow-user-registry-service", "start User Registry Service")