package org.project

import akka.actor.typed.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

object Starter extends App{

  val config: Config = ConfigFactory.load()

  val system: ActorSystem[Server.Message] = ActorSystem(Server(config), "RoomsServer")
}
