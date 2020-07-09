package pme123.demo.database

import pme123.demo.database.Model.User
import zio.{Has, IO}
import zio.stream.ZStream


trait UserStore {

  def insertUser(user: User): IO[Throwable, User]

  def updateUser(user: User): IO[Throwable, Int]

  def findAllUsers: IO[Throwable, ZStream[Any, Throwable, User]]
}

object UserStore {
  type TYPE = Has[UserStore]

}
