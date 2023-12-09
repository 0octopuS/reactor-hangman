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
import java.net.SocketTimeoutException
import scala.collection.mutable.Map

class HangmanGame(val hiddenWord: String, val initialGuessCount: Int) {
  require(hiddenWord != null && hiddenWord.length > 0)
  require(initialGuessCount > 0)

  var gameState: GameState = GameState(hiddenWord, initialGuessCount, Set())
  val handlers: scala.collection.mutable.Set[EventHandler[_]] =
    scala.collection.mutable.Set()
  val playerHandlersMap: Map[Int, Socket] = Map()
  val playerNames: Map[Int, String] = Map()
}

class PlayerHandler(socket: Socket, game: HangmanGame, dispatcher: Dispatcher)
    extends EventHandler[(String, Socket)] {
  private val in = new BufferedReader(
    new InputStreamReader(socket.getInputStream)
  )
  private val out = new PrintWriter(socket.getOutputStream, true)

  override def getHandle: Handle[(String, Socket)] = {
    new Handle[(String, Socket)] {
      override def read(): (String, Socket) = {
        try {
          (in.readLine(), socket)
        } catch {
          case _ => null
        }
      }
    }
  }

  override def handleEvent(evt: (String, Socket)): Unit = {
    evt match {
      case (x, null) => {
        println("Got null") // This should be impossible
      }
      case (msg, msgSocket) if msg.length > 1 => {
        val name = msg
        game.playerNames += (socket.getPort() -> name)
        val outU = new PrintWriter(socket.getOutputStream, true)

        outU.println(
          game.gameState.getMaskedWord + " " + game.gameState.guessCount
        )
      }
      case (msg, msgSocket) if msg.length == 1 => {
        game.gameState = game.gameState.makeGuess(msg.charAt(0))
        game.playerHandlersMap.foreach {
          case (port, s) => {
            val outU = new PrintWriter(s.getOutputStream, true)
            val name =
              game.playerNames.getOrElse(msgSocket.getPort(), "Unknown")
            outU.println(
              msg.charAt(
                0
              ) + " " + game.gameState.getMaskedWord + " " + game.gameState.guessCount + " " + name // This still needs a name at the end
            )
          }
        }

        if (game.gameState.isGameOver) {
          game.handlers.iterator.foreach { handler =>
            dispatcher.removeHandler(handler)
          }
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
    val connectionHandler =
      new ConnectionHandler(serverSocket, game, dispatcher)
    dispatcher.addHandler(connectionHandler)
    game.handlers += connectionHandler

    // Main loop: handle new connections and events
    while (!game.gameState.isGameOver) {
      try {
        dispatcher.handleEvents()
      } catch {
        case e: Exception => println("Error handling events: " + e.getMessage)
      }
    }

    serverSocket.close()
    println("Game Over!")
  }

}

// Connection Handler is responsible for listening for new client connections on the opened ServerSocket
class ConnectionHandler(
    server: ServerSocket,
    game: HangmanGame,
    dispatcher: Dispatcher
) extends EventHandler[Socket] {
  private val socketServer: ServerSocket = server;

  override def getHandle: Handle[Socket] = {
    new Handle[Socket] {
      override def read(): Socket = {
        try {
          socketServer.accept()
        } catch {
          case _: SocketTimeoutException => null // Ignore timeout
        }
      }
    }
  }

  override def handleEvent(evt: Socket): Unit = {
    val handler = new PlayerHandler(evt, game, dispatcher)
    game.handlers += handler
    game.playerHandlersMap += (evt.getPort() -> evt)
    dispatcher.addHandler(handler)
  }
}
