package pme123.demo

import pme123.demo.CliDemo.{Cmd, Echo, Quit, State}
import zio.ZIO
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{TestConsole, TestEnvironment}


object CliDemoTest extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Any] = {
    suite("all")(
      suite("console1")(
        testM("test1") {
          for {
            _ <- CliDemo.render(State(CliDemo.Hello))
            output <- TestConsole.output //.tap(a => zio.ZIO.effect(System.out.println(a)))
          } yield assert(output)(equalTo(Vector("Hello\n")))
        })
      , {
        val tests = ('a' to 'z').filter(c => c != 'b' && c != 'q').map(c =>
          testM(s"parser: $c --> Echo($c)") {
            CliDemo.parser(c.toString).map(assert(_)(equalTo(Echo(c.toString))))
          })
        suite("parse a .. z (skipping b, q)")(tests: _*)
      },
      suite("gen")(
        testM("gen") {
          checkM(Gen.alphaNumericString.filter(c => c != "b" && c != "q")) {
            input => assertM(CliDemo.parser(input).run)
            (succeeds(equalTo(Echo(input))))
          }
        })
      ,
      suite("parse b,q")(
        testM("q --> Quit") {
          CliDemo.parser("q").map(assert(_)(equalTo(Quit)))
        },
        testM("b --> Boom") {
          assertM(CliDemo.parser("b").run)(failsCause(isSubtype[Failure](anything)))
        }
      )
    )
  }

}
