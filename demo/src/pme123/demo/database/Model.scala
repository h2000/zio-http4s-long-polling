package pme123.demo.database

import java.time.Instant

object Model {

  case class User(id: Option[Long], userName: String, password: String, createdAt: Instant)

  case class Group(id: Option[Long], groupName : String)

  case class UserGroup(userId: Long, groupId: Long)
}