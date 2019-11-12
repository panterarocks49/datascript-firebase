(ns datascript-firebase.core
  (:require
   [reagent.core :as r]))


(defn page []
  [:div "hello world"])


(defn reload []
  (r/render [page]
            (.getElementById js/document "app")))


(defn destroy []
  (r/unmount-component-at-node (.getElementById js/document "app")))


(defn ^:export main []
  (enable-console-print!)
  ;; (fire-core/init!)
  (reload))


