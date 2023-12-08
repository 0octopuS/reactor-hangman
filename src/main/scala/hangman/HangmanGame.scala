// group 1
// 123456 Firstname Lastname
// 654321 Firstname Lastname

package hangman

import reactor.api.{EventHandler, Handle}
import reactor.Dispatcher
import java.net.{ServerSocket, Socket}
import scala.io.BufferedSource
import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import scala.collection.mutable.ListBuffer

class HangmanGame(val hiddenWord: String, val initialGuessCount: Int) {
  require(hiddenWord != null && hiddenWord.length > 0)
  require(initialGuessCount > 0)

  var gameState: GameState = GameState(hiddenWord, initialGuessCount, Set())
  val playerHandlers: ListBuffer[Socket] = ListBuffer[Socket]()
}

class PlayerHandler(socket: Socket, game: HangmanGame, dispatcher: Dispatcher)
    extends EventHandler[String] {
  private val in = new BufferedReader(
    new InputStreamReader(socket.getInputStream)
  )
  private val out = new PrintWriter(socket.getOutputStream, true)

  override def getHandle: Handle[String] = {
    new Handle[String] {
      override def read(): String = {
        try {
          in.readLine()
        } catch {
          case _ => null
        }
      }
    }
  }

  override def handleEvent(evt: String): Unit = {
    evt match {
      case null => {
        println("Got null") // This should not happen
      }
      case _ if evt.length > 1 => {
        val name = evt
        game.playerHandlers.foreach((s) =>
          out.println(
            game.gameState.getMaskedWord + game.gameState.getGuessesLeft
          )
        )
      }
      case _ if evt.length == 1 => {
        println(evt)
        game.gameState = game.gameState.makeGuess(evt.charAt(0))
        print(game.playerHandlers)
        game.playerHandlers.foreach((s) =>
          out.println(
            evt.charAt(
              0
            ) + " " + game.gameState.getMaskedWord + game.gameState.getGuessesLeft // This still needs a name at the end
          )
        )
        // out.println(game.gameState.getMaskedWord)
        if (game.gameState.isGameOver) {
          dispatcher.removeHandler(this)
        }
      }
    }
  }
}

object HangmanGame {
  def main(args: Array[String]): Unit = {
    val word: String = args(0) // first program argument
    val guessCount: Int = args(1).toInt // second program argument
    val game: HangmanGame = new HangmanGame(word, guessCount)

    val dispatcher = new Dispatcher();
    val serverSocket = new ServerSocket(0);
    println(s"Server started on port ${serverSocket.getLocalPort}")

    // val socket: Socket = serverSocket.accept()
    // game.playerHandlers += socket
    // val handler = new PlayerHandler(socket, game, dispatcher)
    // dispatcher.addHandler(handler)
    // dispatcher.handleEvents()

    while (!game.gameState.isGameOver) {
      val socket: Socket = serverSocket.accept()
      val handler = new PlayerHandler(socket, game, dispatcher)
      print("adding a new one, baby")
      game.playerHandlers += socket
      dispatcher.addHandler(handler)
      dispatcher.handleEvents()
    }

    serverSocket.close()
    println("Game Over!")
  }

}

// class ConnectionHandler(server: SocketServer)
//     extends EventHandler[String] {
//   private val socketServer: SocketServer = server;

//   override def getHandle: Handle[String] = {
//     new Handle[String] {
//       override def read(): String = {
//         try {
//           server.accept();
//         } catch {
//           case _ => null
//         }
//       }
//     }
//   }

//   override def handleEvent(evt: String): Unit = {
//     evt match {
//       case null => {
//         println("Got null") // This should not happen
//       }
//       case _ => {
//         println(evt)
//         game.gameState = game.gameState.makeGuess(evt.charAt(0))
//         print(game.playerHandlers)
//         game.playerHandlers.foreach((s) =>
//           out.println(evt.charAt(0) + " " + game.gameState.getMaskedWord)
//         )
//         // out.println(game.gameState.getMaskedWord)
//         if (game.gameState.isGameOver) {
//           dispatcher.removeHandler(this)
//         }
//       }
//     }
//   }
// }
