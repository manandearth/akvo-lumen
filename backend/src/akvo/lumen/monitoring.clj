(ns akvo.lumen.monitoring
  (:require [clojure.tools.logging :as log]
            [iapetos.core :as prometheus]
            [iapetos.collector.jvm :as jvm]
            [iapetos.collector.ring :as ring]
            [integrant.core :as ig])
  (:import (io.prometheus.client.dropwizard DropwizardExports)
           (com.codahale.metrics MetricRegistry)))

(defmethod ig/init-key ::dropwizard-registry [_ _]
  (MetricRegistry.))

(defmethod ig/init-key ::collector [_ {:keys [dropwizard-registry]}]
  (-> (prometheus/collector-registry)
      (jvm/initialize)
      (prometheus/register (DropwizardExports. dropwizard-registry))
      (ring/initialize {:labels [:tenant]})))

(defmethod ig/init-key ::middleware [_ {:keys [collector]}]
  (fn [handler]
    (ring/wrap-metrics handler collector {:path-fn (constantly "unknown")
                                          :label-fn (fn [request response]
                                                      (log/info :metric-path-to-monitoring (:metric-path response))
                                                      {:tenant (:tenant request)
                                                       :path (:metric-path response)})})))

(comment
  (slurp "http://localhost:3000/metrics")
  )
