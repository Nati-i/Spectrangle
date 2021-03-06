package view;

import controller.ClientOrServer;
import controller.Peer;
import controller.client.Client;
import controller.client.ClientCommands;
import controller.server.GameRoom;
import controller.server.Server;
import controller.server.ServerCommands;
import model.Piece;

import java.util.Arrays;

/**
 * Has a read method that "interprets" commands sent by a peer.
 * Runs methods (usually in ServerCommands or ClientCommands), depending on the command received.
 */
public class CommandInterpreter {

    private final ClientOrServer parent;
    private final ClientOrServer.Type parentType;

    /**
     * CommandInterpreter constructor. Takes a ClientOrServer as input as its "parent".
     *
     * @param parentInput parent of this class.
     */
    public CommandInterpreter(ClientOrServer parentInput) {
        this.parent = parentInput;
        this.parentType = parent.getType();
        if (parentType == ClientOrServer.Type.SERVER) {
            ServerCommands.setServerObject((Server) parent);
        }

    }

    /**
     * Reads input from networked peer. Seperates it into arguments (by spaces).
     * Then, depending on the "type' (server/client) of the parent, interprets the commands
     * And prints things to terminal, or executes a method somewhere else with the arguments.
     *
     * @param inputString The string that was received from the peer.
     * @param peer        The peer this was received from.
     */
    public void read(String inputString, Peer peer) {
        // separate by spaces
        // get first command
        String[] separateWords = inputString.split("\\s+");
        String command = separateWords[0];
        String[] args = Arrays.copyOfRange(separateWords, 1, separateWords.length);

        if (this.parentType == ClientOrServer.Type.CLIENT) {
            switch (command) {
                case "welcome":
                    parent.getPrinter().print("Name change acknowledged");
                    if (args.length > 1 && args[1].equals("chat")) {
                        parent.getPrinter().print(", and chat has been enabled.\n");
                    } else {
                        parent.getPrinter().print(".\n");
                    }
                    break;
                case "waiting":
                    parent.getPrinter().println("Waiting for game with requested amount of players.");
                    parent.getPrinter().println("Players in queue: " + String.join(", ", args));
                    break;
                case "start":
                    if (args[0].equals("with")) {
                        String[] newargs = Arrays.copyOfRange(args, 1, args.length);

                        TerminalInputHandler.clearScreen(parent);
                        parent.getPrinter().println("Starting new game with: " + String.join(", ", newargs));
                        ClientCommands.makeBoard(((Client) parent));
                    }
                    break;
                case "order":
                    parent.getPrinter().println("Order of turns: " + String.join(", ", args));
                    break;
                case "tiles":
                    // if parent does not have a board, make it
                    if (((Client) parent).getBoard() == null) {
                        ClientCommands.makeBoard(((Client) parent));
                    }

                    if (args[args.length - 1].equals(parent.getName())) {
                        // Print the board
                        parent.getPrinter().println(((Client) parent).getBoard().toPrinterString());

                        // run these methods
                        ClientCommands.otherTiles(args, ((Client) parent));
                        ClientCommands.setTiles(args, ((Client) parent));

                        // then, set the TerminalInputHandler to the appropriate state depending on
                        // if player must skip or not, and if player is an AI or not
                        if (args[args.length - 2].equals("skip")) {
                            if (((Client) parent).isAI()) {
                                ((Client) parent).getTerminalInputHandler().setState(TerminalInputHandler.InputState.AI_SKIP);
                            } else {
                                ((Client) parent).getTerminalInputHandler().setState(TerminalInputHandler.InputState.SKIP);
                            }

                        } else {
                            if (((Client) parent).isAI()) {
                                ((Client) parent).getTerminalInputHandler().setState(TerminalInputHandler.InputState.AI_TURN);
                            } else {
                                ((Client) parent).getTerminalInputHandler().setState(TerminalInputHandler.InputState.TURN);
                            }
                        }

                        // interrupt the TerminalInputHandler to make it change state
                        ((Client) parent).getTerminalInputHandler().setInterrupted(true);


                    }
                    break;

                case "player":
                    if (args[0].equals("skipped")) {
                        parent.getPrinter().print("Player " + args[1] + " skipped turn.");
                    } else if (args[1].equals("left")) {
                        TerminalInputHandler.clearScreen(parent);
                        parent.getPrinter().println("Player " + args[0] + " left mid-game. Returned to lobby");
                    }
                    break;
                case "replace":
                    parent.getPrinter().println(args[0] + " replaced tile " + args[1] + " with new tile:" + args[3]);
                    break;
                case "move":
                    parent.getPrinter().println(args[0] + " placed tile " + args[1] + " on position " + args[2] + ", earning " + args[3] + " points.");
                    ((Client) parent).getBoard().movePiece(Integer.parseInt(args[2]), new Piece(args[1]));
                    break;
                case "game":
                    if (args[0].equals("finished")) {
                        TerminalInputHandler.clearScreen(parent);
                        parent.getPrinter().println("Game finished");
                        parent.getPrinter().println("Scoreboard:");
                        for (int i = 1; i <= ((args.length - 2) / 2); i++) {
                            parent.getPrinter().println(args[i * 2] + ": " + args[(i * 2) + 1]);
                        }
                        parent.getPrinter().println("Going back to lobby");
                    }
                    ((Client) parent).getTerminalInputHandler().setState(TerminalInputHandler.InputState.COMMAND);
                    ((Client) parent).getTerminalInputHandler().setInterrupted(true);
                    break;
                case "chat":
                    String sender = args[0];
                    String[] messageArray = Arrays.copyOfRange(args, 1, args.length);
                    String message = String.join(" ", messageArray);
                    parent.getPrinter().println(sender + ": " + message);
                    break;
                case "invalid":
                    parent.getPrinter().println(inputString);
                    break;
                default:

                    parent.getPrinter().println(peer.getName() + " sent an unknown command that was ignored: " + inputString);

                    break;
            }
        } else if (this.parentType == ClientOrServer.Type.SERVER) {
            switch (command) {
                case "connect":
                    if (args.length > 0) {
                        ServerCommands.clientConnects(args, peer);
                    }
                    break;
                case "request":
                    if (args.length > 0) {
                        ServerCommands.clientRequests(args, peer);
                    }
                    break;
                case "place":
                    parent.getPrinter().println(peer.getName() + " :" + inputString);
                    if (args.length > 2) {
                        if (args[1].equals("on")) {

                            ((GameRoom) peer.getCurrentRoom()).checkPlace(peer, args[0], Integer.parseInt(args[2]));

                        }
                    }
                    break;
                case "skip":

                    ((GameRoom) peer.getCurrentRoom()).checkSkip(peer);

                    break;
                case "exchange":
                    if (args.length > 0) {

                        ((GameRoom) peer.getCurrentRoom()).checkExchange(peer, args[0]);

                    }
                    break;
                case "chat":
                    ServerCommands.sendChat(args, peer);
                    break;
                default:

                    parent.getPrinter().println(peer.getName() + " sent an unknown command that was ignored: " + inputString);

                    break;
            }
        }
    }
}


