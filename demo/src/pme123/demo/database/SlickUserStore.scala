package pme123.demo.database

import com.github.javafaker.Faker
import pme123.demo.database.Model.User
import pme123.demo.database.DbTables.api._
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import zio._
import zio.clock.Clock
import zio.stream.ZStream

object SlickInterOpt {

  implicit class ZIOObjOps2(private val obj: ZIO.type) extends AnyVal {
    def fromDBIO2[R](dbio: DBIO[R]): ZIO[DatabaseProvider, Throwable, R] =
      for {
        db <- ZIO.accessM[DatabaseProvider](_.get.db)
        r <- ZIO.fromFuture(_ => db.run(dbio))
      } yield r

    import zio.interop.reactivestreams._

    def fromStreamingDBIO2[T](dbio: StreamingDBIO[_, T]): ZStream[DatabaseProvider, Throwable, T] =
      for {
        db <- ZStream.accessM[DatabaseProvider](_.get.db)
        ss <- db.stream(dbio).toStream()
      } yield ss
  }
}

class SlickUserStore(val db: DatabaseProvider) extends UserStore {

  override def findAllUsers: IO[Throwable, ZStream[Any, Throwable, User]] = {
    val query = DbTables.users
    ZIO.fromStreamingDBIO(query.result).provide(db)
  }

  override def insertUser(user: Model.User): IO[Throwable, User] = {
    val insert = (DbTables.users returning DbTables.users) += user
    System.out.println(">>>" + insert.statements)
    ZIO.fromDBIO(insert).provide(db)
  }

  override def updateUser(user: Model.User): IO[Throwable, Int] = {
    val update = DbTables.users
      .filter(u => u.id === user.id)
      .update(user)
    ZIO.fromDBIO(update).provide(db)
  }
}

object SlickUserStore {
  val faker = new Faker()

  def createFakeUser: User = {
    val name = faker.lordOfTheRings().character()
    val pw = faker.crypto().md5()
    val t = faker.date().birthday().toInstant
    User(None, name, pw, t)
  }

  val live: ZLayer[DatabaseProvider with Clock, Throwable, Has[UserStore]] =
    ZLayer.fromFunction { (env: DatabaseProvider with Clock) =>
      new SlickUserStore(env)
    }
}