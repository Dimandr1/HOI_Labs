package org.example;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Client {
    private int delaySec = 0;
    private boolean skipResponse = false;


    public static void main(String[] args) {
        Client client = new Client();
        client.start();
    }

    public void start() {
        System.out.println("Started");
        try (Scanner scanner = new Scanner(System.in)) {

            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                try{
                    processCommand(input);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        }
        System.out.println("The end");
    }

    private void processCommand(String command) throws Exception{
        String[] args = command.split("\\s+");

        if (args.length < 3) {
            throw new IllegalArgumentException("Not enough arguments (min 3)");
        }

        String name = args[0];
        String server = args[1];
        int port = Integer.parseInt(args[2]);

        parseOptions(args);

        connectToServer(name, server, port);
    }

    private void parseOptions(String[] parts) {
        delaySec = 0;
        skipResponse = false;

        for (int i = 3; i < parts.length; i++) {
            switch (parts[i]) {
                case "--delay":
                    if (i + 1 < parts.length) {
                        i++;
                        delaySec = Integer.parseInt(parts[i]);
                    }
                    break;
                case "--noresponse":
                    skipResponse = true;
                    break;
                default:
                    throw new IllegalArgumentException("Wrong option");
            }
        }
    }

    private void connectToServer(String name, String server, int port) throws Exception {
        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress(server, port))) {

            System.out.println("Connected to " + server + ":" + port);

            ByteBuffer request = ByteBuffer.allocate(name.length() + 1);
            request.put(name.getBytes(StandardCharsets.US_ASCII));
            request.put((byte) 0);
            request.flip();
            channel.write(request);
            System.out.println("Name sent");

            // Задержка перед чтением
            if (delaySec > 0) {
                System.out.println("Delay: " + delaySec);
                TimeUnit.SECONDS.sleep(delaySec);
            }

            // Не читаем ответ
            if (skipResponse) {
                System.out.println("Response skip");
                return;
            }



            // Чтение ответа
            ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
            while (sizeBuffer.hasRemaining()) channel.read(sizeBuffer);
            sizeBuffer.flip();
            int dataSize = sizeBuffer.getInt();

            ByteBuffer dataBuffer = ByteBuffer.allocate(dataSize);
            while (dataBuffer.hasRemaining()) channel.read(dataBuffer);

            String response = new String(dataBuffer.array(), StandardCharsets.UTF_8);

            // Парсинг PEM
            Map<String, String> pemContents = parsePEM(response);

            if (pemContents.containsKey("PRIVATE KEY")) {
                byte[] privateKeyBytes = Base64.getMimeDecoder().decode(pemContents.get("PRIVATE KEY"));
                PrivateKey privateKey = KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            }

            if (pemContents.containsKey("CERTIFICATE")) {
                byte[] certBytes = Base64.getMimeDecoder().decode(pemContents.get("CERTIFICATE"));
                X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(certBytes));
            }

            Files.writeString(Paths.get(name + ".key"), pemContents.get("BEGIN PRIVATE KEY"));
            Files.writeString(Paths.get(name + ".crt"), pemContents.get("BEGIN CERTIFICATE"));
            System.out.println("Keys saved");
        }  catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }
    private static Map<String, String> parsePEM(String pemData) {
        Map<String, String> result = new HashMap<>();
        String[] blocks = pemData.split("-----");

        for (int i = 1; i < blocks.length; i += 4) {
            if (i + 2 < blocks.length) {
                String type = blocks[i].trim();
                String base64Data = blocks[i + 1].replaceAll("\\s", "");
                result.put(type, base64Data);
            }
        }

        return result;
    }
}