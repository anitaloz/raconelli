(defproject raconelli "0.1.0-SNAPSHOT"
  :description "Multiplayer racing game"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [http-kit "2.6.0"]
                 [org.clojure/data.json "2.4.0"]
                 [compojure "1.6.2"]]
  :main raconelli.server
  :profiles {:dev {:plugins [[cider/cider-nrepl "0.28.5"]]}}
  :repl-options {:init-ns raconelli.server})