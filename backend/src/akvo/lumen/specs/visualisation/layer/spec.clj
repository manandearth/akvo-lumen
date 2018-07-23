(ns akvo.lumen.specs.visualisation.layer.spec
  (:require [akvo.lumen.specs.visualisation.layer :as visualisation.layer.s]
            [clojure.spec.alpha :as s]))

(s/def ::version int?)

(s/def ::baseLayer #{"terrain" "satellite" "street"})

(s/def ::layers (s/coll-of ::visualisation.layer.s/layer :gen-max 3))

(s/def ::spec (s/keys :req-un [::layers
			       ::version
			       ::baseLayer]))