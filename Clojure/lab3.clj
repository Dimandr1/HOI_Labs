(ns lab3
    (:require [clojure.test :as test]))


;; Собственно параллельный филтр
;; Задается ленивый список всех future'ов = futures
;; Они запускаются за счет drop и next
;; step генерирует по ним ленивую последовательность
(defn p-filter
      ([pred coll]
       (p-filter pred coll 20 10))
      ([pred coll chunk-size n]
          (let [parts (partition-all chunk-size coll)
             futures (map (fn [coll1]
                (future (doall (filter pred coll1)))) parts)
             step (fn step [[fut & more :as current] pending]
                    (lazy-seq
                     (if current
                       (concat (deref fut)
                               (step more (next pending))))))]
         (step futures (drop n futures)))
      )
)

;; Длительное сравнение
(defn heavy-even [x]
  (Thread/sleep 3)
  (even? x))

;;Оценка времени
(println "Test small orig:")
(time (println (take-last 1 (filter heavy-even (range 100)))))
(println "Test small parallel:")
(time (println (take-last 1 (p-filter heavy-even (range 100)))))

(println "_____________________")

(println "Test big orig:")
(time (println (take-last 1 (filter heavy-even (range 500)))))
(println "Test big parallel:")
(time (println (take-last 1 (p-filter heavy-even (range 500)))))

(println "_____________________")

(println "Test huge orig lazy:")
(time (println (take 42 (filter heavy-even (range 10000)))))
(println "Test huge parallel lazy:")
(time (println (take 42 (p-filter heavy-even (range 10000)))))

(println "_____________________")

(defn natural
	([] (natural 1))
	([n] (lazy-seq (cons n (natural (inc n))))))


(println "Test inf orig:")
(time (println (nth (filter heavy-even (natural)) 100)))
(println "Test inf parallel:")
(time (println (nth (p-filter heavy-even (natural)) 100)))
(println "_____________________")




;Всякие тесты
(test/deftest test-primes-list
              (test/testing "Тестирование фильтра на эквивалентность библиотечному"
                            (test/is (= (p-filter even? (range 1 10))
                                        (filter even? (range 1 10))))
                            (test/is (= (p-filter even? (range 9 1000))
                                        (filter even? (range 9 1000))))
                            (test/is (= (p-filter odd? (range 9 1000))
                                        (filter odd? (range 9 1000))))
                            (test/is (= (take 500 (p-filter even? (natural)))
                                        (take 500 (filter even? (natural)))))
                                        ))

;; ;; Тест чтобы убедиться, что тесты вообще могут не пройти
;; (test/deftest test-primes-wrong
;;               (test/testing "Тестирование ошибочное"
;;                            (test/is (= (nth (p-filter heavy-even (range 1 5)) 2) 3))))
(test/run-tests 'lab3)