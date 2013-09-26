object Constants {

  val Username = "username"
  val Password = "password"

  val AuthorizationCode = "a_code"
  val ExpiredAuthorizationCode = "expired_code"
  val RevokedAuthorizationCode = "revoked_code"
  val AnotherAuthorizationCode = "another_code"

  val AuthorizationState = "a_state"

  val RedirectURI = "http://localhost:9000/"
  val InvalidURI = "localhost:9000"
  val ClientWithoutURI = "4.1.1"
  val ClientWithURI = "4.1.2"
  val ClientWithURIUnauthorized = "4.1.2.1"
  val ClientWithInvalidURI = "4.1.3.1"
  val UnauthorizedClient = "4.1.3.2"
  val ClientWithCode = "4.1.4"
  val AnotherClientWithCode = "4.1.4.1"

  val ImplicitGrantClientWithURI = "4.2.1"
}
