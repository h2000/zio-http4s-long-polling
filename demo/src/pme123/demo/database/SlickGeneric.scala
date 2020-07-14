package pme123.demo.database

import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit

import com.github.javafaker.Faker
import pme123.demo.database.SlickGeneric.{Entity, EntityColumns, EntityRepository, EntityTable}
import slick.dbio.DBIOAction
import slick.lifted.ProvenShape

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._

object SlickGeneric extends slick.jdbc.JdbcProfile {
  // these imports need to stay inside

  import api._
  import slick.lifted.CaseClassShape

  case class Entity(id: Option[Long], createdAt: Timestamp, updatedAt: Timestamp)

  case class EntityColumns(id: Rep[Option[Long]], createdAt: Rep[Timestamp], updatedAt: Rep[Timestamp])

  implicit object EntityShape extends CaseClassShape(EntityColumns.tupled, Entity.tupled)

  abstract class EntityTable[T](tag: Tag, name: String) extends Table[T](tag, name) {
    def id: Rep[Long] = column[Long]("ID", O.PrimaryKey, O.AutoInc) // This is the primary key column

    def createdAt: Rep[Timestamp] = column[Timestamp]("CREATED_AT")

    def updatedAt: Rep[Timestamp] = column[Timestamp]("UPDATED_AT")
  }

  def createEntity: Entity = {
    val now = java.sql.Timestamp.from(Instant.now())
    Entity(None, now, now)
  }

  trait EntityRepository[T <: EntityTable[A], A] {

    val query: TableQuery[T]

    def find(id: Long): DBIO[Option[A]] =
      query.filter(_.id === id).result.headOption

    def findAll: DBIO[Seq[A]] =
      query.result
  }

}

object SlickGenericApp extends App {

  import DbTables.api._
  import com.typesafe.config.ConfigFactory
  import pme123.demo.database.SlickGeneric.createEntity

  // -- db config
  private val config = ConfigFactory.parseMap(
    Map(
      "url" -> "jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1;TRACE_LEVEL_FILE=4",
      "driver" -> "org.h2.Driver",
      "connectionPool" -> "disabled"
    ).asJava
  )

  val db = slick.jdbc.H2Profile.backend.createDatabase(config, "")

  // -- execution context for scala futures / for comprehension
  object MyExecutionContext {

    import java.util.concurrent.Executors

    import scala.concurrent.ExecutionContext

    private val cpus = java.lang.Runtime.getRuntime.availableProcessors()
    private val factor = 3 // get from configuration  file
    private val noOfThread = cpus * factor
    implicit val ioThreadPool: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(noOfThread))
  }

  // -- entity + repo
  case class City(name: String, crud: Entity)

  class CityTable(tag: Tag) extends EntityTable[City](tag, "CITIES") {
    def name: Rep[String] = column[String]("NAME")

    override def * : ProvenShape[City] =
      (name, EntityColumns(id.?, createdAt, updatedAt)) <> (City.tupled, City.unapply)
  }

  val cityRepository: EntityRepository[CityTable, City] = new EntityRepository[CityTable, City] {
    override val query: SlickGeneric.api.TableQuery[CityTable] = TableQuery[CityTable]
  }

  // -- demo data
  val faker = new Faker()
  val demoCities = (1 to 100).map(_ => {
    City(faker.address().cityName(), createEntity)
  })

  val create: DBIO[Unit] = DBIOAction.seq(
    cityRepository.query.schema.createIfNotExists,
    // return ids of newly created cities
    (cityRepository.query returning cityRepository.query.map(c => c.id))
      ++= demoCities
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
