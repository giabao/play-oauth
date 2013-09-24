package fr.njin.playoauth.as


/**
 * User: bathily
 * Date: 24/09/13
 */
object Utils {

  def toUrlFragment(query: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    Option(query).filterNot(_.isEmpty).map { params =>
      "#" + params.toSeq.flatMap { pair =>
        pair._2.map(v => pair._1+"="+URLEncoder.encode(v, "UTF-8"))
      }.mkString("&")
    }.getOrElse("")
  }

}
