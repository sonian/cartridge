(ns cartridge.test.core
  (:require [cartridge.core :refer :all :as cc]
            [clj-http.client :as http]
            [clojure.java.io :refer [file]]
            [clojure.test :refer :all]
            [ring.adapter.jetty :as ring])
  (:import (java.io ByteArrayInputStream)))

(defn handler [req]
  (condp = [(:request-method req) (:uri req)]
    [:get "/edn"]
    {:status 200 :body "{:foo \"bar\" :baz 7M :eggplant {:quux #{1 2 3}}}"
     :headers {"content-type" "application/edn"}}))

(defn run-server* []
  (ring/run-jetty handler {:port 0 :join? false}))

(def run-server (memoize run-server*))

(defn local-port [server]
  (.getLocalPort (first (.getConnectors server))))

(defn req-fn [port]
  (:body (http/get
          (str "http://localhost:" port "/edn"))))

(deftest ^:integration test-with-cartridge
  (let [server (run-server)
        cartridge-atom (atom {})]
    (with-cartridge [cartridge-atom (constantly :response)]
      (let [req-fn (partial req-fn (local-port server))]
        (is (= (req-fn)
               (String. (get-in @cartridge-atom
                                [:response ::cc/body-content]))))
        (testing "subsequent get calls retrieve the cached response"
          (swap! cartridge-atom assoc-in [:response ::cc/body-content]
                 (.getBytes "we changed it"))
          (is (= "we changed it"
                 (String. (req-fn)))))))))

(deftest ^:integration test-cartridge-playback-fixture
  (let [server (run-server)
        fixture-file-path "test/cartridge-responses"
        fixture-file (file fixture-file-path)
        fixture-fn (cartridge-playback-fixture fixture-file
                                               (constantly :response))
        req-fn (partial req-fn (local-port server))]
    (try
      (-> fixture-file .getParentFile .mkdirs)
      (fixture-fn req-fn)
      (is (.exists fixture-file))
      (let [responses (read-responses-from-disk fixture-file-path)]
        (is (= (req-fn)
               (String. (get-in responses [:response ::cc/body-content])))))
      (finally
        (.delete fixture-file)))))

(deftest test-wrap-save-raw-request
  (let [raw-request {:headers [] :body ""}
        client (wrap-save-raw-request identity)]
    (is (= raw-request (:raw-request (client raw-request))))))

(deftest test-wrap-playback-request
  (let [recordings (atom {})]
    (let [raw-request {:headers [] :body ""}
          request-key (constantly :response)
          client (wrap-playback-request recordings request-key identity)]
      (is (= raw-request (client raw-request)))
      (is (= raw-request (:response @recordings))))
    (let [bytes (.getBytes "foo")
          raw-request {:headers []
                       :body (ByteArrayInputStream. bytes)}
          request-key (constantly :stream-response)
          client (wrap-playback-request recordings request-key identity)]
      (is (= (-> raw-request
                 (dissoc :body)
                 (assoc ::cc/body-content (seq bytes)))
             (-> (client raw-request)
                 (dissoc :body)
                 (update-in [::cc/body-content] seq))))
      (let [resp (:stream-response @recordings)]
        (is (= raw-request (dissoc resp ::cc/body-content)))
        (is (= (seq bytes) (seq (::cc/body-content resp))))))))

(deftest test-b64
  (is (= "hello" (-> (.getBytes "hello")
                     b64-encode
                     b64-decode
                     (#(String. %))))))

(deftest test-transform-responses
  (let [responses {:foobar {:body (.getBytes "hello")}}]
    (is (= {:type :b64 :content "aGVsbG8="}
           (get-in (transform-responses responses serialize-body)
                   [:foobar :body])))))

(deftest test-serialization
  (is (nil? (:body (deserialize-body (serialize-body {:body nil})))))
  (is (= "hello" (:body (deserialize-body (serialize-body {:body "hello"})))))
  (let [bytes (.getBytes "hello")]
    (is (= {:type :b64 :content "aGVsbG8="}
           (:body (serialize-body {:body bytes}))))
    (is (= {:type :stream :content "aGVsbG8="}
           (:body (serialize-body {::cc/body-content bytes
                                   :body (ByteArrayInputStream. bytes)}))))
    (is (= {:type :stream :content "aGVsbG8="}
           (:body (serialize-body {::cc/body-content bytes}))))))

(deftest test-saving-and-reading
  (let [test-save-file (file (System/getProperty "java.io.tmpdir")
                             "cartridge-test")
        bytes (.getBytes "foobar")
        responses {:resp-1 {:body "foobar" :number 123}
                   :resp-2 {:body bytes :vector [3 2 1]}
                   :resp-3 {::cc/body-content bytes :vector [3 2 1]}}
        bytes-to-string (fn [{:keys [body] :as resp}]
                          (if-let [content (::cc/body-content resp)]
                            (assoc resp :body (String. content))
                            (if (string? body)
                              resp
                              (assoc resp :body (String. body)))))]
    (try
      (is (not (.exists test-save-file)) "The file should not exist yet.")
      (save-responses-to-disk test-save-file responses)
      (is (.exists test-save-file) "The file should have been written")
      (is (= (update-in (transform-responses responses bytes-to-string)
                        [:resp-3 ::cc/body-content] seq)
             (update-in (transform-responses
                         (read-responses-from-disk test-save-file)
                         bytes-to-string)
                        [:resp-3 ::cc/body-content] seq)))
      (finally
        (when (.exists test-save-file)
          (.delete test-save-file))))))
