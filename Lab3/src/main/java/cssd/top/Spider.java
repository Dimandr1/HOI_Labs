package cssd.top;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

public class Spider {
    private ConcurrentLinkedQueue<String> urlQueue;
    private HashMap<String, String> urlMap;
    private HttpClient httpClient;
    private String baseUrl;
    private int activeThreads = 0;

    public static void main(String[] args) throws InterruptedException {
        String baseUrl = "http://localhost:8080/";
        System.out.println("Write url to connect. It MUST end with \"/\"");
        System.out.println("OR put empty string for default value");
        System.out.println("example:\nhttp://localhost:8080/");
        try (Scanner scanner = new Scanner(System.in)) {
            String input = scanner.nextLine().trim();
            if(!input.isBlank()) {
                baseUrl = input;
            }
        }

        System.out.println("Starting");
        Spider crawler = new Spider(baseUrl);

        crawler.start();
    }

    public Spider(String baseUrl) {
        this.baseUrl = baseUrl;
        httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
        urlQueue = new ConcurrentLinkedQueue<>();
        urlMap = new HashMap<>();
    }

    //Работа основного потока
    public void start() throws InterruptedException {
        long startTime = System.currentTimeMillis();

        //Добавляем первый адрес в очередь
        //Затем в цикле запускаем виртуальный поток на обработку первого адреса из очереди
        //После получения ответа от сервера поток-обработчик добавит все дальнейшие адреса в очередь
        urlQueue.offer("");
        String curUrl = urlQueue.poll();

        while (curUrl != null || activeThreads > 0) {
            if (curUrl == null) {//Дожидаемся, пока отработают все виртуальные потоки
                Thread.sleep(100);
            }
            else {//Запускаем обработку адреса из очереди
                String finalCurrentUrl = curUrl;
                activeThreads++;
                Thread.startVirtualThread(() -> processUrl(finalCurrentUrl));
            }
            curUrl = urlQueue.poll();
        }

        //Завершаем выводом небольшой статистики и всех сообщений по условию
        long endTime = System.currentTimeMillis();
        printSortedMessages();
        System.out.println("Total messages: " + urlMap.size());
        System.out.println("Program completed in " + (endTime - startTime) / 1000.0 + " seconds");
    }

    public void processUrl(String urlPath) {
            String fullUrl = baseUrl + urlPath;
            synchronized (urlMap){
                //Наличие пустой строки по ключу в мапе отражает, что этот ключ сейчас обрабатывается
                //Если же строка непустая, то он уже обработан, а сообщение получено
                //В любом случае если ключ есть, то смысла еще раз запускать обработку нет
                if(!urlMap.containsKey(fullUrl)){
                    urlMap.put(fullUrl, "");
                }
                else{
                    return;
                }
            }
            System.out.println("Requesting: " + fullUrl);

        try {
            //Отправляем запрос, обрабатываем ответ, пополняем очередь при необходимости
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();
            String responseBody = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();

            ObjectMapper mapper = new ObjectMapper();
            ServerResponse serverResponse = mapper.readValue(responseBody, ServerResponse.class);
            synchronized (urlMap) {
                urlMap.put(fullUrl, serverResponse.message);
            }
            System.out.println("Successfully processed: " + urlPath + " -> " + serverResponse.message);

            System.out.println("Successors for " + urlPath + ": " + Arrays.toString(serverResponse.successors));
            for (String successor : serverResponse.successors) {
                synchronized (urlMap) {
                    if (!urlMap.containsKey(urlPath)) {
                        urlQueue.offer(successor);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error while processing: " + fullUrl);
            System.err.println(e);
        } finally {
            activeThreads--;
            //Завершаем "активность" потока
        }
    }

    //Сортировка и вывод всех сообщений
    public void printSortedMessages() {
        ArrayList<String> messages = new ArrayList<>(urlMap.values());
        Collections.sort(messages);
        System.out.println("Sorted response messages:");
        for (String message : messages) {
            System.out.println(message);
        }
    }

    //Структура для хранения ответа сервера в удобном виде
    public static class ServerResponse {
        public String message;
        public String[] successors;
    }
}
