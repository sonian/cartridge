(ns cartridge.core
  (:require [clj-http.client :as http]
            [clojure.core.incubator :refer [dissoc-in]]
            [clojure.data.codec.base64 :as b64]
            [clojure.java.io :refer [file writer copy]]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.reader.edn :as edn])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream InputStream)))

(defn b64-decode
  "b64 decodes a utf8 string."
  [string]
  (b64/decode (.getBytes string "utf8")))

(defn b64-encode
  "b64 encodes a collection of bytes as utf8."
  [bytes]
  (String. (b64/encode bytes) clojure.lang.RT/UTF8))

(defn stash-stream-body
  "If the response body is an InputStream, it is fully read into a byte array
  and stashed at ::body-content in the response map"
  [{:keys [body] :as resp}]
  (if (instance? InputStream body)
    (let [baos (ByteArrayOutputStream.)]
      (copy body baos)
      (assoc resp ::body-content (.toByteArray baos)))
    resp))

(defn wrap-playback-request
  "clj-http middleware to record http responses or playback extant recordings"
  [saved-responses-atom key-fn client]
  (fn [req]
    (let [key (key-fn req)
          cached-resp (get @saved-responses-atom key)
          resp (or cached-resp
                   (let [resp (-> (client req)
                                  ;; don't need to record the java obj
                                  ;; http-req if it's there
                                  (dissoc-in [:request :http-req])
                                  ;; realize a stream body into a byte array
                                  stash-stream-body)]
                     (swap! saved-responses-atom assoc key resp)
                     resp))]
      (if-let [content (::body-content resp)]
        ;; refresh the body input stream, if the body was a stream
        (assoc resp :body (ByteArrayInputStream. content))
        resp))))

(defn wrap-save-raw-request
  "clj-http middleware that preserves the raw request"
  [client]
  (fn [req]
    (client (assoc req :raw-request req))))

(defn deserialize-body
  "Reads and reconstitutes the body"
  [resp]
  (let [{:keys [type content]} (:body resp)]
    (condp = type
      :nil (assoc resp :body nil)
      :string (assoc resp :body content)
      :stream (assoc (dissoc resp :body) ::body-content (b64-decode content))
      (assoc resp :body (b64-decode content)))))

(defn serialize-body
  "Preps the body for writing to disk.  Strings and nil pass through
  untouched, everything else is base64 encoded."
  [{:keys [body] :as resp}]
  (if-let [content (::body-content resp)]
    (assoc (dissoc resp ::body-content)
      :body {:type :stream
             :content (b64-encode content)})
    (assoc resp
      :body (cond
             (nil? body) {:type :nil :content nil}
             (string? body) {:type :string :content body}
             :else {:type :b64 :content (b64-encode body)}))))

(defn transform-responses
  "Takes a map of responses, applying transform-fn to each response"
  [responses transform-fn]
  (zipmap (keys responses)
          (map transform-fn (vals responses))))

(defn save-responses-to-disk
  "Encodes the body of the responses and saves them at the given
  path interpreted as a file."
  [path responses]
  (with-open [output-writer (writer (file path))]
    (pprint (transform-responses responses serialize-body) output-writer)))

(defn read-responses-from-disk
  "reads responses from a path via edn/read-string"
  [path]
  (let [response-file (file path)]
    (when (.exists response-file)
      (transform-responses (edn/read-string (slurp path)) deserialize-body))))

(defn saved-request-key
  "Keys a request for storage. Default `key-fn`"
  [req]
  (-> (:raw-request req)
      ((juxt :url :query-params :method :body :headers))
      set))

(defmacro with-cartridge
  "Accepts an atom, optional `key-fn` and body. Records clj-http
  request/response pairs into the atom. If a key-fn is passed, that
  is used to key the request for storage. Your atom should be a map."
  [[saved-responses-atom & [key-fn]] & body]
  (let [key-fn (or key-fn saved-request-key)]
    `(http/with-middleware (concat [(partial wrap-playback-request
                                             ~saved-responses-atom
                                             ~key-fn)]
                                   http/*current-middleware*
                                   [wrap-save-raw-request])
       ~@body)))

(defn cartridge-playback-fixture
  "Meant to be used as a once fixture, this function wraps our recording
  middlewares around subsequent clj-http requests, writing the responses to
  output-path"
  ([output-path]
     (cartridge-playback-fixture output-path saved-request-key))
  ([output-path key-fn]
     (fn [func]
       (let [cached-responses (read-responses-from-disk output-path)
             saved-responses-atom (atom cached-responses)]
         (with-cartridge [saved-responses-atom key-fn]
           (func))
         (when-not (= cached-responses @saved-responses-atom)
           (save-responses-to-disk output-path @saved-responses-atom))))))
