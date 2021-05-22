package org.project.services.rooms

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.project.model.{Room, User}
import org.project.services.rooms.redis.RedisRoomService

import scala.collection.Set

object RoomService {

  sealed trait Status

  object Successful extends Status

  object Failed extends Status

  sealed trait Response

  final case class OK(room: Room) extends Response

  final case class KO(reason: String) extends Response

  final case class Rooms(rooms: Set[Room] = Set.empty) extends Response

  sealed trait Command

  final case class CreateRoom(user: User, room: Room, replyTo: ActorRef[Response]) extends Command

  final case class AddUserToRoom(user: User, room: Room, replyTo: ActorRef[Response]) extends Command

  final case class ExitUserFromRoom(user: User, room: Room, replyTo: ActorRef[Response]) extends Command

  final case class GetRooms(replyTo: ActorRef[Response]) extends Command

  final case class SubMessages(room: Room, replyTo: ActorRef[Response]) extends Command


  def apply(redisRoomService: RedisRoomService): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case GetRooms(replyTo) =>
          replyTo ! redisRoomService.getRooms()
          Behaviors.same
        case CreateRoom(user, room, replyTo) =>
          replyTo ! redisRoomService.createRoom(user, room)
            .map(room => OK(room))
            .getOrElse(KO(s"Room with name ${room.name} already exist"))
          Behaviors.same
        case AddUserToRoom(user, room, replyTo) =>
          val response = redisRoomService.addUserToRoom(user, room) match {
            case Some(true) => OK(room)
            case _ => KO(s"Can't add to ${room.name} room. Perhaps room isn't created or user already here")
          }
          replyTo ! response
          Behaviors.same
        case ExitUserFromRoom(user, room, replyTo) =>
          val response = redisRoomService.exitUserFromRoom(user, room) match {
            case Some(true) => OK(room)
            case _ => KO(s"Can't exclude from ${room.name} room. Perhaps room isn't created or user isn't here")
          }

          redisRoomService.closeRoomIfEmpty(room) match {
            case Some(true) => ctx.log.debug(s"room $room closed")
            case _ => ctx.log.debug(s"can't close the room $room")
          }

          replyTo ! response
          Behaviors.same
        case _ =>
          Behaviors.ignore
      }
    }
}
