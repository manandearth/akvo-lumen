(ns akvo.lumen.local-server
  (:require [compojure.core :refer :all]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [ring.util.response :refer [response]]))

(defn endpoint [_]
  (GET "/local-server/:file" [file]
       {:status  200
        :headers {"Content-Type" "text/plain"}
        :body    (slurp (io/resource file))}))
