package org.example;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BidirectedSortingList implements Iterable<String>{
    private ListNode firstNode;
    private final int stepInterval;
    private final int stepDelay;
    private final Lock firstLock = new ReentrantLock(); // Эта блокировка используется для доступа к first node
    //Заметил, что можно было вместо неё использовать блок synchronized, но уже не хочется переписывать
    private final Object iterLock = new Object();
    private int sortSteps;
    public BidirectedSortingList(int stepInterval, int stepDelay){
        firstNode = null;
        this.stepInterval = stepInterval;
        this.stepDelay = stepDelay;
        sortSteps = 0;
    }

    //Добавление новой строки в начало списка
    public void addNode(String value){
        ListNode newNode;
        firstLock.lock();
        if(firstNode == null){
            newNode = new ListNode(null, value);
        }
        else{
            firstNode.lock.lock();
            newNode = firstNode.insertNextNode(value);
            firstNode.lock.unlock();
        }
        this.firstNode = newNode;
        firstLock.unlock();
    }

    //Выводит текущее (насколько возможно) состояние списка в красивом виде используя итератор
    public void printList(){
        synchronized (iterLock) {
            System.out.println("______________________________");
            System.out.println("Sorting steps: " + sortSteps);
            int c = 1;
            for (String value : this) {
                System.out.println(c + " - " + value);
                c++;
            }
            System.out.println("______________________________");
        }
    }

    //Меняет текущий блок с следующим, переставляя всё необходимое
    //Все локи должны быть выполнены вне функции
    private void swapNodeWithNext(ListNode node){
        if(node.nextNode == null){
            throw new IllegalArgumentException("No next node!");
        }

        ListNode next = node.nextNode;
        if(firstNode == node){
            firstNode = next;
        }
        if(node.prevNode != null){
            node.prevNode.nextNode = next;
        }
        if(next.nextNode != null) {
            next.nextNode.prevNode = node;
        }
        next.prevNode = node.prevNode;
        node.nextNode = next.nextNode;

        try { // При необходимости - задержка "внутри" шага.
            TimeUnit.SECONDS.sleep(stepDelay);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        next.nextNode = node;
        node.prevNode = next;
        sortSteps++;
    }

    //Пройти от начала списка, меняя местами блоки при необходимости
    public void bubbleSortTurn(){
        boolean swapped = false;
        ArrayList<Lock> locks = new ArrayList<>();

        checkForIter(); //Проход пузырька должен останавливаться во время итерирования, и данный вызов это проверяет

        //Первая итерация обрабатывается отдельно, т.к. первый элемент - единственный, не имеющий предыдущего
        // (и требующий firstLock)
        firstLock.lock();
        ListNode cur = firstNode;
        //Для корректности выполнения обмена элементов требуется заблокировать не только этот и следующий блоки
        //Но и предыдущий п идущий после следующего (итого 4)
        if(cur == null){
            firstLock.unlock();
            return;
        }
        cur.lock.lock();
        locks.add(cur.lock);
        if(cur.nextNode == null){
            for(Lock lock : locks){lock.unlock();}
            firstLock.unlock();
            return;
        }
        cur.nextNode.lock.lock();
        locks.add(cur.nextNode.lock);
        if(cur.nextNode.nextNode != null){
            cur.nextNode.nextNode.lock.lock();
            locks.add(cur.nextNode.nextNode.lock);
        }
        if(cur.value.compareTo(cur.nextNode.value) > 0){
            swapNodeWithNext(cur);
            cur = cur.prevNode;
            swapped = true;
        }
        //Завершая осмотр блока, освобождаем все локи
        for(Lock lock : locks){lock.unlock();}
        locks.clear();
        firstLock.unlock();
        if(stepInterval > 0 && swapped){ //При наличии задержек между шагами выводим список (и ждем, конечно)
            printList();
            swapped = false;
            try {
                TimeUnit.SECONDS.sleep(stepInterval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        //Дальше то же самое, но теперь блокируем по 4 блока (кроме последней итерации)
        while(cur.nextNode != null && cur.nextNode.nextNode != null){
            checkForIter();
            ListNode t = cur;
            for(int i = 0; i < 4 && t.nextNode != null; i++){
                t.lock.lock();
                locks.add(t.lock);
                t = t.nextNode;
            }
            if(cur.nextNode.value.compareTo(cur.nextNode.nextNode.value) > 0){
                swapNodeWithNext(cur.nextNode);
                swapped = true;

            }
            else {
                cur = cur.nextNode;
            }
            for(Lock lock : locks){lock.unlock();}
            locks.clear();
            if(stepInterval > 0 && swapped){
                printList();
                try {
                    TimeUnit.SECONDS.sleep(stepInterval);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                swapped = false;
            }
        }
    }

    //Функцию используют сортирующие потоки для проверки, что сейчас не проводится итерация и они могут дальше работать
    public void checkForIter(){
        synchronized (iterLock){}
    }

    //Замена итератора
    @Override
    public Iterator<String> iterator() {
        return new BidirectedListIterator();
    }

    //Класс "блока" списка.
    private class ListNode{
        public ListNode nextNode;
        public ListNode prevNode;
        public String value;
        private final Lock lock = new ReentrantLock();

        public ListNode(ListNode next, String value){
            this.nextNode = next;
            this.prevNode = null;
            this.value = value;
        }

        public ListNode insertNextNode(String value){
            ListNode next = new ListNode(this, value);
            this.prevNode = next;
            return next;
        }
    }

    private class BidirectedListIterator implements Iterator<String> {
        private ListNode current;
        private boolean firstLocked;
        public BidirectedListIterator(){
            firstLock.lock();
            firstLocked = true;
            current = firstNode;
            current.lock.lock();
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public String next() {
            if(firstLocked){
                firstLock.unlock();
                firstLocked = false;
            }
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String value = current.value;
            if(current.nextNode != null){
                current.nextNode.lock.lock();
            }
            current.lock.unlock();
            current = current.nextNode;

            return value;
        }
    }
}
