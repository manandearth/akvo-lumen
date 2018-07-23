(ns akvo.lumen.specs.aggregation.query
  (:require [akvo.lumen.specs.core :as lumen.s]
            [akvo.lumen.specs.postgres]
            [akvo.lumen.postgres.filter :as postgres.filter]
            [clojure.spec.alpha :as s]))

(s/def ::operation ::postgres.filter/operation)

(s/def ::strategy ::postgres.filter/strategy)

(s/def ::value ::lumen.s/string-nullable)

(s/def ::column string?)

(s/def ::nullable-column (s/or :s string?
                               :n nil?))

(s/def ::aggregation ::postgres.filter/aggregation)