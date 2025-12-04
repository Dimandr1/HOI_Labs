(ns lab1)

(defn c1 [chars n]
      (let [acc chars]
      (reduce
        (fn [lst i]
            (reduce concat '()
                      (map
                        (fn [st]
                            (map
                              (fn [ch] (str ch st))
                              (filter (fn [x] (not= (str (first st)) x)) chars)))
                        lst)))
              acc
              (range 1 n)
        )))

(println (c1 '("a" "b" "c" "d") 2 ))
