package domain

import scalikejdbc.async.{AsyncDB, AsyncDBSession}
import scala.concurrent.Future
import play.api.mvc.EssentialAction
import play.api.libs.iteratee.Iteratee

object DB {

  //TODO Is it a good choice?
  implicit val dbContext = scala.concurrent.ExecutionContext.Implicits.global

  def InTxAsync(action: AsyncDBSession => Future[EssentialAction]): EssentialAction = EssentialAction { request =>
    Iteratee.flatten(AsyncDB.withPool( tx => action(tx).map(_(request))))
  }

  def InTx(action: AsyncDBSession => EssentialAction): EssentialAction = InTxAsync { tx:AsyncDBSession =>
    Future.successful(action(tx))
  }
}
