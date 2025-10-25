package org.example;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.FileOutputStream;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class CAGenerator {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) {
        String keyFile = "main.key";
        String format = "PEM";
        int keySize = 8192;

        try {
            generateCAKey(keyFile, format, keySize);
        } catch (Exception e) {
            System.err.println("Error generating CA key: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void generateCAKey(String keyFile, String format, int keySize) throws Exception {
        // Генерация пары ключей RSA
        System.out.println("Generating RSA " + keySize + "-bit key pair...");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(keySize);
        KeyPair caKeyPair = keyGen.generateKeyPair();

        // Сохранение в нужном формате
        saveAsPEM(keyFile, caKeyPair.getPrivate());
        }

    private static void saveAsPEM(String filename, PrivateKey privateKey) throws Exception {
        // Кодируем приватный ключ в PEM формате
        String pemContent = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privateKey.getEncoded()) +
                "\n-----END PRIVATE KEY-----\n";

        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(pemContent.getBytes());
        }
    }
}
