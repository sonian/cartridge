(defproject sonian/cartridge "1.0.0"
  :description "HTTP response playback for clj-http"
  :url "https://www.github.com/sonian/cartridge"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/core.incubator "0.1.3"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/tools.reader "0.8.2"]
                 [clj-http "0.7.8"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [org.slf4j/slf4j-nop "1.7.5"]
                                  [ring/ring-jetty-adapter "1.2.1"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}}
  :aliases {"all" ["with-profile" "dev,1.4:dev"]}
  :test-selectors {:default  #(not (:integration %))
                   :integration :integration
                   :all (constantly true)})
