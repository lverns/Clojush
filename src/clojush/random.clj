(ns clojush.random
  (:use [clojush globals translate])
  (:require [clj-random.core :as random]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; random functions

(def ^:dynamic *thread-local-random-generator* (random/make-mersennetwister-rng))

(def lrand-int random/lrand-int)

(def lrand random/lrand)

(def lrand-nth random/lrand-nth)

(def lshuffle random/lshuffle)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; random plush genome generator

(defn random-closes
  "Returns a random number of closes based on close-parens-probabilities, which
   defaults to [0.772 0.206 0.021 0.001]. This is roughly equivalent to each selection
   coming from  a binomial distribution with n=4 and p=1/16.
      (see http://www.wolframalpha.com/input/?i=binomial+distribution+4+0.0625)
   This results in the following probabilities:
     p(0) = 0.772
     p(1) = 0.206
     p(2) = 0.021
     p(3) = 0.001"
  [close-parens-probabilities]
  (let [prob (lrand)]
    (loop [parens 0
           probabilities (concat (reductions + close-parens-probabilities)
                                 '(1.0))]
      (if (<= prob (first probabilities))
        parens
        (recur (inc parens)
               (rest probabilities))))))

(defn conditional-thread
  "Takes a value and threads it through the functions. If a function
   returns nil, the old value will be the value passed to the next
   function."
  [val & fs]
  (reduce (fn [value f]
              (if-let [new-value (f value)]
                new-value
                value))
          val fs))
;; Example usage of conditional-thread (the function)
;; (conditional-thread 0 [inc inc inc])
;; => 3
;; (conditional-thread 0 [#(when (= 0 %) 2)
;;                        inc
;;                        inc])
;; => 4
;; (conditional-thread 0 [#(when (= 1 %) 2)
;;                        inc
;;                        inc])
;; => 2

;; (defmacro conditional-thread
;;   "This macro acts a lot like ->, but is conditional. val can be
;;    any value. clauses should be of the form (boolean1 f1 boolean2 f2 ...
;;    This will transform to (... (if boolean2 (f2 (if boolean1 (f1 val) val)) (if boolean1 (f1 val) val))"
;;   ([val] val)
;;   ([val clauses]
;;    (if clauses
;;      (list 'if (first clauses)
;;            (if (next clauses)
;;              `(conditional-thread (~(second clauses) ~val) ~(next (next clauses)))
;;              (throw (IllegalArgumentException.
;;                      "The second argument to conditional-thrush must be a list with an even number of forms")))
;;            `(conditional-thread ~val ~(next (next clauses))))
;;      val)))

(defn random-plush-instruction-map
  "Returns a random instruction map given the atom-generators and the required
   epigenetic-markers."
  ([atom-generators]
   (random-plush-instruction-map atom-generators {}))
  ([atom-generators argmap]
   (random-plush-instruction-map atom-generators false argmap))
  ([atom-generators random-insertion {:keys [epigenetic-markers
                                             close-parens-probabilities
                                             silent-instruction-probability]
                                      :or {epigenetic-markers []
                                           close-parens-probabilities [0.772 0.206 0.021 0.001]
                                           silent-instruction-probability 0}}]
   (let [markers (concat epigenetic-markers
                         [:instruction :uuid]
                         (if random-insertion [:random-insertion]))]
     (zipmap markers
             (map (fn [marker]
                    (case marker
                      :instruction (let [element (lrand-nth atom-generators)]
                                     (if (fn? element)
                                       (let [fn-element (element)]
                                         (if (fn? fn-element)
                                           (fn-element)
                                           fn-element))
                                       element))
                      :close (random-closes close-parens-probabilities)
                      :silent (if (< (lrand) silent-instruction-probability)
                                true
                                false)
                      :random-insertion true
                      :uuid (java.util.UUID/randomUUID)
                      ))
                  markers)))))

(defn random-plush-genome-with-size
  "Returns a random Plush genome containing the given number of points."
  ([genome-size atom-generators argmap]
   (random-plush-genome-with-size genome-size atom-generators false argmap))
  ([genome-size atom-generators random-insertion argmap]
   (repeatedly genome-size
               #(random-plush-instruction-map
                 atom-generators
                 random-insertion
                 argmap))))

(defn random-plush-genome
  "Returns a random Plush genome with size limited by max-genome-size."
  ([max-genome-size atom-generators]
    (random-plush-genome max-genome-size atom-generators {}))
  ([max-genome-size atom-generators argmap]
   (random-plush-genome max-genome-size atom-generators false argmap))
  ([max-genome-size atom-generators random-insertion argmap]
    (random-plush-genome-with-size (inc (lrand-int max-genome-size))
                           atom-generators
                           random-insertion
                           argmap)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; random Push code generator

(defn random-push-code
  "Returns a random Push expression with size limited by max-points."
  ([max-points atom-generators]
    (random-push-code max-points atom-generators {:max-points @global-max-points}))
  ([max-points atom-generators argmap]
    (translate-plush-genome-to-push-program
      {:genome (random-plush-genome (max (int (/ max-points 4)) 1)
                                    atom-generators
                                    argmap)}
      argmap)))
