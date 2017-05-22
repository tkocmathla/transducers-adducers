(ns transducer-adducers.pipeline
  (:require
    [clojure.core.reducers :as r]
    [clojure.core.async :as a 
     :refer [>! <! >!! <!! go go-loop chan to-chan pipeline pipeline-async pipeline-blocking]]
    [clojure.data.json :as json]
    [clojure.walk :refer [keywordize-keys]]
    [clj-http.client :as http]
    [hickory.core :as hc]
    [hickory.select :as hs :refer [select tag]]))

(defn pipeline-process [xform xs]
  (let [cin (to-chan xs) cout (chan 1)]
    ;; apply xform from cin -> cout in parallel
    (pipeline 16 cout xform cin)
    ;; go executes concurrently on a separate thread and returns a channel with the result
    (<!! (go-loop [acc []]
           (let [x (<! cout)]
             (if-not (nil? x) 
               (recur (conj acc x))
               acc))))))

;; -----------------------------------------------------------------------------

(def adv-url "https://api.github.com/repos/richardwilkes/gcs_library/contents/Library/Advantages")
(def xform (comp (map keywordize-keys) 
                 (map :download_url)
                 (map http/get)
                 (map :body)
                 (map hc/parse)
                 (map hc/as-hickory)
                 (map (partial select (hs/and (tag :advantage))))))

(defn time-these [] 
  (let [files (->> adv-url http/get :body json/read-str)]
    (time (dotimes [_ 1] (into [] xform files)))
    (time (dotimes [_ 1] (pipeline-process xform files)))))