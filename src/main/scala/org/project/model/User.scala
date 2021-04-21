package org.project.model

final case class User(name: String)

final case class AuthUserContext(user: User, token: String)
