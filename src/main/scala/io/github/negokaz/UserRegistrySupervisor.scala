package io.github.negokaz

import akka.actor.SupervisorStrategy._
import akka.actor._
import org.h2.api.ErrorCode._
import org.h2.jdbc.JdbcSQLException

import scala.concurrent.duration._

object UserRegistrySupervisor {

  def props = Props[UserRegistrySupervisor]
  val name = "userRegistrySupervisor"
}

class UserRegistrySupervisor extends Actor with ActorLogging {

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() {
      case e: JdbcSQLException if e.getErrorCode == TABLE_OR_VIEW_NOT_FOUND_1 =>
        log.error("データベースのデータが壊れています！！！")
        Stop
      case e: JdbcSQLException if e.getErrorCode == CONNECTION_BROKEN_1 =>
        log.warning("データベースとの接続が切断されました")
        Restart
      case _: Exception => Escalate
    }

  val userRegistryActor = createUserRegistryActor()

  override def receive: Receive = {
    case m => userRegistryActor forward m
  }

  def createUserRegistryActor(): ActorRef = {
    context.actorOf(UserRegistryActor.props, UserRegistryActor.name)
  }
}
