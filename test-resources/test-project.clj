(defproject lein-release/lein-release "0.1.0-SNAPSHOT"
  :description "Leiningen Release Plugin"
  :url         "https://github.com/relaynetwork/lein-release"
  :dev-dependencies [[swank-clojure "1.4.2"]]
  :repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]
  :eval-in :leiningen
  :lein-release {:scm :git
                 :build-uberjar true}

  :dependencies [[org.clojure/clojure "1.8.0"]])
