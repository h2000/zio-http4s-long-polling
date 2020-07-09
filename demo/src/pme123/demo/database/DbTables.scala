package pme123.demo.database

import java.sql.Timestamp

import pme123.demo.database.Model.{Group, User, UserGroup}
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

object DbTables extends JdbcProfile {

  import api._

  class UserTable(tag: Tag) extends Table[User](tag, "user") {
    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc, O.SqlType("BIGINT"))

    def username: Rep[String] = column[String]("userName", O.SqlType("VARCHAR"))

    def password: Rep[String] = column[String]("password", O.SqlType("VARCHAR"))

    def serverKey: Rep[String] = column[String]("serverkey", O.SqlType("VARCHAR"), O.Length(64))

    def salt: Rep[String] = column[String]("salt", O.SqlType("VARCHAR"), O.Length(64))

    def iterations: Rep[Int] = column[Int]("iterationcount", O.SqlType("INT"))

    def created: Rep[Timestamp] = column[Timestamp]("created_at", O.SqlType("TIMESTAMP"))

    val mkUser: ((Option[Long], String, String, String, String, Int, Timestamp)) => User = {
      case (id, name, pwd, _, _, _, created) => User(id, name, pwd, created.toInstant)
    }

    def unMkUser(u: User): Option[(Option[Long], String, String, String, String, Int, Timestamp)] =
      Some(u.id, u.userName, u.password, "", "", 0, new Timestamp(u.createdAt.toEpochMilli))

    override def * : ProvenShape[User] = (id.?, username, password, serverKey, salt, iterations, created) <> (mkUser, unMkUser)
  }

  val users: TableQuery[UserTable] = TableQuery[UserTable]

  class GroupTable(tag: Tag) extends Table[Group](tag, "group") {
    def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc, O.SqlType("BIGINT"))

    def groupName: Rep[String] = column[String]("groupName", O.SqlType("VARCHAR"))

    override def * : ProvenShape[Group] = (id.?, groupName) <> (Group.tupled, Group.unapply)
  }

  val groups: TableQuery[GroupTable] = TableQuery[GroupTable]

  class UserGroupTable(tag: Tag) extends Table[UserGroup](tag, "user_group") {

    def userId: Rep[Long] = column[Long]("userId", O.SqlType("BIGINT"))

    def groupId: Rep[Long] = column[Long]("groupId", O.SqlType("BIGINT"))

    override def * : ProvenShape[UserGroup] = (userId, groupId) <> (UserGroup.tupled, UserGroup.unapply)
  }

  val userGroups: TableQuery[UserGroupTable] = TableQuery[UserGroupTable]

  val schema: DbTables.DDL = users.schema ++ groups.schema ++ userGroups.schema
}