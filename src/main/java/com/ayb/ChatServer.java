package com.ayb;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ChatServer: A simple multi-client chat server that supports broadcasting, private messaging,
 * and multicast messaging. It also handles client disconnections and errors gracefully.
 */
public class ChatServer extends Thread {
    private boolean isServerActive = true; // Flag to indicate whether the server is running
    private int clientCount = 0; // Counter for the number of connected clients
    // Using CopyOnWriteArrayList to store connected clients safely in a multithreaded environment
    private List<ClientHandler> clientHandlerList = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        new ChatServer().start(); // Start the chat server thread
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(1234)) { // Server listens on port 1234
            while (isServerActive) {
                // Accept new client connections
                Socket clientSocket = serverSocket.accept();
                clientCount++; // Increment client count for each new connection
                // Create a new ClientHandler for the connected client and add it to the list
                ClientHandler clientHandler = new ClientHandler(clientSocket, clientCount);
                clientHandlerList.add(clientHandler);
                clientHandler.start(); // Start the client handler thread for this client
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage()); // Handle server socket issues
        }
    }

    /**
     * ClientHandler: A thread that handles communication with a connected client, including
     * private messages, multicast, and broadcasting messages.
     */
    class ClientHandler extends Thread {
        private Socket clientSocket;
        private PrintWriter output;
        private BufferedReader input;
        private int clientNumber; // The client’s unique number
        private String clientIP; // The IP address of the connected client

        public ClientHandler(Socket socket, int clientNumber) {
            this.clientSocket = socket;
            this.clientNumber = clientNumber;
            this.clientIP = socket.getRemoteSocketAddress().toString(); // Get client’s IP address
        }

        @Override
        public void run() {
            try {
                // Setup input and output streams for communication with the client
                input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                output = new PrintWriter(clientSocket.getOutputStream(), true);

                // Log the connection details on the server and send a welcome message to the client
                System.out.println("Client " + clientNumber + " connected. IP: " + clientIP);
                output.println("Welcome to the Chat Server, you are client number " + clientNumber);

                // Listen for messages from the client in a loop
                String message;
                while ((message = input.readLine()) != null) {
                    if (message.startsWith("@")) {
                        handlePrivateMessage(message); // Handle private message
                    } else if (message.startsWith("#")) {
                        handleMulticastMessage(message); // Handle multicast message
                    } else {
                        // Broadcast message to all clients except the sender
                        broadcastMessage("Broadcast from Client " + clientNumber + ": " + message, this);
                    }
                }
            } catch (IOException e) {
                System.err.println("Client " + clientNumber + " disconnected. IP: " + clientIP);
            } finally {
                disconnectClient(); // Ensure client disconnection is handled
            }
        }

        /**
         * Handles private messages sent in the format @clientNumber message.
         */
        private void handlePrivateMessage(String message) {
            try {
                String[] parts = message.split(" ", 2); // Split into "@clientNumber" and the message
                if (parts.length < 2 || parts[1].trim().isEmpty()) {
                    // If the message is incomplete, notify the sender
                    output.println("Error: Incomplete private message. Use format: @clientNumber message");
                    return;
                }

                // Extract the target client number from the message
                int targetClientNumber = Integer.parseInt(parts[0].substring(1));
                // Find the target client by number
                ClientHandler targetClient = findClientByNumber(targetClientNumber);

                if (targetClient != null) {
                    // Send the message to the target client and notify the sender
                    targetClient.output.println("Private message from Client " + clientNumber + ": " + parts[1]);
                    output.println("Private message sent to Client " + targetClientNumber);
                    System.out.println("Private message from Client " + clientNumber + " to Client " + targetClientNumber);
                } else {
                    // Notify sender if the target client doesn't exist
                    output.println("Error: Client " + targetClientNumber + " does not exist.");
                }
            } catch (NumberFormatException e) {
                output.println("Error: Invalid client number."); // Handle invalid client number format
            }
        }

        /**
         * Handles multicast messages sent in the format #client1,client2,... message.
         */
        private void handleMulticastMessage(String message) {
            try {
                String[] parts = message.split(" ", 2); // Split into "#client1,client2,..." and the message
                if (parts.length < 2 || parts[1].trim().isEmpty()) {
                    // If the message is incomplete, notify the sender
                    output.println("Error: Incomplete multicast message. Use format: #client1,client2,... message");
                    return;
                }

                // Extract the client numbers and parse them into a set to avoid duplicates
                String[] clientNumbers = parts[0].substring(1).split(",");
                Set<Integer> targetClientNumbers = new HashSet<>();
                List<Integer> invalidClients = new ArrayList<>();

                // Parse and validate each client number
                for (String clientNum : clientNumbers) {
                    try {
                        int clientNumber = Integer.parseInt(clientNum.trim());
                        if (findClientByNumber(clientNumber) != null) {
                            targetClientNumbers.add(clientNumber); // Add valid clients to the set
                        } else {
                            invalidClients.add(clientNumber); // Track invalid clients
                        }
                    } catch (NumberFormatException e) {
                        invalidClients.add(-1); // Invalid client number format
                    }
                }

                // If valid clients exist, send the message to them
                if (!targetClientNumbers.isEmpty()) {
                    for (ClientHandler client : clientHandlerList) {
                        if (targetClientNumbers.contains(client.clientNumber)) {
                            client.output.println("Multicast message from Client " + clientNumber + ": " + parts[1]);
                        }
                    }

                    // Notify the sender about the message status (successful clients + invalid clients)
                    String invalidClientsMessage = invalidClients.isEmpty() ? "" : " Clients not found: " + invalidClients;
                    output.println("Message sent to existing clients." + invalidClientsMessage);
                    System.out.println("Multicast message from Client " + clientNumber + " to Clients " + targetClientNumbers + invalidClientsMessage);
                } else {
                    output.println("Error: No valid clients found.");
                }
            } catch (Exception e) {
                output.println("Error: Invalid multicast message format."); // Handle formatting errors
            }
        }

        /**
         * Broadcasts a message to all clients except the sender.
         */
        private void broadcastMessage(String message, ClientHandler sender) {
            for (ClientHandler client : clientHandlerList) {
                if (client != sender) {
                    client.output.println(message); // Send the broadcast message to other clients
                }
            }
            System.out.println(message); // Log broadcast on the server
        }

        /**
         * Handles client disconnection by removing the client from the list and closing resources.
         */
        private void disconnectClient() {
            try {
                System.out.println("Client " + clientNumber + " has left the chat.");
                output.println("You have been disconnected.");
                clientHandlerList.remove(this); // Remove the client from the active list
                clientSocket.close(); // Close the client socket
            } catch (IOException e) {
                System.err.println("Error while disconnecting Client " + clientNumber + ": " + e.getMessage());
            }
        }

        /**
         * Finds a client by their assigned number in the client handler list.
         */
        private ClientHandler findClientByNumber(int clientNumber) {
            for (ClientHandler client : clientHandlerList) {
                if (client.clientNumber == clientNumber) {
                    return client; // Return the found client handler
                }
            }
            return null; // Return null if no client was found
        }
    }
}
