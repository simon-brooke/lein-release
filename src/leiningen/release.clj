(ns leiningen.release
  (:require
   [clojure.java.shell :as sh]
   [clojure.string     :as string])
  (:import
   [java.util.regex Pattern]))

(def ^:dynamic config {})

;; Make both where we read the project file from, and where we write it to
;; dynamically rebindable, for testing.
(def ^:dynamic *project-file-name* "project.clj")
(def ^:dynamic *project-file-out* *project-file-name*)

(defn raise [fmt & args]
  (throw (RuntimeException. (apply format fmt args))))

(def scm-systems
     {:git {:add    ["git" "add"]
            :tag    ["git" "tag"]
            :commit ["git" "commit"]
            :push   ["git" "push" "origin" "master"]
            :status ["git" "status"]
            :start-release ["git" "flow" "release" "start"]
            :finish-release ["git" "flow" "release" "finish"]}})


(defmacro compose-exception-reason
  "Compose and return a sensible reason message for this `exception`."
  [exception]
  `(string/join
    "\n\tcaused by: "
    (reverse
     (loop [ex# ~exception result# ()]
       (if-not (nil? ex#)
         (recur
          (.getCause ex#)
          (cons (str
                 (.getName (.getClass ex#))
                 ": "
                 (.getMessage ex#)) result#))
         result#)))))


(defn detect-scm []
  (or
   (:scm config)
   (cond
     (.exists (java.io.File. ".git"))
     :git
     :no-scm-detected
     (raise "Error: no scm detected! (I know only about git for now)."))))

(defn sh! [& args]
  (let [res (apply sh/sh args)]
    (.println System/out (:out res))
    (.println System/err (:err res))
    (when-not (zero? (:exit res))
      (raise "Error: command failed %s => %s" args res))))

(defn scm! [cmd & args]
  (let [scm   (detect-scm)
        scm-cmd (get-in scm-systems [scm cmd])]
    (if-not scm-cmd
      (raise "No such SCM command: %s in %s" cmd scm))
    (apply sh! (concat scm-cmd args))))

(def maven-version-regexes
     {:major-only                               #"(\d+)(?:-(.+))?"
      :major-and-minor                          #"(\d+)\.(\d+)(?:-(.+))?"
      :major-minor-and-incremental              #"(\d+)\.(\d+)\.(\d+)(?:-(.+))?"})

(defn parse-maven-version [vstr]
  ;; <MajorVersion [> . <MinorVersion [> . <IncrementalVersion ] ] [> - <BuildNumber | Qualifier ]>
  (cond
    (re-matches (:major-only maven-version-regexes) vstr)
    (let [[[_ major qualifier]] (re-seq (:major-only maven-version-regexes) vstr)]
      {:format      :major-only
       :major       major
       :minor       nil
       :incremental nil
       :qualifier   qualifier})

    (re-matches (:major-and-minor maven-version-regexes) vstr)
    (let [[[_ major minor qualifier]] (re-seq (:major-and-minor maven-version-regexes) vstr)]
      {:format      :major-and-minor
       :major       major
       :minor       minor
       :incremental nil
       :qualifier   qualifier})

    (re-matches (:major-minor-and-incremental maven-version-regexes) vstr)
    (let [[[_ major minor incremental qualifier]] (re-seq (:major-minor-and-incremental maven-version-regexes) vstr)]
      {:format      :major-minor-and-incremental
       :major       major
       :minor       minor
       :incremental incremental
       :qualifier   qualifier})
    :else
    {:format :not-recognized
     :major vstr}))


(defn ^:dynamic get-release-qualifier []
  (System/getenv "RELEASE_QUALIFIER"))


;; See: http://mojo.codehaus.org/versions-maven-plugin/version-rules.html
(defn compute-next-development-version [current-version]
  (let [parts             (vec (.split current-version "\\."))
        version-parts     (vec (take (dec (count parts)) parts))
        minor-version     (-> parts last (.split "\\D" 2) first Integer/parseInt)
        new-minor-version (str (inc minor-version) "-SNAPSHOT")]
    (string/join "." (conj version-parts new-minor-version))))


(defn replace-project-version
  "Replace the project version `old-vstring` with the value `new-vstring`
  in the content of the file at `*project-file-name*` (by default,
  `project.clj`) and return the content as a string (presumably, to
  preserve formatting?)"
  [old-vstring new-vstring]
  (let [proj-file     (slurp *project-file-name*)
        matcher       (.matcher
                       (Pattern/compile
                        (format "(\\(defproject .+? )\"\\Q%s\\E\"" old-vstring))
                       proj-file)]
    (if-not (.find matcher)
      (raise "Error: unable to find version string %s in project.clj file!" old-vstring))
    (.replaceFirst matcher (format "%s\"%s\"" (.group matcher 1) new-vstring))))


(defn set-project-version!
  "Replace the project version `old-vstring` with the value `new-vstring`
  in the content of the file at `*project-file-name*` (by default,
  `project.clj`) and write it to `*project-file-out` (by default, also
  `project.clj`)."
  [old-vstring new-vstring]
  (spit *project-file-out* (replace-project-version old-vstring new-vstring)))


(defn detect-deployment-strategy [project]
  (cond
    (:deploy-via config)
    (:deploy-via config)

    (:repositories project)
    :lein-deploy

    :no-deploy-strategy
    :lein-install))


(defn perform-deploy! [project project-jar]
  (case (detect-deployment-strategy project)
    :lein-deploy     (sh! "lein" "deploy")
    :lein-install    (sh! "lein" "install")
    :clojars         (sh! "lein" "deploy" "clojars")
    :shell           (apply sh! (:shell config))
    ;; default
    (raise "Error: unrecognized deploy strategy: %s" (detect-deployment-strategy))))

(defn extract-project-version-from-file
  ([]
     (extract-project-version-from-file *project-file-name*))
  ([proj-file]
     (let [s (slurp proj-file)
           m (.matcher (Pattern/compile "\\(defproject .+? \"([^\"]+?)\"") s)]
       (if-not (.find m)
         (raise "Error: unable to find project version in file: %s" proj-file))
       (.group m 1))))

(defn is-snapshot? [vstring]
  (string/ends-with? vstring "-SNAPSHOT"))

(defn compute-release-version [current-version]
  (str (string/replace current-version "-SNAPSHOT" "")
       (get-release-qualifier)))


(defn extract-git-flow-config
  [[line & lines]]
   (cond
    (empty? line) nil
    (= (string/trim line) "[gitflow \"branch\"]")
    (loop [[l & ls] lines result '()]
      (if
        (empty? l)
        (reverse result)
        (recur ls (cons l result))))
    true
    (extract-git-flow-config lines)))


(defn git-flow-initialised?
  "Returns true if the scm is git, and git flow is initialised."
  []
  (if
    (= :git (detect-scm))
    (let
      [git-flow-config (extract-git-flow-config
                        (string/split-lines
                         (slurp ".git/config")))]
      ;; TODO: check that config is complete
      (not (empty? git-flow-config)))))


(defn release [project & args]
  (binding [config (or (:lein-release project) config)]
    (try
    (if-let [current-version (get project :version)]
      (let
        [release-version  (compute-release-version current-version)
         next-dev-version (compute-next-development-version
                           (string/replace current-version "-SNAPSHOT" ""))
         target-dir       (:target-path
                           project
                           (:target-dir
                            project
                            (:jar-dir project "."))) ; target-path for lein2, target-dir or jar-dir for lein1
         jar-file-name    (format "%s/%s-%s.jar"
                                  target-dir
                                  (:name project)
                                  release-version)
         flow? (git-flow-initialised?)]
        (when (is-snapshot? current-version)
          (println (format "setting project version %s => %s"
                           current-version
                           release-version))
          (if
            flow?
            (scm! :start-release release-version))
          (set-project-version! current-version release-version)
          (println "adding, committing and tagging project.clj")
          (scm! :add "project.clj")
          (scm! :commit "--no-verify" "-m" (format "lein-release plugin: preparing %s release" release-version))
          (scm! :tag (format "%s-%s" (:name project) release-version)))
        (when-not (.exists (java.io.File. jar-file-name))
          (println "creating jar and pom files...")
          (sh! "lein" "jar")
          (sh! "lein" "pom"))
        (when (-> project :lein-release :build-uberjar)
          (println "creating uberjar")
          (sh! "lein" "uberjar"))
        (perform-deploy! project jar-file-name)
        (if flow? (scm! :finish-release release-version))
        (when-not (is-snapshot? (extract-project-version-from-file))
          (println (format "updating version %s => %s for next dev cycle" release-version next-dev-version))
          (set-project-version! release-version next-dev-version)
          (scm! :add "project.clj")
          (scm!
           :commit
           "-m"
           (format
            "lein-release plugin: bumped version from %s to %s for next development cycle"
            release-version
            next-dev-version))))
      (println "Error: failed to find :version in project"))
      (catch Exception any
        (compose-exception-reason any)))))
