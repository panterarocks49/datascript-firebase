(ns datascript-firebase.core
  (:require
   [reagent.core :as r]
   [datascript-firebase.reactive :as dr]
   [datascript-firebase.firebase :as firebase]))


(defn page [{:as ctx :keys [conn transact!]}]
  (r/with-let [*ints (dr/q
                      '[:find [?int ...]
                        :where
                        [_ :test ?int]]
                      conn)]
    [:div
     {:style {:margin-top   "24px"
              :text-align "center"
              }}
     [:button
      {:on-click #(transact! [{:db/id -1
                               :test  (int (rand 1000))}])}
      "Add integer"]
     (into
      [:div]
      (doall
       (for [i @*ints]
         [:div i])))]))


(defn page-wrapper []
  (r/with-let [*ctx (firebase/create-ctx)]
    (if @*ctx
      [page @*ctx]
      [:div
       "loading..."])
    ))


(defn reload []
  (r/render [page-wrapper]
            (.getElementById js/document "app")))


(defn destroy []
  (r/unmount-component-at-node (.getElementById js/document "app")))


(defn ^:export main []
  (enable-console-print!)
  (firebase/init!)
  (reload))


