(ns akvo.lumen.lib.aggregation.bar
  (:require [akvo.lumen.lib :as lib]
            [akvo.lumen.lib.aggregation.commons :refer (run-query)]
            [akvo.lumen.lib.dataset.utils :refer (find-column)]
            [akvo.lumen.postgres.filter :refer (sql-str)]
            [clojure.java.jdbc :as jdbc]
            [clojure.pprint :refer (pprint)]
            [clojure.tools.logging :as log]))

(defn- sql [table-name bucket-column subbucket-column comparison aggregation filter-sql sort-query truncate-size]
  (if comparison
    (format "SELECT * FROM (SELECT %1$s as x, %2$s as y FROM %3$s WHERE %4$s GROUP BY %1$s)z ORDER BY %5$s LIMIT %6$s"
            (:columnName bucket-column)
            aggregation
            table-name
            filter-sql
            (case sort-query
              nil   "x ASC"
              "asc" "z.y ASC NULLS FIRST"
              "dsc" "z.y DESC NULLS LAST")
            truncate-size)
    (if subbucket-column
      (format "
          WITH
            sort_table
          AS
            (SELECT %1$s AS x, %2$s AS sort_value, TRUE as include_value 
             FROM %3$s 
             WHERE %4$s 
             GROUP BY %1$s 
             ORDER BY %5$s 
             LIMIT %6$s) , 
            data_table
          AS
            ( SELECT %1$s as x, %2$s as y,
              %7$s as s
              FROM %3$s
              WHERE %4$s
              GROUP BY %1$s, %7$s ) 
          SELECT
            data_table.x AS x,
            data_table.y,
            data_table.s,
            sort_table.sort_value,
            sort_table.include_value
          FROM
            data_table
          LEFT JOIN
            sort_table
          ON
            COALESCE(sort_table.x::text, '@@@MISSINGDATA@@@') = COALESCE(data_table.x::text, '@@@MISSINGDATA@@@')
          WHERE
            sort_table.include_value IS NOT NULL 
          ORDER BY %5$s"
              (:columnName bucket-column)
              aggregation
              table-name
              filter-sql
              (case sort-query
                nil   "x ASC"
                "asc" "sort_value ASC NULLS FIRST"
                "dsc" "sort_value DESC NULLS LAST")
              truncate-size
              (:columnName subbucket-column))
      (format "SELECT * FROM (SELECT %1$s as x, %2$s as y FROM %3$s WHERE %4$s GROUP BY %1$s)z ORDER BY %5$s LIMIT %6$s"
              (:columnName bucket-column)
              aggregation
              table-name
              filter-sql
              (case sort-query
                nil   "x ASC"
                "asc" "z.y ASC NULLS FIRST"
                "dsc" "z.y DESC NULLS LAST")
              truncate-size))))

(defn- aggregation* [aggregation-method metric-column bucket-column]
  (let [aggregation-method (if-not metric-column "count" (or aggregation-method "count"))]
    (format
     (case aggregation-method
       nil                         "NULL"
       ("min" "max" "count" "sum") (str aggregation-method  "(%s)")
       "mean"                      "avg(%s)"
       "median"                    "percentile_cont(0.5) WITHIN GROUP (ORDER BY %s)"
       "distinct"                  "COUNT(DISTINCT %s)"
       "q1"                        "percentile_cont(0.25) WITHIN GROUP (ORDER BY %s)"
       "q3"                        "percentile_cont(0.75) WITHIN GROUP (ORDER BY %s)")
     (or (:columnName metric-column)
         (:columnName bucket-column)))))

(defn- subbucket-column-response [sql-response bucket-column]
  (let [bucket-values    (distinct
                          (mapv
                           (fn [[x-value y-value s-value]] x-value)
                           sql-response))
        subbucket-values (distinct
                          (mapv
                           (fn [[x-value y-value s-value]] s-value)
                           sql-response))]
    {:series
     (mapv
      (fn [s-value]
        {:key   s-value
         :label s-value
         :data
         (map
          (fn
            [bucket-value]
            {:value (or (nth
                         (first
                          (filter
                           (fn [o] (and (= (nth o 0) bucket-value) (= (nth o 2) s-value)))
                           sql-response))
                         1
                         0) 0)})
          bucket-values)})
      subbucket-values)
     :common
     {:metadata
      {:type (:type bucket-column)}
      :data (mapv
             (fn [bucket] {:label bucket
                           :key   bucket})
             bucket-values)}}))

(defn- bucket-column-response [sql-response bucket-column metric-y-column]
  {:series [{:key   (:title metric-y-column)
             :label (:title metric-y-column)
             :data  (mapv (fn [[x-value y-value]]
                            {:value y-value})
                          sql-response)}]
   :common {:metadata {:type (:type bucket-column)}
            :data     (mapv (fn [[x-value y-value]]
                              {:label x-value
                               :key   x-value})
                            sql-response)}})

(defn- comparison-column-response [sql-response bucket-column metric-y-column comparison-y-column]

  {:series (conj [{:key   (:title metric-y-column)
                   :label (:title metric-y-column)
                   :data  (mapv (fn [[x-value y-value]]
                                  {:value y-value})
                                sql-response)}]
                 [{:key   (:title comparison-y-column)
                   :label (:title comparison-y-column)
                   :data  (mapv (fn [[x-value y-value]]
                                  {:value y-value})
                                sql-response)}])
   :common {:metadata {:type (:type bucket-column)}
            :data     (conj (mapv (fn [[x-value y-value]]
                                    {:label x-value
                                     :key   x-value})
                                  sql-response)
                            (mapv (fn [[x-value y-value]]
                                    {:label x-value
                                     :key   x-value})
                                  sql-response))}})

(defn query
  [tenant-conn {:keys [columns table-name]} query]
  (if-let [bucket-column    (find-column columns (:bucketColumn query))]
    (let [subbucket-column (find-column columns (:subBucketColumn query))
          metric-y-column  (or (find-column columns (:metricColumnY query)) subbucket-column)
          aggregation  (aggregation* (:metricAggregation query) metric-y-column bucket-column)
          metric-y-comparison (find-column columns (:metricColumnComparisonY))

          sql-response (->> (sql table-name bucket-column subbucket-column metric-y-comparison aggregation
                                 (sql-str columns (:filters query))
                                 (:sort query) (or (:truncateSize query) "ALL"))
                            (run-query tenant-conn))

          max-elements     200]
      (if (> (count sql-response) max-elements)
        (lib/bad-request {"error"  true
                          "reason" "too-many"
                          "max"    max-elements
                          "count"  (count sql-response)})
        (lib/ok
         (if metric-y-comparison
           (comparison-column-response sql-response bucket-column metric-y-column metric-y-comparison))
         (if subbucket-column
           (subbucket-column-response sql-response bucket-column)
           (bucket-column-response sql-response bucket-column metric-y-column)))))
    (lib/ok (bucket-column-response nil nil nil))))
