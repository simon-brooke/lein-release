(ns leiningen.release-test
  (:require
   [clojure.test :refer :all]
   [leiningen.release :refer :all]))

(deftest parse-maven-version-tests
  (testing "parse-maven-version"
    (let [expected {:format :major-only,
                    :major "1",
                    :minor nil,
                    :incremental nil,
                    :qualifier nil}
          actual (parse-maven-version "1")]
      (is (= actual expected) "Major-only, unqualified"))
    (let [expected {:format :major-only,
                    :major "1",
                    :minor nil,
                    :incremental nil,
                    :qualifier "SNAPSHOT"}
          actual (parse-maven-version "1-SNAPSHOT")]
      (is (= actual expected) "major-only, snapshot"))
    (let [expected {:format :major-only,
                    :major "1",
                    :minor nil,
                    :incremental nil,
                    :qualifier "b123"}
          actual (parse-maven-version "1-b123")]
      (is (= actual expected) "major-only, qualified"))
    (let [expected {:format :major-and-minor,
                    :major "1",
                    :minor "2",
                    :incremental nil,
                    :qualifier nil}
          actual (parse-maven-version "1.2")]
      (is (= actual expected) "major-minor unqualified"))
    (let [expected {:format :major-and-minor,
                    :major "1",
                    :minor "2",
                    :incremental nil,
                    :qualifier "SNAPSHOT"}
          actual (parse-maven-version "1.2-SNAPSHOT")]
      (is (= actual expected) "major-minor, snapshot"))
    (let [expected {:format :major-and-minor,
                    :major "1",
                    :minor "2",
                    :incremental nil,
                    :qualifier "b123"}
          actual (parse-maven-version "1.2-b123")]
      (is (= actual expected) "major-minor, qualified"))
    (let [expected {:format :major-minor-and-incremental,
                    :major "1",
                    :minor "2",
                    :incremental "3",
                    :qualifier nil}
          actual (parse-maven-version "1.2.3")]
      (is (= actual expected) "major-minor-patch"))
    (let [expected {:format :major-minor-and-incremental,
                    :major "1",
                    :minor "2",
                    :incremental "3",
                    :qualifier "SNAPSHOT"}
          actual (parse-maven-version "1.2.3-SNAPSHOT")]
      (is (= actual expected) "major-minor-patch, snapshot"))
    (let [expected {:format :major-minor-and-incremental,
                    :major "1",
                    :minor "2",
                    :incremental "3",
                    :qualifier "b123"}
          actual (parse-maven-version "1.2.3-b123")]
      (is (= actual expected) "major-minor-patch, qualified"))
    (let [expected {:format :major-minor-and-incremental,
                    :major "1",
                    :minor "2",
                    :incremental "3",
                    :qualifier "rc1"}
          actual (parse-maven-version "1.2.3-rc1")]
      (is (= actual expected) "major-minor-patch, release candidate"))))


 (deftest compute-next-development-version-tests
   (testing "compute-next-development-version"
     (let [expected "2-SNAPSHOT"
           actual (compute-next-development-version "1")]
           (is (= actual expected) "Major-version only"))
     (let [expected "2-SNAPSHOT"
           actual (compute-next-development-version "1-SNAPSHOT")]
           (is (= actual expected) "Major-only, snapshot"))
     (let [expected "2-SNAPSHOT"
           actual (compute-next-development-version "1-rc1")]
           (is (= actual expected) "Major-only, release candidate"))
     (let [expected "1.2-SNAPSHOT"
           actual (compute-next-development-version "1.1")]
           (is (= actual expected) "major-minor, unqualified"))
     (let [expected "1.2-SNAPSHOT"
           actual (compute-next-development-version "1.1-SNAPSHOT")]
           (is (= actual expected) "major-minor, snapshot"))
     (let [expected "1.2.3-SNAPSHOT"
           actual (compute-next-development-version "1.2.2")]
           (is (= actual expected) "major-minor-patch, unqualified"))
     (let [expected "1.2.3-SNAPSHOT"
           actual (compute-next-development-version "1.2.2-rc3")]
           (is (= actual expected) "major-minor-patch, release candidate"))))


 (deftest compute-release-version-tests
   (testing "compute-release-version"
    (let [expected "1.0.116"
          actual (compute-release-version "1.0.116-SNAPSHOT")]
           (is (= actual expected) "vanilla"))
    (let [expected "1.0.116-v2"
          actual (binding [get-release-qualifier (fn [] "-v2")]
                    (compute-release-version "1.0.116-SNAPSHOT"))]
           (is (= actual expected) "with bound release qualifier"))))


 (deftest replace-project-version-tests
   (testing "replace-project-version"
   (binding [*project-file-name* "test-resources/test-project.clj"
             *project-file-out* "target/test-project-0.1.1.clj"]
     (let [expected "0.1.1"
           actual (nth
                   (read-string
                    (replace-project-version "0.1.0-SNAPSHOT" "0.1.1"))
                    2)]
       (is (= actual expected) "major-minor-patch, snapshot"))
     (let [expected "0.1.1"
           actual (do
                    (set-project-version! "0.1.0-SNAPSHOT" "0.1.1")
                    (nth (read-string (slurp *project-file-out*)) 2))]
       (is (= actual expected) "testing update in file")))))

