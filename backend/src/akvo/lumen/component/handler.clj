(ns akvo.lumen.component.handler
  (:require [compojure.core :as compojure.core]
            [compojure.response :as compojure.res]
            [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [ring.middleware.defaults]
            [ring.middleware.json]
            [ring.middleware.stacktrace]
            [ring.util.response :as ring.response]))

(defn metric-path-middleware [handler]
  (fn [request]
    (log/debug :--------------)
    (log/debug :uri (:uri request)  :route-params (:route-params request) :context (:context request) :compojure/route (:compojure/route request) )
    (log/info :metric-path (let [[method path] (:compojure/route request)]
                             [method (str (:context request) path)]))
    (log/debug :--------------)
    (assoc (handler request)
           :metric-path (let [[method path] (:compojure/route request)]
                          [method (str (:context request) path)]))))

(defn- middleware-fn [f args]
  (let [args (if (or (nil? args) (sequential? args)) args (list args))]
    #(apply f % args)))

(defn- middleware-map [functions arguments]
  (reduce-kv (fn [m k v] (assoc m k (middleware-fn v (arguments k)))) {} functions))

(defn- compose-middleware [applied functions arguments]
  (->> (reverse applied)
       (map (middleware-map functions arguments))
       (apply comp identity)))

(defmethod ig/init-key :akvo.lumen.component.handler/applied-fun  [_ opts]
  (log/error :applied-fun-start)
  (fn [endpoints]
    (doseq [e endpoints]
      (log/error :e e))))

#_(defn routing
  "Apply a list of routes to a Ring request map."
  [request & handlers]
  (some #(% request) (map #(-> %
;;                               (compojure.core/wrap-routes middleware)
                               (compojure.core/wrap-routes metric-path-middleware)
                               ) handlers)))

#_(defn routes
  "Create a Ring handler by combining several handlers into one."
  [middleware & handlers]
  (fn
    ([request]
     (apply routing request handlers))
    ([request respond raise]
     (letfn [(f [handlers]
               (if (seq handlers)
                 (let [handler  (compojure.core/wrap-routes (first handlers) metric-path-middleware)
                       respond' #(if % (respond %) (f (rest handlers)))]
                   (handler request respond' raise))
                 (respond nil)))]
       (f handlers)))))


(defmethod ig/init-key :akvo.lumen.component.handler/handler  [_ {:keys [endpoints middleware handler] :as opts}]
  (if-not handler
    (let [wrap-mw (compose-middleware (:applied middleware) (:functions middleware) (:arguments middleware))
          r* (apply compojure.core/routes
                    (map
                     #(compojure.core/wrap-routes % metric-path-middleware)
                     (vals endpoints)))]
      (assoc opts :handler (wrap-mw r*)))
    opts))

(defmethod ig/halt-key! :akvo.lumen.component.handler/handler  [_ opts]
  (dissoc opts :handler))

(defmethod ig/init-key :akvo.lumen.component.handler/wrap-stacktrace  [_ opts]
  ring.middleware.stacktrace/wrap-stacktrace)

(defmethod ig/init-key :akvo.lumen.component.handler/wrap-body  [_ opts]
  ring.middleware.json/wrap-json-body)

(defmethod ig/init-key :akvo.lumen.component.handler/wrap-response  [_ opts]
  ring.middleware.json/wrap-json-response)

(defmethod ig/init-key :akvo.lumen.component.handler/wrap-defaults  [_ opts]
  ring.middleware.defaults/wrap-defaults)

(defmethod ig/init-key :akvo.lumen.component.handler/wrap-hide-errors  [_ opts]
  (fn [handler error-response]
    (fn [request]
      (try
        (handler request)
        (catch Throwable _
          (-> (compojure.res/render error-response request)
              (ring.response/content-type "text/html")
              (ring.response/status 500)))))))

(defmethod ig/init-key :akvo.lumen.component.handler/wrap-not-found  [_ opts]
  (fn [handler error-response]
    (fn [request]
      (or (handler request)
          (-> (compojure.res/render error-response request)
              (ring.response/content-type "text/html")
              (ring.response/status 404))))))
