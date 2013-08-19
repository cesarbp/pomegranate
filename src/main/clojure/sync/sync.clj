(ns main.clojure.sync.sync
  (:refer-clojure :exclude [read])
  (:require [cemerick.pomegranate :as pom :refer [add-dependencies]]
            [cemerick.pomegranate.aether :as aether :refer [maven-central]]
            [clojure.edn :as edn]
            [clojure.java.io :as io :only [reader]]
            [leiningen.core.project :refer [normalize-values read]]
            [leiningen.core.user :refer [resolve-credentials]]))

(defn deps->maps
  [deps]
  (reduce (fn [maps [name version & opts]]
            (merge maps
                   {name (if opts
                           (merge {:version version}
                                  (apply hash-map opts))
                           {:version version})}))
          {}
          deps))

(defn repos->maps
  [repos]
  (reduce (fn [maps [name opts]]
            (merge maps
                   {name opts}))
          {}
          repos))

(defn get-repos-deps
  [project & profiles]
  (let [project (normalize-values project)
        main-deps (:dependencies project)
        joined-deps (reduce (fn [acc profile]
                              (merge acc (-> project profile :dependencies deps->maps)))
                            (deps->maps main-deps)
                            profiles)
        actual-deps (mapv (fn [[name {version :version :as opts}]]
                            (reduce into
                                    [name version]
                                    (for [[k v] (dissoc opts :version)]
                                      [k v])))
                          joined-deps)

        main-repos (:repositories project)
        joined-repos (reduce (fn [acc profile]
                               (merge acc (-> project profile :dependencies repos->maps)))
                             (repos->maps main-repos)
                             profiles)
        actual-repos (zipmap (keys joined-repos)
                             (map resolve-credentials (vals joined-repos)))]

    {:coordinates actual-deps
     :repositories actual-repos}))

(defn sync!
  [& profiles]
  (let [project (if profiles
                  (read "project.clj" profiles)
                  (read "project.clj"))
        {:keys [coordinates repositories]} (apply get-repos-deps project profiles)]
    (add-dependencies :coordinates coordinates
                      :repositories repositories)))
