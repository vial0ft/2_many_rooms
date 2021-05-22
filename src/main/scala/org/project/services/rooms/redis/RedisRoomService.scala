package org.project.services.rooms.redis

import com.redis.RedisClient
import com.typesafe.config.Config
import org.project.marshalling.JsonSupport
import org.project.model.{Room, User}
import org.project.services.rooms.RoomService.Rooms
import org.project.services.rooms.redis.RedisRoomService._listStr2Rooms
import spray.json._

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

object RedisRoomService {

  private def _listStr2Rooms(x: List[String]): Rooms = Rooms(x.toSet.map(n => Room(n)))

  def apply(config: Config): RedisRoomService = {
    val host = config.getString("redis.rooms.host") // Gets the host and a port from the configuration
    val port = config.getInt("redis.rooms.port")
    val client = new RedisClient(host, port)
    client.flushdb

    new RedisRoomService(client)
  }
}


class RedisRoomService(_r: RedisClient) extends JsonSupport {

  def getRooms(): Rooms = {
    _r.keys()
      .map(k => k.flatten)
      .map(l => _listStr2Rooms(l))
      .getOrElse(Rooms())
  }

  def existedRoom(roomName: String): Option[Room] = {
    if (!_r.exists(roomName)) return None
    Some(Room(roomName))
  }

  def createRoom(user: User, room: Room): Try[Room] = {
    if (existedRoom(room.name).nonEmpty) return Failure(new Exception("Room already exists"))

    if (_r.set(room.name, usersSetFormat.write(Set.empty).compactPrint)) {
      Success(room)
    } else {
      Failure(new Exception(s"Cant create room $room"))
    }
  }

  def addUserToRoom(user: User, room: Room): Option[Boolean] = {
    updateValue(room.name)(v => doWithUser(v, user)((set, u) => !set.contains(u), (set, u) => set | Set(u)))
  }

  def exitUserFromRoom(user: User, room: Room): Option[Boolean] = {
    updateValue(room.name)(v => doWithUser(v, user)((set, u) => set.contains(u), (set, u) => set.filterNot(f => f == u)))
  }

  def roomIsEmpty(room: Room): Option[Boolean] = {
    _r.get(room.name).map(v => v.eq(usersSetFormat.write(Set.empty).compactPrint))
  }

  def closeRoomIfEmpty(room: Room): Option[Boolean] = {
    roomIsEmpty(room).flatMap(isEmpty => if (isEmpty) closeRoom(room) else None)
  }

  private def closeRoom(room: Room): Option[Boolean] = {
    _r.del(room.name).map(l => l != 0)
  }


  private def doWithUser(setStringValue: String, user: User)(
    f: (collection.Set[User], User) => Boolean,
    m: (collection.Set[User], User) => collection.Set[User]): Option[String] = {
    val setOfUsers = usersSetFormat.read(setStringValue.parseJson)
    if (!f(setOfUsers, user)) return None
    Some(usersSetFormat.write(m(setOfUsers, user)).compactPrint)
  }

  private def updateValue(key: String)(f: (String) => Option[String] = s => Some(s)): Option[Boolean] = {
    _r.get(key)
      .flatMap(v => f(v))
      .map(v => _r.set(key, v))
  }
}
