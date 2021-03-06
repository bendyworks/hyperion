(ns hyperion.riak-spec
  (:require [speclj.core :refer :all ]
            [hyperion.api :refer :all ]
            [hyperion.log :as log]
            [hyperion.dev.spec :refer [it-behaves-like-a-datastore]]
            [hyperion.riak.spec-helper :refer [with-testable-riak-datastore]]
            [hyperion.riak :refer :all ]
            [clojure.data.codec.base64 :refer [encode decode]])
  (:import [com.basho.riak.client.raw.query.indexes BinValueQuery BinRangeQuery IntValueQuery IntRangeQuery]))

(hyperion.log/error!)

; Required Configuration:
; Add the following to the riak_kv section of Riak's config.
; {delete_mode, immediate}
; This will force Riak to immediately delete keys, instead of keeping them around for a while
; http://lists.basho.com/pipermail/riak-users_lists.basho.com/2011-October/006048.html

(defentity :testing
  [name]
  [first-name]
  [inti :type java.lang.Integer]
  [data])

(defentity :other-testing
  [name]
  [first-name]
  [inti :type java.lang.Integer]
  [data])

(defentity :types
  [bool]
  [bite :type java.lang.Byte]
  [shrt :type java.lang.Short]
  [inti :type java.lang.Integer]
  [lng :type java.lang.Long]
  [flt :type java.lang.Float]
  [dbl :type java.lang.Double]
  [str]
  [chr :type java.lang.Character]
  [kwd :type clojure.lang.Keyword])

(describe "Riak Datastore"

  (before (log/error!))

  (context "Connection Config"
    (it "configures PBC connection"
      (let [config-map {:api :pbc
                        :host "foo.bar.com"
                        :port 123
                        :connection-timeout-millis 234
                        :idle-connection-ttl-millis 345
                        :initial-pool-size 5
                        :pool-size 6
                        :socket-buffer-size-kb 456}
            config (build-connection-config config-map)]
        (should= "com.basho.riak.client.raw.pbc.PBClientConfig" (.getName (class config)))
        (should= "foo.bar.com" (.getHost config))
        (should= 123 (.getPort config))
        (should= 234 (.getConnectionWaitTimeoutMillis config))
        (should= 345 (.getIdleConnectionTTLMillis config))
        (should= 5 (.getInitialPoolSize config))
        (should= 6 (.getPoolSize config))
        (should= 456 (.getSocketBufferSizeKb config))))

    (it "configures HTTP connection"
      (let [http-client (org.apache.http.impl.client.DefaultHttpClient.)
            retry-handler (org.apache.http.impl.client.DefaultHttpRequestRetryHandler.)
            config-map {:api :http
                        :host "foo.bar.com"
                        :port 123
                        :http-client http-client
                        :mapreduce-path "map/reduce"
                        :max-connections 234
                        :retry-handler retry-handler
                        :riak-path "riak/path"
                        :scheme "https"
                        :timeout 345}
            config (build-connection-config config-map)]
        (should= "com.basho.riak.client.raw.http.HTTPClientConfig" (.getName (class config)))
        (should= "https://foo.bar.com:123/riak/path" (.getUrl config))
        (should= http-client (.getHttpClient config))
        (should= "map/reduce" (.getMapreducePath config))
        (should= 234 (.getMaxConnections config))
        (should= retry-handler (.getRetryHandler config))
        (should= 345 (.getTimeout config))))


    (it "configures HTTP connection using :url option"
      (let [config-map {:api :http
                        :url "https://foo.bar.com:123/riak/path"}
            config (build-connection-config config-map)]
        (should= "com.basho.riak.client.raw.http.HTTPClientConfig" (.getName (class config)))
        (should= "https://foo.bar.com:123/riak/path" (.getUrl config))))

    )

  (context "PBC client"

    (with client (open-client :api :pbc ))
    (after (try (.shutdown @client) (catch Exception e)))

    (it "creating a PBC client"
      (should= "com.basho.riak.client.raw.pbc.PBClientAdapter" (.getName (class @client)))
      (should-not-throw (.ping @client)))
    )

  (context "HTTP client"

    (with client (open-client :api :http ))
    (after (try (.shutdown @client) (catch Exception e)))

    (it "creating an HTTP client"
      (should= "com.basho.riak.client.raw.http.HTTPClientAdapter" (.getName (class @client)))
      (should-not-throw (.ping @client)))
    )

  (context "Creating a riak datastore"

    (it "with a client"
      (let [client (open-client :api :http )]
        (try
          (let [ds (new-riak-datastore client)]
            (should= client (.client ds)))
          (finally (.shutdown client)))))

    (it "with options"
      (let [ds (new-riak-datastore :api :http)]
        (try
          (should-not= nil (.client ds))
          (finally (.shutdown (.client ds))))))

    (it "using factory"
      (let [ds (new-datastore :implementation :riak :api :http)]
        (try
          (should-not= nil (.client ds))
          (finally (.shutdown (.client ds))))))
    )

  (context "Live"
    (with-testable-riak-datastore)

    (it-behaves-like-a-datastore)
    )
  )


