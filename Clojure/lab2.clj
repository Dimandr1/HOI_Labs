(ns lab2
    (:require [clojure.test :as test]))

(defn primes []
      (letfn [
              (enqueue [sieve n step]
                       (let [m (+ n step)]
                            (if (sieve m)
                              (recur sieve m step)
                              (assoc sieve m step))))

              (next-primes [sieve candidate]
                           (lazy-seq
                             (if (sieve candidate)
                               (next-primes (enqueue sieve candidate (sieve candidate)) (inc candidate))
                               (cons candidate
                                     (next-primes (enqueue sieve candidate candidate)
                                                  (inc candidate))))))]
             (next-primes {} 2)))



(test/deftest test-primes-list
              (test/testing "Тестирование списком"
                            (test/is (= (take 5 (primes))
                                        [2 3 5 7 11]))))

(test/deftest test-primes-nth
         (test/testing "Тестирование простых по индексу"
                  (test/is (= (nth (primes) 0) 2))
                  (test/is (= (nth (primes) 1) 3))
                  (test/is (= (nth (primes) 2) 5))
                  (test/is (= (nth (primes) 3) 7))
                  (test/is (= (nth (primes) 4) 11))
                  (test/is (= (nth (primes) 9) 29))
                  (test/is (= (nth (primes) 24) 97))
                  (test/is (= (nth (primes) 99) 541))
                  (test/is (= (nth (primes) 499) 3571))
                  ))

;(test/deftest test-primes-wrong
;              (test/testing "Тестирование ошибочное"
;                            (test/is (= (nth (primes) 123) 2))))
(test/run-tests 'lab2)
