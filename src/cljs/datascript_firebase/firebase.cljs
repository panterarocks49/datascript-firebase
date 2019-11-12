(ns datascript-firebase.firebase
  (:require
   ["firebase/app" :as firebase]
   ;; because it still needs to be included
   ;; ["firebase/auth"]
   ["firebase/database"]
   [goog.object :as gobj]
   ;; [clojure.pprint :refer [pprint]]
   [cljs.core.async :as async]
   [reagent.core :as r]
   [datascript.core :as d]
   [datascript.transit :as dt]
   [taoensso.timbre :as log]
   [clojure.string :as str]
   [datascript-firebase.reactive :as dr]
   )
  (:require-macros
   [cljs.core.async.macros :as async-macros]))

(defn db-ref [path]
  (.ref (firebase/database) (str/join "/" path)))

(defn save [path value]
  (.set (db-ref path) value))

(defn push [path value]
  (.push (db-ref path) value))


(defn read-string
  [s]
  (dt/read-transit-str s)
  )

(defn write-string
  [x]
  (dt/write-transit-str x)
  )

(defonce session-id (gobj/get (random-uuid) "uuid"))

(defn save-snapshot
  [path k db]
  (save path
        (write-string {:snapshot-key k
                       ;; this says conn, but its actually the db
                       :conn         db}))
  )


(defn record-db-after-on-tx
  [path parsed-val txreturn]
  (try
    (save (conj path "after")
          (write-string (dissoc txreturn :db-before :db-after :tx-meta)))
    (catch js/Error e
      (log/error e))))



(defn transact!
  ([path tx]
   (transact! path tx nil))
  ([path tx meta]
   (try
     (push path (clj->js {:tx          (write-string tx)
                          :tx-meta     meta
                          :session-id  session-id
                          :time        (.now js/Date)}))
     (catch js/Error e
       (log/error e)))))


(defn on-rolling [txpath snappath startk conn]
  (let [ref        (db-ref txpath)
        new-db?    (or (= ":NEW"
                          startk)
                       (nil? startk))
        process-tx (fn process-tx [x]
                     (try
                       (let [val (.val x)
                             k   (.-key x)]
                         (when (not= k startk)
                           (log/debugf "Processing tx: %s" k)
                           (let [{:as   parsed-val
                                  :keys [tx t
                                         tx-meta]} (-> val
                                                       (js->clj :keywordize-keys true)
                                                       (update :tx #(read-string %)))
                                 tx-report         (d/transact! conn tx tx-meta)]
                             ;; (run-tx-after tx-report)
                             (when (= session-id (:session-id parsed-val))
                               (save-snapshot snappath k @conn)
                               (record-db-after-on-tx (conj txpath k)
                                                      parsed-val
                                                      tx-report))
                             )))
                       (catch js/Error e
                         (log/error "Incoming firebase tx failed")
                         (log/error e))))]
    (if  new-db?
      (.. ref
          orderByKey
          (on "child_added"
              process-tx))
      (.. ref
          orderByKey
          (startAt startk)
          (on "child_added"
              process-tx)))))


(defn load-snap
  "Attempts to load the conn from firebase. Returns an async channel which will have the conn on it when it's loaded. If there is no conn at the snappath it will create a conn and save it there."
  [snappath schema]
  (let [snap-chan (async/chan 1)
        put-snap! (fn put-snap!
                    ([k conn]
                     (log/debugf "Successfully loaded firebase conn, k: %s" (pr-str k))
                     (async/put! snap-chan {:k    k
                                            :conn conn})
                     (async/close! snap-chan)))
        ref       (db-ref snappath)]
    (.. ref
        (once "value"
              (fn load-snap-cb [x]
                (if-let [val (.val x)]
                  (try
                    (let [{k  :snapshot-key
                           db :conn} (read-string val)
                          conn       (if (= schema (:schema db))
                                       (d/conn-from-db db)
                                       (d/conn-from-datoms (set (d/datoms db :eavt)) schema))]
                      (put-snap! k conn))
                    (catch js/Object e
                      (log/error "Error loading conn from firebase")
                      (log/error e)))
                  (let [k    ":NEW"
                        conn (d/create-conn schema)]
                    (log/debugf "creating new db at %s" snappath)
                    (save-snapshot snappath k @conn)
                    (put-snap! k conn)))))
        (catch
            (fn [error]
              (log/error error))))
    snap-chan))


(defn create-ctx
  []
  (let [*ctx (r/atom nil)]
    (async-macros/go
      (let [snappath       ["snap"]
            txpath         ["log"]
            schema         {}
            {:keys [k
                    conn]} (async/<! (load-snap snappath schema))
            ]
        (dr/listen! conn)
        (on-rolling txpath snappath k conn)
        (reset! *ctx
                {:conn      conn
                 :transact! (partial transact! txpath)
                 })))
    *ctx))


(defn init! []
  (firebase/initializeApp
   #js {:apiKey      "<Your api key>"
        :databaseURL "<Your database url>"})
  )
