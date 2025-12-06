(ns lab1)

;Функция для получения всех перестановок по условию задачи
; n-1 раз каждая строка из нашего "результирующего" списка
; преобразуется в список удовлетворяющих условию строк путем добавления в её начало символов из алфавита
; Затем эти списки объединяются в один и идут на следующую итерацию
(defn permutations [chars n]
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

; Просмотр результата
(println (permutations '("a" "b" "c" "d") 2 ))
