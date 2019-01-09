(ns akvo.lumen.lib.import-test
  {:functional true}
  (:require [akvo.lumen.fixtures :refer [*tenant-conn*
                                         tenant-conn-fixture
                                         *error-tracker*
                                         error-tracker-fixture]]
            [akvo.lumen.test-utils :refer [import-file update-file]]
            [akvo.lumen.utils.logging-config :refer [with-no-logs]]
            [akvo.lumen.specs.import :as i-c]
            [clojure.string :as string]
            [clojure.test :refer :all]
            [hugsql.core :as hugsql])
  (:import [java.util.concurrent ExecutionException]))

(hugsql/def-db-fns "akvo/lumen/lib/job-execution.sql")
(hugsql/def-db-fns "akvo/lumen/lib/transformation.sql")

(use-fixtures :once tenant-conn-fixture error-tracker-fixture)


(deftest test-csv-dos-file
  (testing "Import of DOS-formatted CSV file"
    (let [dataset-id (import-file *tenant-conn* *error-tracker* {:file "dos.csv" :dataset-name "DOS data"})
          dataset (dataset-version-by-dataset-id *tenant-conn* {:dataset-id dataset-id
                                                                :version 1})]
      (is (= 2 (count (:columns dataset)))))))

(deftest test-csv-mixed-columns
  (testing "Import of mixed-type data"
    (let [dataset-id (import-file *tenant-conn* *error-tracker* {:file "mixed-columns.csv"
                                                                 :dataset-name "Mixed Columns"})
          dataset (dataset-version-by-dataset-id *tenant-conn* {:dataset-id dataset-id
                                                                :version 1})
          columns (:columns dataset)]
      (is (= "text" (get (first columns) "type")))
      (is (= "number" (get (second columns) "type")))
      (is (= "text" (get (last columns) "type"))))))

(deftest test-csv-geoshape-csv
  (testing "Import csv file generated from a shapefile"
    (let [dataset-id (import-file *tenant-conn* *error-tracker* {:dataset-name "Liberia shapefile"
                                                                 :file "liberia_adm2.csv"
                                                                 :has-column-headers? true})
          dataset (dataset-version-by-dataset-id *tenant-conn* {:dataset-id dataset-id
                                                                :version 1})
          columns (:columns dataset)]
      (is (= "geoshape" (get (first columns) "type")))
      (is (= "number" (get (second columns) "type")))
      (is (= "text" (get (last columns) "type")))
      (is (= (count columns) 15)))))

(deftest test-csv-varying-column-count
  (testing "Should fail to import csv file with varying number of columns"
    (let [job-execution-id (import-file *tenant-conn* *error-tracker* {:dataset-name "Mixed Column Counts"
                                                                       :file "mixed-column-counts.csv"})]
      (is (= "Invalid csv file. Varying number of columns"
             (:error-message (job-execution-by-id *tenant-conn* {:id job-execution-id})))))))

(deftest test-csv-trimmed-columns
  (testing "Testing if whitespace is removed from beginning & end of column titles"
    (let [dataset-id (import-file *tenant-conn* *error-tracker* {:dataset-name "Padded titles"
                                                                 :file "whitespace.csv"})
          dataset (dataset-version-by-dataset-id *tenant-conn* {:dataset-id dataset-id
                                                                :version 1})
          titles (map :title (:rows dataset))
          trimmable? #(or (string/starts-with? " " %) (string/ends-with? " " %))]
      (is (every? trimmable? titles)))))


(hugsql/def-db-fns "akvo/lumen/lib/transformation_test.sql")

(deftest  test-clj-import
  (testing "Testing import"
    (let [dataset-id (import-file *tenant-conn* *error-tracker* 
                                  {:dataset-name "Padded titles"
                                   :kind "clj"
                                   :data (i-c/sample-imported-dataset [:text :number] 2) })
          dataset (dataset-version-by-dataset-id *tenant-conn* {:dataset-id dataset-id
                                                                :version 1})
          stored-data (->> (latest-dataset-version-by-dataset-id *tenant-conn*
                                                                 {:dataset-id dataset-id})
                           (get-data *tenant-conn*))]
      (is (= (map keys (:columns dataset)) '(("key"
	                                      "sort"
	                                      "direction"
	                                      "title"
	                                      "type"
	                                      "multipleType"
	                                      "hidden"
	                                      "multipleId"
	                                      "columnName")
                                             ("key"
	                                      "sort"
	                                      "direction"
	                                      "title"
	                                      "type"
	                                      "multipleType"
	                                      "hidden"
	                                      "multipleId"
	                                      "columnName"))))

      (is (= (map #(select-keys % ["type" "columnName"]) (:columns dataset))
             '({"type" "text", "columnName" "c1"}
	       {"type" "number", "columnName" "c2"})))

      (is (= (map keys stored-data) '((:rnum :c1 :c2) (:rnum :c1 :c2)))))))

(deftest  test-clj-update
  (testing "Testing update"
    (let [[job dataset] (import-file *tenant-conn* *error-tracker* 
                                     {:dataset-name "Padded titles"
                                      :kind "clj"
                                      :data (i-c/sample-imported-dataset [:text :number] 2) 
                                      :with-job? true})
          dataset-id (:dataset_id dataset)
          dataset (dataset-version-by-dataset-id *tenant-conn* {:dataset-id dataset-id
                                                                :version 1})
          stored-data (->> (latest-dataset-version-by-dataset-id *tenant-conn*
                                                                 {:dataset-id dataset-id})
                           (get-data *tenant-conn*))
          updated-res (update-file *tenant-conn* *error-tracker* (:dataset_id job) (:data_source_id job)
                        {:kind "clj"
                         :data (i-c/sample-imported-dataset [:text :number] 2)})]
      (is (some? updated-res)))))


#_(deftest test-import-raster
  (testing "Import raster"
    (let [filename "SLV_ppp_v2b_2015_UNadj.tif"
          path (.getAbsolutePath (.getParentFile (io/file (io/resource filename))))
          prj (project-and-compress path filename)]
      (is (zero? (get-in prj [:shell :exit])))
      (let [table-name (str "t_" (System/currentTimeMillis))
            sql (get-raster-data-as-sql (:path prj) (:filename prj) table-name)
            file (str "/tmp/" (System/currentTimeMillis) ".sql")]
        (create-raster-table *tenant-conn* {:table-name table-name})
        (create-raster-index *tenant-conn* {:table-name table-name})
        (add-raster-constraints *tenant-conn* {:table-name table-name})
        (jdbc/execute! *tenant-conn* [sql])
        (is (= 84 (:c (raster-count *tenant-conn* {:table-name table-name}))))))))
