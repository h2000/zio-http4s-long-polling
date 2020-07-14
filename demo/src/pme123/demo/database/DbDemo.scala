package pme123.demo.database

import java.util.Date
import java.util.concurrent.Executors

import com.typesafe.config.{Config, ConfigFactory}
import pme123.demo.database.Model.{Group, User, UserGroup}
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcBackend
import zio._
import zio.clock.Clock
import zio.logging.{Logging, log}
import zio.stream.ZSink

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext


object MyExecutionContext {

  private val cpus = java.lang.Runtime.getRuntime.availableProcessors()
  private val factor = 3 // get from configuration  file
  private val noOfThread = cpus * factor
  implicit val ioThreadPool: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(noOfThread))
}

object DbDemo extends zio.App {
  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program
      .provideLayer(env)
      .fold(
        _ => ExitCode.failure,
        _ => ExitCode.success
      )


  type Env = ZEnv with UserStore.TYPE with Logging with DatabaseProvider

  val program: ZIO[Env, Throwable, Unit] = {
    import DbTables.api._
    import slick.interop.zio.syntax._

    val createSchema = DbTables.schema.createIfNotExists
    val insertRandomUsers = DbTables.users ++= (1 to 100).map(_ => SlickUserStore.createFakeUser)
    val allUsers = DbTables.users.result
    val allGroups = DbTables.groups.result
    val insertGroups = (DbTables.groups returning DbTables.groups) ++= Seq(Group(None, "admins"), Group(None, "users"))
    val insertGroups2 =
      (DbTables.groups returning DbTables.groups) ++= Seq(Group(None, "admins2"), Group(None, "users2"))

    def delGroups(ids: Seq[Long]) = {
      val groupsToDelete = DbTables.groups.filter(g => g.id.inSet(ids.toSet))
      groupsToDelete.delete.named("deleteGroupsByIds")
    }

    import MyExecutionContext.ioThreadPool
    val insertAndDel = (for {
      // TODO why do we need a thread pool here?
      gs <- insertGroups2.map(g => g.map(_.id).filter(_.isDefined).map(_.get))
      _ <- delGroups(gs)
    } yield ()).transactionally

    val groupsWithUsers = for {
      ug <- DbTables.users join
        DbTables.userGroups on (_.id === _.userId) join
        DbTables.groups on (_._2.groupId === _.id)
    } yield (ug._2, ug._1._1)

    val groupsWithUsers2 = (for {
      ug <- DbTables.userGroups
      g <- ug.group
      u <- ug.user
    } yield (g, u)).result

    val step1 =
      for {
        _ <- ZIO.fromDBIO(createSchema).orDie
        _ <- ZIO.fromDBIO(insertRandomUsers).orDie
        _ <- log.error("DB filled")
        repo <- ZIO.access[UserStore.TYPE](_.get)
        u1 <- repo.insertUser(User(None, "foo1", "pw1", new Date().toInstant))
        _ <- repo.updateUser(u1.copy(password = "new password")).orDie
        us <- repo.findAllUsers
        _ <- us.foreach(u => log.info(s"User: $u"))
        newGroups <- ZIO.fromDBIO(insertGroups)
        adminGroup = newGroups(0)
        standardGroup = newGroups(1)
        gs <- ZIO.fromStreamingDBIO(DbTables.groups.result)
        _ <- gs.foreach(u => log.info(s"Groups: $u"))
        _ <- ZIO.fromDBIO(DbTables.userGroups ++= newGroups.map(g => UserGroup(u1.id.get, g.id.get)))
        ugs <- ZIO.fromStreamingDBIO(groupsWithUsers.result)
        _ <- ugs.run(ZSink.collectAll).map(_.toList)
        ugs2 <- ugs.foreach(u => log.info(s"Groups: $u"))
        j2 <- ZIO.fromDBIO(groupsWithUsers2).map(_.groupMap(_._2)(_._1))
        _ <- log.info(s"$j2")
        //        _ <- console.putStrLn("any char to stop") *> console.getStrLn
        _ <- ZIO.fromDBIO(insertAndDel)
        gs3 <- ZIO.fromDBIO(allGroups)
        _ <- console.putStrLn(s"$gs3")

      } yield ()

    step1.tapCause(cause => log.error("Error:", cause))
  }

  private val config = ConfigFactory.parseMap(
    Map(
      "url" -> "jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=4",
      "driver" -> "org.h2.Driver",
      "connectionPool" -> "disabled"
    ).asJava
  )
  val dbConfigLayer: ZLayer[Any, Throwable, Has[Config]] = ZLayer.fromEffect(ZIO.effect(config))
  val dbBackendLayer: ULayer[Has[JdbcBackend]] = ZLayer.succeed(slick.jdbc.H2Profile.backend)

  val dbEnv: ZLayer[Any, Throwable, DatabaseProvider with Clock with UserStore.TYPE] =
    (dbConfigLayer ++ dbBackendLayer) >>>
      DatabaseProvider.live >+>
      zio.clock.Clock.live >+>
      SlickUserStore.live

  val env: ZLayer[Any, Throwable, Env] =
    ZEnv.live >+> (dbEnv ++ Logging.console())
}