(ns akvo.lumen.import
  (:require  [akvo.lumen.boundary.error-tracker :as error-tracker]
             [akvo.lumen.import.common :as import]
             [akvo.lumen.import.csv]
             [akvo.lumen.import.flow]
             [akvo.lumen.lib :as lib]
             [akvo.lumen.lib.raster :as raster]
             [akvo.lumen.util :as util]
             [cheshire.core :as json]
             [clojure.java.jdbc :as jdbc]
             [clojure.string :as string]
             [clojure.tools.logging :as log]
             [hugsql.core :as hugsql])
  (:import [org.postgis Polygon MultiPolygon]
           [org.postgresql.util PGobject]))

(hugsql/def-db-fns "akvo/lumen/job-execution.sql")
(hugsql/def-db-fns "akvo/lumen/lib/dataset.sql")
(hugsql/def-db-fns "akvo/lumen/transformation.sql")

(defn successful-import [conn job-execution-id table-name columns spec]
  (let [dataset-id (util/squuid)
        imported-table-name (util/gen-table-name "imported")]
    (insert-dataset conn {:id dataset-id
                          :title (get spec "name") ;; TODO Consistent naming. Change on client side?
                          :description (get spec "description" "")})
    (clone-data-table conn
                      {:from-table table-name
                       :to-table imported-table-name}
                      {}
                      {:transaction? false})
    (insert-dataset-version conn {:id (util/squuid)
                                  :dataset-id dataset-id
                                  :job-execution-id job-execution-id
                                  :table-name table-name
                                  :imported-table-name imported-table-name
                                  :version 1
                                  :columns (mapv (fn [{:keys [title id type key] :as columns}]
                                                   (cond-> {:type (name type)
                                                            :title (string/trim title)
                                                            :columnName (name id)
                                                            :sort nil
                                                            :direction nil
                                                            :hidden false}
                                                     (contains? columns :key) (assoc :key (boolean key))))
                                                 columns)
                                  :transformations []})
    (update-successful-job-execution conn {:id job-execution-id})))

(defn failed-import [conn job-execution-id reason table-name]
  (update-failed-job-execution conn {:id job-execution-id
                                     :reason [reason]})
  (drop-table conn {:table-name table-name}))


(defn val->geometry-pgobj
  [v]
  (doto (PGobject.)
    (.setType "geometry")
    (.setValue (.toString v))))

(extend-protocol jdbc/ISQLValue
  org.postgis.Polygon
  (sql-value [v] (val->geometry-pgobj v))
  org.postgis.MultiPolygon
  (sql-value [v] (val->geometry-pgobj v))
  org.postgis.Point
  (sql-value [v] (val->geometry-pgobj v)))



(defn do-import
  "Import runs within a future and since this is not taking part of ring
  request / response cycle we need to make sure to capture errors."
  [conn {:keys [sentry-backend-dsn] :as config} error-tracker job-execution-id]
  (let [table-name (util/gen-table-name "ds")]
    (try
      (let [spec (:spec (data-source-spec-by-job-execution-id conn {:job-execution-id job-execution-id}))]
        (with-open [importer (import/dataset-importer (get spec "source") config)]
          (let [columns (import/columns importer)]
            (import/create-dataset-table conn table-name columns)
            (import/add-key-constraints conn table-name columns)
            (util/time* :iterate-do-import
                        (doall  (map
                                 (partial jdbc/insert-multi! conn table-name )
                                 (partition-all 1000 (map import/coerce-to-sql (import/records importer))))))
            (successful-import conn job-execution-id table-name columns spec))))
      (catch Throwable e
        (failed-import conn job-execution-id (.getMessage e) table-name)
        (log/error e)
        (error-tracker/track error-tracker e)
        (throw e)))))

(defn handle-import-request [tenant-conn config error-tracker claims data-source]
  (let [data-source-id (str (util/squuid))
        job-execution-id (str (util/squuid))
        table-name (util/gen-table-name "ds")
        kind (get-in data-source ["source" "kind"])]
    (insert-data-source tenant-conn {:id data-source-id
                                     :spec (json/generate-string data-source)})
    (insert-job-execution tenant-conn {:id job-execution-id
                                       :data-source-id data-source-id})
    (future (do-import tenant-conn config error-tracker job-execution-id))
    (lib/ok {"importId" job-execution-id
             "kind" kind})))
