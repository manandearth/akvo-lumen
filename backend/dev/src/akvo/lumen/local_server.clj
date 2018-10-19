(ns akvo.lumen.local-server
  (:require [compojure.core :refer :all]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [ring.util.response :refer [response]]))

(defn endpoint [_]
  (GET "/local-server/:file" [file]
       {:status  200
        :headers {"Content-Type" "text/plain"}
        :body    (slurp (io/resource file))}))

(defmethod ig/init-key :akvo.lumen.local-server/endpoint  [_ opts]
  (endpoint opts))
