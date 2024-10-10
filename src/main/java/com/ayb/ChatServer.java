package com.ayb;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple server that acts as a chatroom. Clients send messages and the server broadcasts them to other connected clients.
 */
public class ChatServer extends Thread {
    private boolean isServerActive = true; // Flag to indicate if the server is running
    private int clientCount = 0; // Counter for the number of connected clients
    private List<ClientHandler> clientHandlerList = new ArrayList<>(); // List to manage all client handlers

    public static void main(String[] args) {
        new ChatServer().start(); // Start the server thread
    }

    @Override
    public void run() {
        try {
            // Start the server on port 1234
            ServerSocket serverSocket = new ServerSocket(1234);

            // Keep the server running and accept client connections
            while (isServerActive) {
                Socket clientSocket = serverSocket.accept(); // Wait for a client to connect
                ++clientCount; // Increment client count on each new connection
                ClientHandler clientHandler = new ClientHandler(clientSocket, clientCount); // Start a new thread for each client
                clientHandlerList.add(clientHandler); // Add client handler to the list
                clientHandler.start(); // Start the client handler thread
            }
        } catch (IOException e) {
            throw new RuntimeException("Server encountered an error: ", e); // Handle exceptions by throwing a runtime exception
        }
    }

    /**
     * Handles interaction with a connected client, including broadcasting messages to other clients.
     */
    class ClientHandler extends Thread {
        private Socket clientSocket; // Socket for communication with the client
        private int clientNumber; // Unique number assigned to each client

        public ClientHandler(Socket clientSocket, int clientNumber) {
            this.clientSocket = clientSocket; // Initialize client socket
            this.clientNumber = clientNumber; // Initialize client number
        }

        public Socket getClientSocket() {
            return clientSocket;
        }

        public void setClientSocket(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public int getClientNumber() {
            return clientNumber;
        }

        public void setClientNumber(int clientNumber) {
            this.clientNumber = clientNumber;
        }

        /**
         * Broadcasts a message to all connected clients except the sender.
         *
         * @param message The message to be broadcasted
         * @param senderSocket The socket of the sender client
         */
        public void broadcastMessage(String message, Socket senderSocket) {
            try {
                for (ClientHandler client : clientHandlerList) {
                    if (client.clientSocket != senderSocket) { // Don't send the message to the sender
                        PrintWriter printWriter = new PrintWriter(client.getClientSocket().getOutputStream(), true);
                        printWriter.println(message); // Send the message to other clients
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error broadcasting message: ", e);
            }
        }

        @Override
        public void run() {
            try {
                // Set up input and output streams to communicate with the client
                InputStream inputStream = clientSocket.getInputStream();
                InputStreamReader isr = new InputStreamReader(inputStream);
                BufferedReader br = new BufferedReader(isr);

                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true); // Output stream to send messages to the client
                String clientIp = clientSocket.getRemoteSocketAddress().toString(); // Get client IP address

                // Send a welcome message to the client
                pw.println("Welcome to the Chat Server, you are client number " + clientNumber);
                System.out.println("Connection from client number " + clientNumber + ", IP: " + clientIp);

                // Continuously handle messages from the client
                while (true) {
                    String message = br.readLine(); // Read client message
                    broadcastMessage("Client " + clientNumber + ": " + message, clientSocket); // Broadcast the message to other clients
                }
            } catch (IOException e) {
                System.err.println("Error in client handling: " + e.getMessage());
                e.printStackTrace(); // Handle exceptions during client interaction
            }
        }
    }
}
