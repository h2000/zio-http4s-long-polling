package pme123.demo.database

import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit

import com.github.javafaker.Faker
import slick.dbio.DBIOAction
import slick.lifted.ProvenShape
import slick.sql.SqlAction

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


object SlickGeneric extends slick.jdbc.JdbcProfile {
  // these imports have to be here

  import api._
  import slick.lifted.CaseClassShape

  case class Entity(id: Option[Long], createdAt: Timestamp, updatedAt: Timestamp)

  case class EntityColumns(id: Rep[Option[Long]], createdAt: Rep[Timestamp], updatedAt: Rep[Timestamp])

  implicit object EntityShape extends CaseClassShape(EntityColumns.tupled, Entity.tupled)

  abstract class RichTable[T](tag: Tag, name: String) extends Table[T](tag, name) {
    def id: Rep[Long] = column[Long]("ID", O.PrimaryKey, O.AutoInc) // This is the primary key column

    def createdAt: Rep[Timestamp] = column[Timestamp]("CREATED_AT")

    def updatedAt: Rep[Timestamp] = column[Timestamp]("UPDATED_AT")
  }

  def createEntity: Entity = {
    val now = java.sql.Timestamp.from(Instant.now())
    Entity(None, now, now)
  }

  trait Crud[T <: RichTable[A], A] {

    val tableQuery: TableQuery[T]

    def find(id: Long): SqlAction[Option[A], NoStream, Effect.Read] =
      tableQuery.filter(_.id === id).result.headOption

    def findAll: SqlAction[Seq[A], NoStream, Effect.Read] =
      tableQuery.result
  }

  case class City(name: String, crud: Entity)

  class CityTable(tag: Tag) extends RichTable[City](tag, "CITIES") {
    def name: Rep[String] = column[String]("NAME")

    override def * : ProvenShape[City] =
      (name, EntityColumns(id.?, createdAt, updatedAt)) <> (City.tupled, City.unapply)
  }

  val cityRepository: Crud[CityTable, City] = new Crud[CityTable, City] {
    override val tableQuery: SlickGeneric.api.TableQuery[CityTable] = TableQuery[CityTable]
  }
}

object SlickGenericApp extends App {

  import DbTables.api._
  import com.typesafe.config.ConfigFactory
  import pme123.demo.database.SlickGeneric.{City, cityRepository, createEntity}

  import scala.collection.JavaConverters._

  private val config = ConfigFactory.parseMap(
    Map(
      "url" -> "jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=4",
      "driver" -> "org.h2.Driver",
      "connectionPool" -> "disabled"
    ).asJava
  )


  object MyExecutionContext {

    import java.util.concurrent.Executors

    import scala.concurrent.ExecutionContext

    private val cpus = java.lang.Runtime.getRuntime.availableProcessors()
    private val factor = 3 // get from configuration  file
    private val noOfThread = cpus * factor
    implicit val ioThreadPool: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(noOfThread))
  }

  val db = slick.jdbc.H2Profile.backend.createDatabase(config, "")

  val faker = new Faker()
  val demoCities = (1 to 100).map(_ => {
    City(faker.address().cityName(), createEntity)
  })

  val create = DBIOAction.seq(
    cityRepository.tableQuery.schema.createIfNotExists,
    cityRepository.tableQuery returning cityRepository.tableQuery.map(c => c.id) ++= demoCities
  )

  import MyExecutionContext.ioThreadPool

  val csF: Future[Seq[City]] =
    for {
      _ <- db.run(create)
      cs <- db.run(cityRepository.findAll)
    } yield cs

  val cs = Await.result(csF, Duration(1, TimeUnit.SECONDS))
  cs.foreach(c => println(c))
}