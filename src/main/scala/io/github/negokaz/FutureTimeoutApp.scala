package io.github.negokaz

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

object FutureTimeoutApp extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val executionContext = system.dispatcher

  import akka.pattern.after

  val timeout: Future[String] = after(200.millis, using = system.scheduler) {
    Future.failed(new TimeoutException())
  }
  val future: Future[String] = Future {
    Thread.sleep(1000)
    "hello"
  }
  val result: Future[String] =
    Future.firstCompletedOf(Seq(future, timeout))

  // Future が完了するまで待つ
  Await.ready(result, Duration.Inf)

  println(s"${result}")
}
