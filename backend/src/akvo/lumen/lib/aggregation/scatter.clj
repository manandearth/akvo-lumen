(ns akvo.lumen.lib.aggregation.scatter
  (:require [akvo.lumen.lib :as lib]
            [akvo.lumen.lib.aggregation.commons :refer (run-query sql-aggregation-subquery)]
            [akvo.lumen.lib.dataset.utils :refer (find-column)]
            [akvo.lumen.postgres.filter :refer (sql-str)]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]))

(defn- serie-data [tag sql-data index]
  (mapv #(array-map tag (nth % index)) sql-data))

(defn serie [sql-data column index]
  (when (:title column)
    {:key (:title column)
     :label (:title column)
     :data (serie-data :value sql-data index)
     :metadata {:type (:type column)}}))

(defn query
  [tenant-conn {:keys [columns table-name]} query]
  (let [filter-sql (sql-str columns (:filters query))
        column-x (find-column columns (:metricColumnX query))
        column-y (find-column columns (:metricColumnY query))
        column-size (find-column columns (:metricColumnSize query))
        column-category (find-column columns (:bucketColumnCategory query))
        column-label (find-column columns (:datapointLabelColumn query))
        column-bucket (find-column columns (:bucketColumn query))
        max-points 2500

        aggregation (partial sql-aggregation-subquery (:metricAggregation query))

        subquery (format "(SELECT * FROM %1$s WHERE %2$s ORDER BY random() LIMIT %3$s)z"
                         table-name filter-sql max-points)

        sql-text-with-aggregation
        (format "SELECT %1$s AS x, %2$s AS y, %3$s AS size, %4$s AS category, %5$s AS label 
                 FROM %6$s
                 GROUP BY %5$s"
                (aggregation column-x)
                (aggregation column-y)
                (aggregation column-size)
                (aggregation column-category)
                (:columnName column-bucket)
                subquery)
        sql-text-without-aggregation (format "SELECT * FROM
                                               (SELECT * FROM 
                                                 (SELECT %1$s AS x, %2$s AS y, %3$s AS size, %4$s AS category, %5$s AS label 
                                                  FROM %6$s 
                                                  WHERE %7$s)z
                                                ORDER BY random() 
                                                LIMIT %8$s)zz
                                              ORDER BY zz.x"
                         (:columnName column-x)
                         (:columnName column-y)
                         (:columnName column-size)
                         (:columnName column-category)
                         (:columnName column-label)
                         table-name
                         filter-sql
                         max-points)

        sql-text (if column-bucket sql-text-with-aggregation sql-text-without-aggregation)
        sql-response (run-query tenant-conn sql-text)]
    (lib/ok
      {:series (map-indexed
                 (fn [idx column]
                   (serie sql-response column idx))
                 [column-x column-y column-size column-category])
       :common {:metadata {:type (:type column-label)
                           :sampled (= (count sql-response) max-points)}
                :data (serie-data :label sql-response 4)}})))
