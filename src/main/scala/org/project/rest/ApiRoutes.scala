package org.project.rest

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.util.Timeout
import org.project.marshalling.JsonSupport
import org.project.model._
import org.project.services.auth.AuthCheckService.{Auth, SuccessAuthCheck}
import org.project.services.auth.AuthenticationService.{FailAuth, SuccessAuth}
import org.project.services.msgs.MessageService.{Fail, Sent, Subscribe, TopicSource}
import org.project.services.rooms.RoomService.{KO, OK, Rooms}
import org.project.services.auth.{AuthCheckService, AuthenticationService}
import org.project.services.msgs.MessageService
import org.project.services.rooms.RoomService

import scala.concurrent.Future
import scala.concurrent.duration._

class ApiRoutes(roomsActor: ActorRef[RoomService.Command],
                checkActor: ActorRef[AuthCheckService.AuthCommand],
                authActor: ActorRef[AuthenticationService.AuthenticationCommand],
                msgActor: ActorRef[MessageService.MessageCommand])(implicit system: ActorSystem[_]) extends JsonSupport {

  import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  implicit val log: LoggingAdapter = Logging(system.classicSystem, getClass)

  // asking someone requires a timeout and a scheduler, if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout: Timeout = 3.seconds

  def check(credentials: Credentials): Future[Option[AuthUserContext]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    credentials match {
      case Credentials.Provided(token) =>
        checkActor.ask(Auth(token, _))
          .map {
            case SuccessAuthCheck(userCtx) => Some(userCtx)
            case _ => None
          }
      case _ => Future.successful(None)
    }
  }

  lazy val rootRoute: Route = {
    concat(
      path("auth")(userAuth),
      userOnlyRoutes
    )
  }

  lazy val userOnlyRoutes: Route = {
    // TODO : redirect to auth if auth has failed
    Route.seal {
      authenticateOAuth2Async(realm = "secure site", check) { user =>
        path("")(roomsRoute(user)) ~
          path("r" / Segment / "enter") { room => enterRoom(user, Room(room)) } ~
          path("r" / Segment / "exit") { room => exitRoom(user, Room(room)) } ~
          path("r" / Segment / "events") { room => messages(user.user, Room(room)) } ~
          path("r" / Segment) { room => roomRoute(user, Room(room)) }
      }
    }
  }
  lazy val userAuth: Route = {
    post {
      entity(as[User]) { user =>
        log.info(s"auth user $user")
        val tokenResponse = authActor.ask(AuthenticationService.Auth(user, _))
        onSuccess(tokenResponse) {
          case SuccessAuth(userAuth) => complete(StatusCodes.OK, userAuth)
          case FailAuth(reason) => complete(StatusCodes.Unauthorized, reason)
        }
      }
    }
  }

  def roomsRoute(ctx: AuthUserContext): Route = {
    concat(
      get {
        log.info("get Rooms")
        val rooms: Future[RoomService.Response] = roomsActor.ask(RoomService.GetRooms)
        onSuccess(rooms) {
          case rooms: Rooms => complete(rooms)
          case KO(reason) => complete(StatusCodes.InternalServerError, reason)
          case _ => complete(StatusCodes.InternalServerError)
        }
      },
      post {

        entity(as[Room]) { room =>
          log.info("create Room {}", room)
          val response: Future[RoomService.Response] = roomsActor.ask(RoomService.CreateRoom(ctx.user, room, _))
          onSuccess(response) {
            case OK(room) => complete(room)
            case KO(reason) => complete(StatusCodes.InternalServerError, reason)
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      }
    )
  }

  def enterRoom(ctx: AuthUserContext, toRoom: Room): Route = {
    post {
      log.info("enter to Room")
      val response: Future[RoomService.Response] = roomsActor.ask(RoomService.AddUserToRoom(ctx.user, toRoom, _))
      onSuccess(response) {
        case OK(room) => complete(StatusCodes.OK)
        case KO(reason) => complete(StatusCodes.InternalServerError, reason)
        case _ => complete(StatusCodes.InternalServerError)
      }
    }
  }

  def exitRoom(ctx: AuthUserContext, fromRoom: Room): Route = {
    post {
      log.info("exit from Room")
      val response: Future[RoomService.Response] = roomsActor.ask(RoomService.ExitUserFromRoom(ctx.user, fromRoom, _))
      onSuccess(response) {
        case OK(room) => complete(StatusCodes.OK)
        case KO(reason) => complete(StatusCodes.InternalServerError, reason)
        case _ => complete(StatusCodes.InternalServerError)
      }
    }
  }

  def roomRoute(ctx: AuthUserContext, toRoom: Room): Route = {
    post {
      log.info("sent msg")
      entity(as[MessageBody]) { msgBody =>
        val response: Future[MessageService.MessageResponse] = msgActor
          .ask(MessageService.SendMessage(toRoom, Message(msgBody.text, ctx.user.name), _))
        onSuccess(response) {
          case Sent => complete(StatusCodes.OK)
          case Fail(reason) => complete(StatusCodes.InternalServerError, reason)
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }

  def messages(user: User, room: Room): Route = {
    get {
      val flow = msgActor.ask(Subscribe(user, room, _))
      onSuccess(flow) {
        case TopicSource(source) => complete(source.map(msg => ServerSentEvent(msg)))
        case _ => reject
      }
    }
  }
}
