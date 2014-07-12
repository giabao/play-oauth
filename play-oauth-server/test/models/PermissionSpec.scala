package models

import org.specs2.mutable.{Around, Specification}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.test.FakeApplication
import play.api.test.Helpers._
import org.specs2.execute.{Result, AsResult}
import scalikejdbc.async.{NamedAsyncDB, AsyncDBSession}
import scala.language.postfixOps

/**
 * User: bathily
 * Date: 01/10/13
 */
class PermissionSpec extends Specification {

  "Permission" should {
    "create a new record" in new WithFixtures {
      println(users)
      //Permission.create()
    }
  }
}

trait WithDB extends Around {

  val timeout = 10 seconds
  lazy val db:NamedAsyncDB = NamedAsyncDB()

  def fakeApp(): FakeApplication = FakeApplication(
    withoutPlugins = Seq("play.api.cache.EhCachePlugin"),
    additionalPlugins = Seq("com.github.tototoshi.play2.flyway.Plugin", "scalikejdbc.async.PlayPlugin"),
    additionalConfiguration = Map(
      "logger.root" -> "INFO",
      "logger.play" -> "INFO",
      "logger.application" -> "INFO",
      "dbplugin" -> "disabled",
      "evolutionplugin" -> "disabled",
      "db.default.driver" -> "com.mysql.jdbc.Driver",
      "db.default.url" -> "jdbc:mysql://localhost/oauths-test",
      "db.default.user" -> "oauths",
      "db.default.password" -> "oauths",
      "db.default.schema" -> "",
      "db.default.poolInitialSize" -> "1",
      "db.default.poolMaxSize" -> "2",
      "db.default.poolValidationQuery" -> "select 1",
      "db.default.poolConnectionTimeoutMillis" -> "2000"
    )
  )

  def fixture(implicit session: AsyncDBSession, ec: ExecutionContext): Future[Unit] = Future.successful()

  def around[T](t: => T)(implicit evidence$1: AsResult[T]): Result = running(fakeApp()) {

    implicit val ec = scala.concurrent.ExecutionContext.global

    Await.result(
      db.localTx { implicit tx =>
        fixture.flatMap { _ =>
          val r = AsResult.effectively(t)
          tx.rollback().map(_ => r)
        }
      },
      timeout
    )

    /*
    implicit val session = db.sharedSession

    Await.result(fixture.map { _ =>
      AsResult.effectively(t)
    }, timeout)
    */
  }


}

trait WithFixtures extends WithDB {

  var users: Seq[User] = Seq.empty

  override def fixture(implicit session: AsyncDBSession, ec: ExecutionContext): Future[Unit] = {
    Future.sequence(Seq(1,2).map(i => User.create(s"user$i", "password", "user", s"$i"))).map(users = _)
  }
}