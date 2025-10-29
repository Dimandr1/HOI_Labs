package org.example;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.bouncycastle.asn1.x500.*;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.*;
import org.bouncycastle.operator.jcajce.*;


//Основной класс сервера
public class KeyGenerationService {
    private final int port;
    private final int keyGeneratorThreads;
    private final String issuerName;
    private final PrivateKey caPrivateKey;

    private ServerSocketChannel serverChannel;
    private Selector selector;

    // Пул потоков для генерации ключей
    private final List<KeyGeneratorThread> keyGeneratorThreadsList = new ArrayList<>();
    private final BlockingQueue<KeyGenerationTask> keyGenerationQueue = new LinkedBlockingQueue<>();

    // Поток для отправки ответов
    private ResponseThread responseThread;
    private final BlockingQueue<ClientResponse> responseQueue = new LinkedBlockingQueue<>();

    private volatile boolean running = true;

    private final Map<String, KeyPairWithCertificate> keyCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> pendingRequests = new ConcurrentHashMap<>();


    public static void main(String[] args) {
        //Введение параметров для запуска сервера
        System.out.println("Started. Print port and generator threads amount");
        System.out.println("AND DON'T FORGET TO GENERATE SIGNING KEY BEFORE START");
        System.out.println("Example:\n8080 2");
        String[] params;
        try (Scanner scanner = new Scanner(System.in)) {
            String input = scanner.nextLine().trim();
            params = input.split("\\s+");
        }
        try {
            int port = Integer.parseInt(params[0]);
            int keygenThreads = Integer.parseInt(params[1]);
            String issuerName = new String("CN=Cringe");
            //String issuerName = params[2];
            String caKeyFile = "main.key"; //ключ заранее сгенерирован классом CAGenerator

            PrivateKey caPrivateKey = loadKey(caKeyFile);

            KeyGenerationService service = new KeyGenerationService(
                    port, keygenThreads, issuerName, caPrivateKey);

            Runtime.getRuntime().addShutdownHook(new Thread(service::stop));
            service.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public KeyGenerationService(int port, int keyGeneratorThreads, String issuerName, PrivateKey caPrivateKey) {
        this.port = port;
        this.keyGeneratorThreads = keyGeneratorThreads;
        this.issuerName = issuerName;
        this.caPrivateKey = caPrivateKey;
    }

    public void start() throws IOException {
        //запуск работы потоков
        //выделяется 1 поток под добавление новых подключений и обработку команд от старых
        //Этим потоком является стартовый поток
        //еще 1 поток под отправку ответов на команды клиентов
        //и еще потоки под генерацию, количество которых задается при старте
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(port));

        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Key Generation Service started on port " + port);
        System.out.println("Using " + keyGeneratorThreads + " key generator threads");

        // Запуск потоков генерации ключей
        for (int i = 0; i < keyGeneratorThreads; i++) {
            KeyGeneratorThread thread = new KeyGeneratorThread("KeyGenerator-" + i);
            thread.start();
            keyGeneratorThreadsList.add(thread);
        }

        // Запуск потока для отправки ответов
        responseThread = new ResponseThread();
        responseThread.start();

        // Основной цикл обработки соединений
        eventLoop();
    }

    //Функция ожидания ввода для считывающего потока
    private void eventLoop() {
        try {
            while (running) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        acceptConnection(key);
                    } else if (key.isReadable()) {
                        readRequest(key);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Обработка запроса на подключение к серверу
    private void acceptConnection(SelectionKey key) throws IOException {
        SocketChannel clientChannel = ((ServerSocketChannel) key.channel()).accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ, new ClientSession());

        System.out.println("Accepted connection from: " + clientChannel.getRemoteAddress());
    }

    //Обработка присланной клиентом команды (считывание имени)
    private void readRequest(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = channel.read(buffer);

        if (bytesRead == -1) {
            channel.close();
            return;
        }

        // Поиск нулевого байта (конец имени)
        buffer.flip();
        int nullPosition = -1;
        for (int i = buffer.position(); i < buffer.limit(); i++) {
            if (buffer.get(i) == 0) {
                nullPosition = i;
                break;
            }
        }

        if (nullPosition != -1) {
            // Имя полностью получено
            byte[] nameBytes = new byte[nullPosition - buffer.position()];
            buffer.get(nameBytes);
            buffer.get(); // Пропустить нулевой байт

            String clientName = new String(nameBytes);

            // Очистить буфер для следующего запроса
            buffer.compact();

            System.out.println("Received request for name: " + clientName);

            // Обработка запроса
            handleKeyRequest(clientName, channel, session);
        } else {
            // Имя еще не полностью получено, продолжить чтение
            buffer.compact();
        }
    }

    //Обработка команды по считанному имени (создание запроса на генерацию или сразу добавление в очередь на отправку клиенту)
    private void handleKeyRequest(String clientName, SocketChannel channel, ClientSession session) {
        // Проверка наличия вычисленных ключей
        KeyPairWithCertificate cached = keyCache.get(clientName);
        if (cached != null) {
            queueResponse(channel, cached);
            return;
        }

        // Проверка на повторяющийся запрос
        AtomicInteger pendingCount = pendingRequests.computeIfAbsent(
                clientName, k -> new AtomicInteger(0));

        int currentPending = pendingCount.incrementAndGet();

        if (currentPending == 1) {
            // Первый запрос для этого имени - начать генерацию
            KeyGenerationTask task = new KeyGenerationTask(clientName, channel, session);
            try {
                keyGenerationQueue.put(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            // Повторный запрос - просто добавить в ожидание
            //все запросы из очереди ожидания будут обработаны при генерации ключа
            session.addPendingRequest(clientName);
            System.out.println("Duplicate request for name: " + clientName +
                    " (pending: " + currentPending + ")");
        }
    }

    //Обработка запроса на генерацию ключа
    private void generateKeys(String clientName, SocketChannel channel, ClientSession session) {
        try {
            System.out.println("Generating keys for: " + clientName);

            // Генерация пары ключей RSA 8192
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(8192);
            KeyPair keyPair = keyGen.generateKeyPair();

            // Создание сертификата
            X509Certificate certificate = createCertificate(clientName, keyPair);

            KeyPairWithCertificate result = new KeyPairWithCertificate(keyPair, certificate);

            // Сохранение в кэш
            keyCache.put(clientName, result);

            System.out.println("Key generation completed for: " + clientName);

            // Отправка результата первоначальному запросу
            queueResponse(channel, result);

            // Обработка ожидающих запросов для этого имени
            handlePendingRequests(clientName, result);

        } catch (Exception e) {
            e.printStackTrace();
            // В реальной системе здесь должна быть обработка ошибок
        } finally {
            pendingRequests.remove(clientName);
        }
    }

    private void handlePendingRequests(String clientName, KeyPairWithCertificate result) {
        // Находим все сессии, которые ожидают этот результат
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.attachment() instanceof ClientSession) {
                ClientSession sess = (ClientSession) key.attachment();
                if (sess.hasPendingRequest(clientName)) {
                    SocketChannel chan = (SocketChannel) key.channel();
                    queueResponse(chan, result);
                    sess.removePendingRequest(clientName);
                }
            }
        }
    }

    private X509Certificate createCertificate(String subjectName, KeyPair keyPair) throws Exception {
        // Используем Bouncy Castle для создания сертификата
        X500Name issuer = new X500Name(issuerName);
        X500Name subject = new X500Name("CN=" + subjectName);

        Date startDate = new Date();
        Date endDate = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000); // 1 год

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(caPrivateKey); //подпись будет загруженным предварительно ключом

        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.currentTimeMillis()),
                startDate,
                endDate,
                subject,
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded())
        );

        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    //Добавление запроса на отправку ключа пользователю
    private void queueResponse(SocketChannel channel,
                               KeyPairWithCertificate result) {
        try {
            responseQueue.put(new ClientResponse(channel, result));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    //Собственно отправка ключей пользователю
    private void sendResponse(ClientResponse response) {
        try {
            SocketChannel channel = response.channel;
            KeyPairWithCertificate keyData = response.keyData;

            // Создаем ответ в текстовом PEM формате (совместим со всеми языками)
            String responseText = createPEMResponse(keyData);
            byte[] responseData = responseText.getBytes("UTF-8");

            // Отправка
            ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
            sizeBuffer.putInt(responseData.length);
            sizeBuffer.flip();

            ByteBuffer dataBuffer = ByteBuffer.wrap(responseData);

            while (sizeBuffer.hasRemaining()) channel.write(sizeBuffer);
            while (dataBuffer.hasRemaining()) channel.write(dataBuffer);

            System.out.println("Response sent for: " + keyData.certificate.getSubjectX500Principal().getName());

        } catch (ClosedChannelException e) {
            System.out.println("Couldn't send response due to socket closing for " +
                    response.keyData.certificate.getSubjectX500Principal().getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;

        try {
            if (serverChannel != null) serverChannel.close();
            if (selector != null) selector.close();

            for (KeyGeneratorThread thread : keyGeneratorThreadsList) {
                thread.interrupt();
            }


            if (responseThread != null) {
                responseThread.interrupt();
            }

            // Ожидание завершения потоков
            for (KeyGeneratorThread thread : keyGeneratorThreadsList) {
                try {
                    thread.join(5000);
                } catch (InterruptedException e) {
                    // Игнорируем
                }
            }

            if (responseThread != null) {
                try {
                    responseThread.join(5000);
                } catch (InterruptedException e) {
                    // Игнорируем
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //Класс потока для генерации ключей
    private class KeyGeneratorThread extends Thread {
        public KeyGeneratorThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (!isInterrupted() && running) {
                try {
                    KeyGenerationTask task = keyGenerationQueue.take();
                    generateKeys(task.clientName, task.channel, task.session);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println(getName() + " stopped");
        }
    }

    // Класс потока для отправки ответов
    private class ResponseThread extends Thread {
        public ResponseThread() {
            super("ResponseThread");
        }

        @Override
        public void run() {
            while (!isInterrupted() && running) {
                try {
                    ClientResponse response = responseQueue.take();
                    sendResponse(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("ResponseThread stopped");
        }

    }
    //Сериализация ключей в подходящий для передачи вид
    private String createPEMResponse(KeyPairWithCertificate keyData) throws Exception {
        StringBuilder sb = new StringBuilder();

        // Приватный ключ в PEM
        sb.append("-----BEGIN PRIVATE KEY-----\n");
        sb.append(Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(
                keyData.keyPair.getPrivate().getEncoded()));
        sb.append("\n-----END PRIVATE KEY-----\n");

        // Сертификат в PEM
        sb.append("-----BEGIN CERTIFICATE-----\n");
        sb.append(Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(
                keyData.certificate.getEncoded()));
        sb.append("\n-----END CERTIFICATE-----\n");

        return sb.toString();
    }

    //Функция для загрузки из памяти ключа (для подписи)
    private static PrivateKey loadKey(String filename) throws Exception {
        String pemContent = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filename)));
        // Удаляем заголовки и разбираем Base64
        String base64Content = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64Content);
        // Создаем приватный ключ из байтов
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return keyFactory.generatePrivate(keySpec);
    }

    // Класс для представления задачи генерации ключей
    private static class KeyGenerationTask {
        final String clientName;
        final SocketChannel channel;
        final ClientSession session;

        KeyGenerationTask(String clientName, SocketChannel channel, ClientSession session) {
            this.clientName = clientName;
            this.channel = channel;
            this.session = session;
        }
    }


    // Класс для хранения сессии клиента
    private class ClientSession {
        private final Set<String> pendingRequests = ConcurrentHashMap.newKeySet();


        public void addPendingRequest(String clientName) {
            pendingRequests.add(clientName);
        }

        public void removePendingRequest(String clientName) {
            pendingRequests.remove(clientName);
        }

        public boolean hasPendingRequest(String clientName) {
            return pendingRequests.contains(clientName);
        }
    }

    // Класс для хранения пары ключей и сертификата
    private class KeyPairWithCertificate implements Serializable {
        private final KeyPair keyPair;
        private final X509Certificate certificate;

        public KeyPairWithCertificate(KeyPair keyPair, X509Certificate certificate) {
            this.keyPair = keyPair;
            this.certificate = certificate;
        }
    }

    // Класс для представления ответа клиенту
    private class ClientResponse {
        private final SocketChannel channel;
        private final KeyPairWithCertificate keyData;

        public ClientResponse(SocketChannel channel,  KeyPairWithCertificate keyData) {
            this.channel = channel;
            this.keyData = keyData;
        }
    }
}