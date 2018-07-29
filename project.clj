(defproject lein-release/lein-release "1.0.10"
  :description "Leiningen Release Plugin"
  :license {:name "Eclipse Public License" ;; for compatibility with leiningen
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url         "https://github.com/relaynetwork/lein-release"
;;   :dev-dependencies [[swank-clojure "1.4.2"]]
;;   :repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]
  :eval-in :leiningen
  :lein-release {:scm :git
                 :build-uberjar true}

  :plugins [[lein-release "1.0.10-SNAPSHOT"]]
  ;; eating our own dogfood...

  :dependencies [[org.clojure/clojure "1.8.0"]])
