import com.github.theon.uri.config.UriConfig
import com.github.theon.uri.Uri
import play.api.mvc.{AnyContentAsFormUrlEncoded, AnyContentAsEmpty}
import play.api.test.FakeRequest

/**
 * User: bathily
 * Date: 19/09/13
 */
object Utils {


  def url(uri:Uri)(implicit c: UriConfig = UriConfig.default):String = {
    val schemeStr = uri.scheme.map(_ + "://").getOrElse("//")
    val userInfo =  uri.user.map(_ +  uri.password.map(":" + _).getOrElse("") + "@").getOrElse("")
    uri.host.map(schemeStr + userInfo + _).getOrElse("") +
      uri.port.map(":" + _).getOrElse("") +
      uri.path(c)
  }

  def fullUrl(uri:String, query: Seq[(String, String)]) = {
    import java.net.URLEncoder
    uri + Option(query.groupBy(_._1).mapValues(_.map(_._2))).filterNot(_.isEmpty).map { params =>
      (if (uri.contains("?")) "&" else "?") + params.toSeq.flatMap { pair =>
        pair._2.map(value => pair._1 + "=" + URLEncoder.encode(value, "utf-8"))
      }.mkString("&")
    }.getOrElse("")
  }

  object OauthFakeRequest {
    def apply(query: (String, String)*):FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", fullUrl("/", query))
  }

  object OauthTokenFakeRequest {
    def apply(query: (String, String)*):FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", "").withFormUrlEncodedBody(query:_*)
  }

}
