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
    private final Lock firstLock = new ReentrantLock();
    private final Object iterLock = new Object();
    private int sortSteps;
    public BidirectedSortingList(int stepInterval, int stepDelay){
        firstNode = null;
        this.stepInterval = stepInterval;
        this.stepDelay = stepDelay;
        sortSteps = 0;
    }

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

        try {
            TimeUnit.SECONDS.sleep(stepDelay);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        next.nextNode = node;
        node.prevNode = next;
        sortSteps++;
    }

    public void bubbleSortTurn(){
        boolean swapped = false;
        ListNode currentNode;
        ArrayList<Lock> locks = new ArrayList<>();

        checkForIter();

        firstLock.lock();
        ListNode cur = firstNode;
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
        for(Lock lock : locks){lock.unlock();}
        locks.clear();
        firstLock.unlock();
        if(stepInterval > 0 && swapped){
            printList();
            swapped = false;
            try {
                TimeUnit.SECONDS.sleep(stepInterval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

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

    public void checkForIter(){
        synchronized (iterLock){}
    }
    @Override
    public Iterator<String> iterator() {
        return new BidirectedListIterator();
    }

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
