(ns akvo.lumen.local-server
  (:require [compojure.core :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [akvo.lumen.lib :as lib]
            [akvo.lumen.specs :as lumen.s]
            [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [ring.middleware.transit :refer (wrap-transit-response)]
            [ring.util.response :refer [response]]))

(defn spec* [ns id]
  (keyword (str (apply str (next (seq ns))) "/" id)))

(defn endpoint [_]
  (context "/spec" request
           (GET "/describe/:ns/:id" [ns id]
                (lib/ok {:namespace ns
                         :id id
                         :ns (spec* ns id)
                         :spec (s/describe (spec* ns id))}))
           (wrap-transit-response
            (POST "/valid/:ns/:id" [ns id]
                  (log/error :raw request)
                  (lib/ok {:namespace ns
                           :body (:body request)
                           :id id
                           :ns (spec* ns id)
                           :valid? (s/conform (spec* ns id) (:body request))}))
            {:encoding :json, :opts {}})
          
           ))

#_(s/conform :akvo.lumen.specs.import.column/type "text")

#_(map first (s/exercise :akvo.lumen.specs.import.column/type))
(defmethod ig/init-key :akvo.lumen.local-server/endpoint  [_ opts]
  (endpoint opts))

(comment
  (-> (client/get
       "http://t1.lumen.local:3000/spec/describe/:akvo.lumen.specs.import.column/type")
      :body
      (json/decode keyword))
  (-> (client/post
       "http://t1.lumen.local:3000/spec/conform/:akvo.lumen.specs.import.column/type"
       {:form-params :text
        :content-type :transit+json})
      :body

      )
  )
