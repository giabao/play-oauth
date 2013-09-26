import fr.njin.playoauth.as.OauthError
import fr.njin.playoauth.common.OAuth
import org.specs2.mutable.Specification
import play.api.libs.json.Json

class OauthErrorSpec extends Specification {

  "OauthError" should {
    "Deserialize to json" in {
      Seq(
        OauthError.InvalidRequestError(),
        OauthError.InvalidClientError(),
        OauthError.InvalidGrantError(),
        OauthError.UnauthorizedClientError(),
        OauthError.UnsupportedGrantTypeError(),
        OauthError.InvalidScopeError(),
        OauthError.AccessDeniedError(),
        OauthError.UnsupportedResponseTypeError()
      ).map(Json.toJson(_).toString) must beEqualTo(Seq(
        "{\"error\":\"invalid_request\"}",
        "{\"error\":\"invalid_client\"}",
        "{\"error\":\"invalid_grant\"}",
        "{\"error\":\"unauthorized_client\"}",
        "{\"error\":\"unsupported_grant_type\"}",
        "{\"error\":\"invalid_scope\"}",
        "{\"error\":\"access_denied\"}",
        "{\"error\":\"unsupported_response_type\"}"
      ))
    }

    "Convert to query" in {
      Seq[Map[String, Seq[String]]](
        OauthError.InvalidRequestError(),
        OauthError.InvalidClientError(),
        OauthError.InvalidGrantError(),
        OauthError.UnauthorizedClientError(),
        OauthError.UnsupportedGrantTypeError(),
        OauthError.InvalidScopeError(),
        OauthError.AccessDeniedError(),
        OauthError.UnsupportedResponseTypeError()
      ) must beEqualTo(Seq(
          Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.InvalidRequest)),
          Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.InvalidClient)),
          Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.InvalidGrant)),
          Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.UnauthorizedClient)),
          Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.UnsupportedGrantType)),
          Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.InvalidScope)),
          Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.AccessDenied)),
          Map(OAuth.OauthError -> Seq(OAuth.ErrorCode.UnsupportedResponseType))
      ))
    }
  }

}
