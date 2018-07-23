(ns akvo.lumen.specs.transformations.remove-sort
    (:require [akvo.lumen.specs.dataset.column :as dataset.column.s]
	      [clojure.spec.alpha :as s]))

(s/def ::columnName ::dataset.column.s/columnName)

(s/def ::args
  (s/keys :req-un [::columnName]))