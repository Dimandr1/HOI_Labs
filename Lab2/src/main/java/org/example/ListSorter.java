package org.example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

//Функции для работы с обычным списком хранятся здесь, для работы в собственной реализацией - в другом файле
public class ListSorter {

    static ArrayList<Thread> threads;
    static boolean running = true;
    static boolean useArrayList;
    static BidirectedSortingList myList;
    static List<String> defList;
    static int defListSteps = 0;
    static int stepInterval;
    public static void main(String[] args) {
        //Ввод параметров и указание типа исследуемого списка
        System.out.println("Start arguments: <sortingThreads> <stepInterval> <in-stepDelay>");
        System.out.println("Example:\n3 0 2");
        Scanner scanner = new Scanner(System.in);
        String startArgs = scanner.nextLine().trim();
        String[] argsAr = startArgs.split("\\s+");
        if(argsAr.length < 3){
            throw new RuntimeException("Not enough arguments (need 3)");
        }
        int sortingThreads = Integer.parseInt(argsAr[0]);
        stepInterval = Integer.parseInt(argsAr[1]);
        int stepDelay = Integer.parseInt(argsAr[2]);
        if(sortingThreads < 1 || stepInterval < 0 || stepDelay < 0){
            throw new IllegalArgumentException("wrong arguments");
        }
        System.out.println("Print something if you want to use ArrayList or put empty string otherwise");
        String askList = scanner.nextLine().trim();
        if(askList.isEmpty()){
            System.out.println("My list selected!");
            useArrayList = false;
            myList = new BidirectedSortingList(stepInterval, stepDelay);
        }
        else{
            System.out.println("Default list selected!");
            useArrayList = true;
            defList = Collections.synchronizedList(new ArrayList<String>());
        }

        //Запуск потоков
        threads = new ArrayList<>();

        for(int i = 0; i < sortingThreads; i++){
            SortingThread thread = new SortingThread(i);
            threads.add(thread);
            thread.start();
        }

        //Цикл взаимодействия с пользователем
        System.out.println("Now you can print a string to add into the list or an empty input to print the list:");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if(line.isEmpty()){
                if(useArrayList){
                    printDefList();
                }
                else {
                    myList.printList();
                }
            }
            else{
                if(useArrayList){
                    defList.addFirst(line);
                    System.out.println("Added!");
                }
                else {
                    myList.addNode(line);
                    System.out.println("Added!");
                }
            }
        }
        System.out.println("ending");
        stop();
    }

    //Поток, отвечающий за сортировку списка
    private static class SortingThread extends Thread {
        public SortingThread(int id) {
            super("SortingThread" + id);
        }

        @Override
        public void run() {
            //Регулярно запускает обходы списка пузырьком
            while (running) {
                if(useArrayList){
                    defListSortTurn();
                }
                else {
                    myList.bubbleSortTurn();
                }
            }
        }

        //Фукнция для одного пузырькового обхода для ArrayList
        public void defListSortTurn(){
            for(int i = 0; i < defList.size()-1; i++){
                synchronized (defList) {
                    String cur = defList.get(i);
                    String next = defList.get(i + 1);

                    if (cur.compareTo(next) > 0) {
                        defListSteps++;
                        defList.set(i, next);
                        defList.set(i + 1, cur);
                    }
                }
                if (stepInterval > 0) {
                    try {
                        TimeUnit.SECONDS.sleep(stepInterval);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

    }
    //Функция для вывода классического списка
    public static void printDefList(){
        if(useArrayList){
            System.out.println("__________________________\nSorting steps on arrayList: " + defListSteps);
            for(int i = 0; i < defList.size(); i++){
                System.out.println(i + ": " + defList.get(i));
            }
            System.out.println("__________________________");
        }
        else {
            myList.printList();
        }
    }

    public static void stop() {
        running = false;
        for (Thread thread : threads) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                // Игнорируем
            }
        }
    }

}
