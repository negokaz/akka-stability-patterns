package io.github.negokaz

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{ Failure, Success, Try }

object UserQueryServiceApp extends App {

  lazy val log = Logging(system, UserQueryServiceApp.getClass)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  import akka.pattern.CircuitBreaker

  // サーキットブレーカーを作成
  lazy val breaker =
    new CircuitBreaker(
      system.scheduler, // ActorSystem のスケジューラ
      maxFailures = 5, // エラーカウントがこの数字になるとリクエストを遮断
      callTimeout = 3.seconds, // リクエストをタイムアウトさせる時間
      resetTimeout = 3.seconds, // リクエストの遮断を一時的に取り止めるまでの時間
      maxResetTimeout = 30.seconds, // resetTimeout の上限
      exponentialBackoffFactor = 1.5 // 再試行の度に resetTimeout の時間を増やす倍数
    )

  // 外部サービスに対してリクエスト
  def fetchUserList(): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(uri = "http://localhost:8080/users"))

  // 外部サービスが返した結果を検証する関数
  val httpErrorResponseAsFailure: Try[HttpResponse] => Boolean = {
    // HTTPのステータスコードが OK 以外の場合はエラーとみなす
    case Success(response) => response.status != StatusCodes.OK
    case Failure(_) => true
  }

  val serviceRoutes: Route =
    path("userlist") {
      // curl -X GET http://localhost:8081/userlist
      get {
        val response: Future[HttpResponse] =
          breaker.withCircuitBreaker(fetchUserList, httpErrorResponseAsFailure)
        complete(response)
      }
    }

  val serverBindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(serviceRoutes, "localhost", 8081)

  println(s"Server online at http://localhost:8081/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  serverBindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}
