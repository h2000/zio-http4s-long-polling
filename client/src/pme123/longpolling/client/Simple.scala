//package pme123.longpolling.client
//
//import java.io.IOException
//
//import zio.console.{Console, getStrLn, putStr, putStrLn}
//import zio.{URIO, ZIO}
//
///**
// *
// * User: klausmeier
// * Date: 29.06.20
// */
//object Simple extends zio.App {
//  override def run(args: List[String]): URIO[Console, Int] = {
//    (for {_ <- program1
//          _ <- prompt.forever
//          }
//      yield ())
//      .fold(_ => 1, _ => 0)
//
//  }
//
//  val program1: ZIO[Console, Nothing, Unit] =
//    putStrLn("Hello World")
//
//  val prompt: ZIO[Console, IOException, String] =
//    for {
//      _ <- putStr("> x:")
//      x <- getStrLn
//      _ <- putStrLn("x:=" + x)
//    } yield x
//
//}
