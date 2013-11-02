package fr.njin.playoauth.common.domain

import scala.concurrent.Future


trait OauthResourceOwner

trait OauthResourceOwnerPermission[RO <: OauthResourceOwner, C <: OauthClient, P <: OauthPermission[C]]
  extends ((RO, C) => Future[Option[P]])
