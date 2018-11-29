(ns akvo.lumen.lib.aggregation.scatter
  (:require [akvo.lumen.lib :as lib]
            [akvo.lumen.postgres.filter :as filter]
            [akvo.lumen.lib.dataset.utils :as utils]
            [clojure.java.jdbc :as jdbc]))

(defn- run-query [tenant-conn table-name sql-text column-x-name column-y-name column-size-name column-category-name filter-sql aggregation-method max-points column-label-name column-bucket-name]
  (rest (jdbc/query tenant-conn
                    [(format sql-text
                             column-x-name column-y-name column-size-name column-category-name table-name filter-sql aggregation-method max-points column-label-name column-bucket-name)]
                    {:as-arrays? true})))

(defn cast-to-decimal [column-string column-type]
  (case column-type
    "number" column-string
    "date" (str "(1000 * cast(extract(epoch from " column-string ") as decimal))")
    column-string))

(defn sql-aggregation-subquery [aggregation-method column-string column-type]
  (case aggregation-method
    nil ""
    ("min" "max" "count" "sum") (str aggregation-method "(" (cast-to-decimal column-string column-type) "::decimal)")
    "mean" (str "avg(" (cast-to-decimal column-string column-type) "::decimal)")
    "median" (str "percentile_cont(0.5) WITHIN GROUP (ORDER BY " (cast-to-decimal column-string column-type) ")")
    "distinct" (str "COUNT(DISTINCT " (cast-to-decimal column-string column-type) ")")
    "q1" (str "percentile_cont(0.25) WITHIN GROUP (ORDER BY " (cast-to-decimal column-string column-type) ")")
    "q3" (str "percentile_cont(0.75) WITHIN GROUP (ORDER BY " (cast-to-decimal column-string column-type) ")")))

(defn query
  [tenant-conn {:keys [columns table-name]} query]
  (let [filter-sql (filter/sql-str columns (get query "filters"))
        column-x (utils/find-column columns (get query "metricColumnX"))
        column-x-type (get column-x "type")
        column-x-name (get column-x "columnName")
        column-x-title (get column-x "title")
        column-y (utils/find-column columns (get query "metricColumnY"))
        column-y-type (get column-y "type")
        column-y-name (get column-y "columnName")
        column-y-title (get column-y "title")
        column-size (utils/find-column columns (get query "metricColumnSize"))
        column-size-type (get column-size "type")
        column-size-name (get column-size "columnName")
        column-size-title (get column-size "title")
        column-category (utils/find-column columns (get query "bucketColumnCategory"))
        column-category-type (get column-category "type")
        column-category-name (get column-category "columnName")
        column-category-title (get column-category "title")
        column-label (utils/find-column columns (get query "datapointLabelColumn"))
        column-label-type (get column-label "type")
        column-label-name (get column-label "columnName")
        column-label-title (get column-label "title")
        column-bucket (utils/find-column columns (get query "bucketColumn"))
        column-bucket-name (get column-bucket "columnName")
        column-bucket-title (get column-bucket "title")
        max-points 2500
        have-aggregation (boolean column-bucket)
        aggregation-method (get query "metricAggregation")

        sql-text-with-aggregation (str "SELECT "
                                       (sql-aggregation-subquery aggregation-method "%1$s" column-x-type)
                                       " AS x, "
                                       (sql-aggregation-subquery aggregation-method "%2$s" column-y-type)
                                       " AS y, "
                                       (sql-aggregation-subquery aggregation-method "%3$s" column-size-type)
                                       " AS size, "
                                       (sql-aggregation-subquery aggregation-method "%4$s" column-category-type)
                                       " AS category, %10$s AS label FROM (SELECT * FROM %5$s WHERE %6$s ORDER BY random() LIMIT %8$s)z GROUP BY %10$s")
        sql-text-without-aggregation "
          SELECT * FROM (SELECT * FROM (SELECT %1$s AS x, %2$s AS y, %3$s AS size, %4$s AS category, %9$s AS label FROM %5$s WHERE %6$s)z ORDER BY random() LIMIT %8$s)zz ORDER BY zz.x"
        sql-text (if have-aggregation sql-text-with-aggregation sql-text-without-aggregation)
        sql-response (run-query tenant-conn table-name sql-text column-x-name column-y-name column-size-name  column-category-name filter-sql aggregation-method max-points column-label-name column-bucket-name)]
    (lib/ok
     {"series" (conj [{"key" column-x-title
                        "label" column-x-title
                        "data" (mapv (fn [[x-value y-value size-value category-value label]]
                                        {"value" x-value})
                                      sql-response)
                        "metadata" {"type" column-x-type}}
                      {"key" column-y-title
                        "label" column-y-title
                        "data" (mapv (fn [[x-value y-value size-value category-value label]]
                                      {"value" y-value})
                                    sql-response)
                        "metadata"  {"type" column-y-type}}]
                      (when column-size-title 
                        {"key" column-size-title
                        "label" column-size-title
                        "data" (mapv (fn [[x-value y-value size-value category-value label]]
                                        {"value" size-value})
                                      sql-response)
                        "metadata"  {"type" column-size-type}})
                      (when column-category-title
                        {"key" column-category-title
                        "label" column-category-title
                        "data" (mapv (fn [[x-value y-value size-value category-value label]]
                                        {"value" category-value})
                                      sql-response)
                        "metadata"  {"type" column-category-type}}))
      "common" {"metadata" {"type" column-label-type "sampled" (= (count sql-response) max-points)}
                "data" (mapv (fn [[x-value y-value size-value category-value label]]
                               {"label" label})
                             sql-response)}})))
