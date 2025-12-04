(defn sieve [n]
      (loop [last-tried 2 sift (range 2 (inc n))]
            (if
              (or (nil? last-tried) (> last-tried n))
              sift
              (let [filtered (doall (filter #(or (= % last-tried) (< 0 (rem % last-tried))) sift))]
                   (let [next-to-try (first (doall (filter #(> % last-tried) filtered)))]
                        (recur next-to-try filtered))))))

(println (sieve 100000))
