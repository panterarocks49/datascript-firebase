(ns datascript-firebase.reactive
  (:require
   [reagent.core :as r]
   [reagent.ratom :as r-ratom]
   [datascript.core :as d]))


(defn listen!
  "registers a listener for the connection transactions. Returns
  the conn object itself with the added listener and state holder
  Subsequent usages of conn in q! and pull! will return reactive
  atoms that will update their value whenever the Datascript value
  changes"
  [conn]
  (when (nil? (::ratom @conn))
    (let [ratom (r/atom @conn)] ;; initial state
      (d/listen! conn
                 ::tx
                 (fn [{:as tx-report :keys [tx-data db-after]}]
                   (reset! ratom db-after)))
      ;; keep a reference to the ratom to avoid GC
      ;; could add this as meta data
      (swap! conn assoc ::ratom ratom)))
  ;; return the conn again to allow standard datascript usage
  conn)


(defn unlisten!
  "unregisters the transaction listener previously attached with
  listen!"
  [conn]
  (d/unlisten! conn ::tx)
  (swap! conn dissoc ::ratom))


(defn pull
  "same as datascript/pull but returns a ratom which will be updated
  every time that the value of conn changes"
  [conn selector eid]
  (r-ratom/make-reaction
   #(try
      (d/pull @(::ratom @conn) (conj selector :db/id) eid)
      (catch js/Error e
        {:db/id nil}))))


(defn q
  "Returns a reagent/atom with the result of the query.
  The value of the ratom will be automatically updated whenever
  a change is detected"
  [query conn & inputs]
  (r-ratom/make-reaction
   #(try
      (apply d/q query @(::ratom @conn) inputs)
      (catch js/Error e
        nil))))


(defn datoms
  [conn index & args]
  (r-ratom/make-reaction
   #(try
      (mapv vec
            (apply d/datoms @(::ratom @conn) index args))
      (catch js/Error e
        nil))
   ))

(defn f-entity
  "f gets the f-entity"
  [conn f eid & args]
  (r-ratom/make-reaction
   #(try
      (apply f (d/entity @(::ratom @conn) eid) args)
      (catch js/Error e
        nil))))




