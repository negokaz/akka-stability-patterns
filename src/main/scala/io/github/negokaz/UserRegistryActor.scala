package io.github.negokaz

import akka.actor.{ Actor, ActorLogging, Props }
import com.github.takezoe.slick.blocking.BlockingH2Driver.blockingApi._
import slick.jdbc.meta.MTable

object UserRegistryActor {

  case class User(id: Option[Int], name: String)
  class Users(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def * = (id.?, name) <> (User.tupled, User.unapply)
  }
  val users = TableQuery[Users]

  final case class ActionPerformed(description: String)
  private final case object CreateSchema
  final case object GetUsers
  final case class CreateUser(user: User)
  final case class GetUser(name: String)
  final case class DeleteUser(name: String)

  def props: Props = Props[UserRegistryActor]
  val name = "userRegistryActor"
}

class UserRegistryActor extends Actor with ActorLogging {
  import UserRegistryActor._

  val db = Database.forURL("jdbc:h2:tcp://localhost/./sample")

  /** DBセッション */
  implicit lazy val session = db.createSession()

  override def preStart(): Unit = {
    log.info("Starting")
    self ! CreateSchema
  }

  override def postRestart(reason: Throwable): Unit = {
    log.warning("Restarted")
  }

  override def postStop(): Unit = {
    log.error("Stopped")
    db.close()
  }

  /** メッセージを受信した際の振る舞いを定義するメソッド */
  override def receive: Receive = {
    case GetUsers =>
      // SELECT * FROM users;
      sender() ! users.list
    case CreateUser(user) =>
      // INSERT INTO users (name) VALUE ('${user.name}')
      users.insert(user)
      sender() ! ActionPerformed(s"User ${user.name} created.")
    case GetUser(name) =>
      // SELECT * FROM users WHERE name = '${name}';
      sender() ! users.filter(_.name === name).firstOption
    case DeleteUser(name) =>
      // DELETE FROM users WHERE name = '${name}'
      users.filter(_.name === name).delete
      sender() ! ActionPerformed(s"User ${name} deleted.")
    case CreateSchema =>
      // CREATE TABLE IF NOT EXISTS users (...);
      if (!MTable.getTables.list.exists(_.name.name == users.baseTableRow.tableName)) {
        users.schema.create
      }
  }
}
