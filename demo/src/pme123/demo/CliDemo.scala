package pme123.demo

import zio.clock.Clock
import zio.console.Console
import zio.logging.{Logging, log}
import zio._

object CliDemo extends zio.App {
  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program.provideLayer(prodEnv).fold(_ => ExitCode.failure, _ => ExitCode.success)

  // -- envs
  // prodEnv type of final program
  type MyEnv = Console with Clock with Logging

  //   construction of my prodEnv from given services using zlayer
  val logging: ZLayer[Console with Clock, Nothing, Logging] = Logging.console(
    format = (_, logEntry) => logEntry,
    rootLoggerName = Some("default-logger")
  )
  val prodEnv: ZLayer[Any, Nothing, Clock with Console with Logging] =
    clock.Clock.live ++ console.Console.live >+> logging

  // -- Cmds
  trait Cmd {
    def pretty: String = this.getClass.getSimpleName.replaceAll("[$]", "")
  }

  object Quit extends Cmd

  object Hello extends Cmd

  case class Echo(input: String) extends Cmd {
    override val pretty = s"Echooo: $input"
  }

  // -- state
  case class State(current: Cmd)

  // -- functional of our repl parts

  def parser(s: String): UIO[Cmd] = UIO.succeed(s).map {
    case "q" => Quit
    case "b" => throw new Exception("boom!"); Hello
    case i@_ => Echo(i)
  }

  def render(state: State): ZIO[Console, Nothing, Unit] = {
    console.putStrLn(s"${state.current.pretty}")
  }

  def readInput(): ZIO[Console with Clock, Nothing, String] =
    for {
      c <- zio.clock.currentDateTime.orDie
      _ <- console.putStr(s"$c > ")
      i <- console.getStrLn.orDie
    } yield (i)


  def controller(input: String, state: State): ZIO[Logging, Unit, State] = {
    for {
      c <- parser(input)
        .tap(s => log.info(s"$input parsed to ${s.pretty}"))
      _ <-
        if (c == Quit) log.error("Quit") *> ZIO.fail[Unit](())
        else ZIO.succeed(c)
    }
      yield State(c)
  }

  def step(state: State): ZIO[Console with Clock with Logging, Unit, State] = {
    for {
      _ <- render(state)
      c <- readInput().tap(c => log.debug(s"readInput => $c"))
      s <- controller(c, state)
    } yield s
  }

  val program: ZIO[MyEnv, Unit, Unit] = {
    def loop(state: State): ZIO[MyEnv, Unit, Unit] =
      step(state).flatMap(nextState => loop(nextState))

    loop(State(Hello))
  }

}
