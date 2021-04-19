package org.project

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.project.model.Room
import org.project.redis.RedisRoomService

import scala.collection._

object RoomService {

  sealed trait Status

  object Successful extends Status

  object Failed extends Status

  sealed trait Response

  final case class OK(room: Room) extends Response

  final case class KO(reason: String) extends Response

  final case class Rooms(rooms: Set[Room] = Set.empty) extends Response

  sealed trait Command

  final case class CreateRoom(room: Room, replyTo: ActorRef[Response]) extends Command

  final case class AddUserToRoom(room: Room, replyTo: ActorRef[Response]) extends Command

  final case class GetRooms(replyTo: ActorRef[Response]) extends Command
  final case class SubMessages(room: Room, replyTo: ActorRef[Response]) extends Command


  def apply(redisRoomService: RedisRoomService)(implicit system: ActorSystem[_]): Behavior[Command] = Behaviors.receiveMessage {
    case GetRooms(replyTo) =>
      replyTo ! redisRoomService.getRooms()
      Behaviors.same
    case CreateRoom(room, replyTo) =>
      replyTo ! redisRoomService.createRoom(room)
        .map(room => OK(room))
        .getOrElse(KO(s"Room with name ${room.name} already exist"))
      Behaviors.same
    case AddUserToRoom(room, replyTo) =>
      replyTo ! redisRoomService.addUserToRoom(room)
        .map(l => OK(room))
        .getOrElse(KO(s"Can't add to ${room.name} room. Perhaps room isn't created"))
      Behaviors.same
    case _ =>
      Behaviors.ignore
  }
}

