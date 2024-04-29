package org.example.Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final Map<String, PrintWriter> clientWriters = new HashMap<>();
    private static final List<String> rooms = new ArrayList<>();
    private static final Map<String, List<String>> roomMembers = new HashMap<>();
    private static int roomCount = 0;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Chat server is running...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from " + clientSocket.getInetAddress().getHostAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Error in the server: " + e.getMessage());
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String nickname;
        private String currentRoom;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                writer = new PrintWriter(clientSocket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // 요구사항 1: 서버 연결 및 닉네임 설정
                writer.println("Welcome to the chat server! Please enter your nickname:");
                nickname = reader.readLine();
                System.out.println(nickname + " connected from " + clientSocket.getInetAddress().getHostAddress());
                writer.println("Hello, " + nickname + "!");

                broadcast(nickname + " has joined the chat.");

                String input;
                while ((input = reader.readLine()) != null) {
                    // 요구사항 2: 메시지 수신 및 발신
                    if (input.startsWith("/")) {
                        handleCommand(input);
                    } else {
                        if (currentRoom != null) {
                            broadcastRoom("[" + currentRoom + "] " + nickname + ": " + input);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error in handling client: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void handleCommand(String input) {
            if (input.startsWith("/")) {
                writer.println("Available commands: /list, /create, /join [room number], /exit, /bye");
            }

            if (input.startsWith("/create")) {
                roomCount++;
                String roomNumber = Integer.toString(roomCount);
                rooms.add(roomNumber);
                roomMembers.put(roomNumber, new ArrayList<>());
                writer.println("Room number " + roomNumber + " has been created.");
            }

            if (input.startsWith("/list")) {
                writer.println("Available rooms:");
                for (String room : rooms) {
                    writer.println(room);
                }
            }

            if (input.startsWith("/join")) {
                String[] parts = input.split(" ");
                if (parts.length == 2) {
                    String roomNumber = parts[1];
                    if (rooms.contains(roomNumber)) {
                        if (currentRoom != null) {
                            roomMembers.get(currentRoom).remove(nickname);
                            broadcastRoom(nickname + " has left the room.");
                        }
                        currentRoom = roomNumber;
                        roomMembers.get(roomNumber).add(nickname);
                        writer.println("You have joined room " + roomNumber);
                        broadcastRoom(nickname + " has joined the room.");
                    } else {
                        writer.println("Room " + roomNumber + " does not exist.");
                    }
                } else {
                    writer.println("Invalid command. Usage: /join [room number]");
                }
            }

            if (input.startsWith("/exit")) {
                if (currentRoom != null) {
                    roomMembers.get(currentRoom).remove(nickname);
                    broadcastRoom(nickname + " has left the room.");
                    currentRoom = null;
                }
                writer.println("You have exited the room and returned to lobby.");
            }

            if (input.startsWith("/bye")) {
                writer.println("Goodbye!");
                cleanup();
            }
        }

        private void cleanup() {
            try {
                if (nickname != null) {
                    clientWriters.remove(nickname);
                    if (currentRoom != null) {
                        roomMembers.get(currentRoom).remove(nickname);
                        if (roomMembers.get(currentRoom).isEmpty()) {
                            rooms.remove(currentRoom);
                            roomMembers.remove(currentRoom);
                            broadcast("Room " + currentRoom + " has been removed.");
                        } else {
                            broadcastRoom(nickname + " has left the room.");
                        }
                    }
                    broadcast(nickname + " has left the chat.");
                }
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                System.err.println("cleaning up client resources: " + e.getMessage());
            }
        }

        private void broadcast(String message) {
            for (PrintWriter writer : clientWriters.values()) {
                writer.println(message);
            }
        }

        private void broadcastRoom(String message) {
            if (currentRoom != null) {
                List<String> members = roomMembers.get(currentRoom);
                if (members != null) {
                    for (String member : members) {
                        PrintWriter memberWriter = clientWriters.get(member);
                        if (memberWriter != null) {
                            memberWriter.println(message);
                        }
                    }
                }
            }
        }
    }
}
