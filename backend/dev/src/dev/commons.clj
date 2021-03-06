(ns dev.commons
  (:require [akvo.lumen.test-utils :as tu]
            [integrant.core :as ig]
            [integrant.repl :as ir]))

(derive :akvo.lumen.component.emailer/dev-emailer :akvo.lumen.component.emailer/emailer)
(derive :akvo.lumen.component.caddisfly/local :akvo.lumen.component.caddisfly/caddisfly)
(derive :akvo.lumen.component.error-tracker/local :akvo.lumen.component.error-tracker/error-tracker)

(defn dissoc-prod-components [c]
  (dissoc c
          :akvo.lumen.component.emailer/mailjet-v3-emailer
          :akvo.lumen.component.caddisfly/prod
          :akvo.lumen.component.error-tracker/prod))


(defn config []
  (let [c (dissoc-prod-components (tu/prep "akvo/lumen/config.edn" "test.edn" "dev.edn"))]
    (ir/set-prep! (fn [] c))
    (ig/load-namespaces c)
    c))


(def tenants (-> (config) :akvo.lumen.migrate/migrate :seed :tenants))
