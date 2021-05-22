package org.project

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import com.typesafe.config.Config
import org.project.rest.ApiRoutes
import org.project.services.auth.{AuthCheckService, AuthenticationService, TokenService}
import org.project.services.msgs.{KafkaHelper, MessageService}
import org.project.services.rooms.redis.RedisRoomService
import org.project.services.rooms.RoomService

import scala.concurrent.Future
import scala.util.{Failure, Success}

/***
 * Configuration server on start
 */
object Server {
  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message

  private final case class Started(binding: ServerBinding) extends Message

  case object Stop extends Message


  def apply(config: Config): Behavior[Message] = Behaviors.setup { ctx =>

    implicit val system: ActorSystem[Nothing] = ctx.system

    val roomRedisService: RedisRoomService = RedisRoomService(config)
    val kafka: KafkaHelper = KafkaHelper(config)
    val tokenService: TokenService = TokenService(config)

    val roomsService = ctx.spawn(RoomService(roomRedisService), "RoomsService")
    val check = ctx.spawn(AuthCheckService(tokenService), "AuthCheckService")
    val authService = ctx.spawn(AuthenticationService(tokenService), "AuthenticationService")
    val msgService = ctx.spawn(MessageService(kafka), "MessageService")

    val routes = new ApiRoutes(roomsService, check, authService, msgService)

    val host = config.getString("http.host") // Gets the host and a port from the configuration
    val port = config.getInt("http.port")

    val serverBinding: Future[Http.ServerBinding] = Http().newServerAt(host, port).bind(routes.rootRoute)

    ctx.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(ex) => StartFailed(ex)
    }

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          ctx.log.info(
            "Stopping server http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          Behaviors.stopped
      }.receiveSignal {
        case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
      }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(cause) =>  throw new RuntimeException("Server failed to start", cause)
        case Started(binding) =>
          ctx.log.info(
            "Server online at http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort)
          if (wasStopped) ctx.self ! Stop
          running(binding)
        case Stop =>
          // we got a stop message but haven't completed starting yet,
          // we cannot stop until starting has completed
          starting(wasStopped = true)
      }

    starting(wasStopped = false)
  }
}
