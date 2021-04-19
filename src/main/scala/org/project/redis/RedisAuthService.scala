package org.project.redis

import com.redis.RedisClient
import com.typesafe.config.Config
import org.project.model.User

object RedisAuthService {


  def apply(config: Config) : RedisAuthService = {
    val host = config.getString("redis.users.host") // Gets the host and a port from the configuration
    val port = config.getInt("redis.users.port")
    val client = new RedisClient(host, port)
    client.flushall
    new RedisAuthService(client)
  }
}

class RedisAuthService(client: RedisClient) {

  // TODO: true check the user on redis
  def getUser(token: String): Option[User] = {
    Some(User(token))
  }
}
