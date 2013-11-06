package fr.njin.playoauth

import play.api.mvc.RequestHeader
import org.apache.commons.codec.binary.Base64


object Utils {

  def toUrlFragment(query: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    Option(query).filterNot(_.isEmpty).map { params =>
      "#" + params.toSeq.flatMap { pair =>
        pair._2.map(v => pair._1+"="+URLEncoder.encode(v, "UTF-8"))
      }.mkString("&")
    }.getOrElse("")
  }

  def parseBasicAuth(request: RequestHeader): Option[(String, String)] = {
    request.headers.get("Authorization").flatMap { authorization =>
      authorization.split(" ").drop(1).headOption.flatMap { encoded =>
        new String(Base64.decodeBase64(encoded.getBytes)).split(":").toList match {
          case u :: p :: Nil => Some(u -> p)
          case _ => None
        }
      }
    }
  }

  def parseBearer(request: RequestHeader): Option[String] = {
    request.headers.get("Authorization").flatMap { authorization =>
      authorization.split(" ").drop(1).headOption
    }
  }

}
