(ns akvo.lumen.local-server
  (:require [compojure.core :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [akvo.lumen.lib :as lib]
            [akvo.lumen.specs :as lumen.s]
            [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [ring.util.response :refer [response]]))

(defn endpoint [_]
  (context "/spec" request
           (context "/:ns/:id" [ns id]
                    (let-routes [spec (keyword (str (apply str (next (seq ns))) "/" id))]
                      (GET "/" _
                           (lib/ok {:namespace ns
                                    :id id
                                    :ns spec
                                    :spec (s/describe spec)}))
                      (POST "/" {:keys [body] :as request} 
                            (println  body)
                            (lib/ok {:namespace ns
                                     :body (str body)
                                     :id id
                                     :ns spec
                                     :spec (s/describe spec)}))

                      )
                    )


           ))

(keyword (str (apply str (next (seq ":hola"))) "/" "id"))

(defmethod ig/init-key :akvo.lumen.local-server/endpoint  [_ opts]
  (endpoint opts))

(comment
  (-> (client/get
       "http://t1.lumen.local:3000/spec/:akvo.lumen.specs.import.column/type/")
      :body
      (json/decode keyword))
  (-> (client/post
       "http://t1.lumen.local:3000/spec/:akvo.lumen.specs.import.column/type/"
       {:body (json/encode {"name" "group-name"})
        :content-type :json})
      :body
      (json/decode keyword)))
