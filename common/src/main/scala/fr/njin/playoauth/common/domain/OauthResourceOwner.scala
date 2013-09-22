package fr.njin.playoauth.common.domain


trait OauthResourceOwner[C <: OauthClient, P <: OauthPermission[C]] {
  def permission(client: C): Option[P]
}

