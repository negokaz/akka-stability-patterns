package io.github.negokaz

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.StdIn
import io.github.negokaz.UserRegistryActor._
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat2(User)
  implicit val usersJsonFormat = seqFormat[User]

  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
}

object UserRegistryServiceApp extends App with JsonSupport {

  lazy val log = Logging(system, UserRegistryServiceApp.getClass)

  // set up ActorSystem and other dependencies here
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  // Needed for the Future and its methods flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher

  implicit val timeout = Timeout(1000.milliseconds)

  val userRegistryActor: ActorRef =
    system.actorOf(UserRegistrySupervisor.props, UserRegistrySupervisor.name)

  // from the UserRoutes trait
  lazy val routes: Route =
    pathPrefix("users") {
      concat(
        pathEnd {
          concat(
            // curl -X GET http://localhost:8080/users
            get {
              val users: Future[Seq[User]] =
                (userRegistryActor ? GetUsers).mapTo[Seq[User]]
              complete(users)
            },
            // curl -X POST -H 'Content-Type: application/json' -d '{"name": "hoge"}' http://localhost:8080/users
            post {
              entity(as[User]) { user =>
                val userCreated: Future[ActionPerformed] =
                  (userRegistryActor ? CreateUser(user)).mapTo[ActionPerformed]
                onSuccess(userCreated) { performed =>
                  log.info("Created user [{}]: {}", user.name, performed.description)
                  complete((StatusCodes.Created, performed))
                }
              }
            }
          )
        },
        path(Segment) { name =>
          concat(
            // curl -X GET http://localhost:8080/users/${name}
            get {
              val maybeUser: Future[Option[User]] =
                (userRegistryActor ? GetUser(name)).mapTo[Option[User]]
              rejectEmptyResponse {
                complete(maybeUser)
              }
            },
            // curl -X GET http://localhost:8080/users/${name}
            delete {
              val userDeleted: Future[ActionPerformed] =
                (system.deadLetters ? DeleteUser(name)).mapTo[ActionPerformed]
              onSuccess(userDeleted) { performed =>
                log.info("Deleted user [{}]: {}", name, performed.description)
                complete((StatusCodes.OK, performed))
              }
            }
          )
        }
      )
    }

  val serverBindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(routes, "localhost", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  serverBindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
