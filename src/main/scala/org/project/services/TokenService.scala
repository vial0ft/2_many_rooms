package org.project.services

import org.project.model.User

object TokenService {

  // TODO: Base64 coded token
  def createToken(user: User): String = {
    user.name
  }
}
