import com.github.theon.uri.config.UriConfig
import com.github.theon.uri.Uri

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

}
