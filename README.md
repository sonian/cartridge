# cartridge

Cartridge is a library for recording & replaying your clj-http responses.
```
.-___-.,_______________.----------------------------------------------___-.
|     | |              |  |                                         |     |\
|     | |--------------|  |                                         |     | |
|     | |              |  |     ____          _                     |     | |
|     | |--------------|  |    / __/__  ___  (_)__ ____             |     | |
|     | |              |  |   _\ \/ _ \/ _ \/ / _ `/ _ \            |     | |
|     `----------------'  |  /___/\___/_//_/_/\_,_/_//_/            |     | |
|     |                |  |   _____         __      _    __         |     | |
|     |================|  |  / ___/__ _____/ /_____(_)__/ /__ ____  |     | |
|     |                |  | / /__/ _ `/ __/ __/ __/ / _  / _ `/ -_) |     | |
|     |================|  | \___/\_,_/_/  \__/_/ /_/\_,_/\_, /\__/  |     | |
|     |                |  |                             /___/       |     | |
|     |================|  |                                         |     | |
|     |                |  |                                         |     | |
|     |================|  |                                         |     | |
|     |                |  |                                         |     | |
|     |================|  |                                         |     | |
|     |                |  |                                         |     | |
|     |================|  `-----------------------------------------'     | |
|     |                |                                                  | |
|     |================|      ______                                      | |
|     |                |      \    /                                      | |
|     |================|       \  /                                       | |
`--.  |                |        \/                                     .--'\|
  \|  |================|                                               |\--'
   |  |                |                                               | |
   |  |----------------|                                               | |
   |  |::::::::::::::::|                                               | |
   |  |::::::::::::::::|                                               | |
   |  |::::::::::::::::|                                               | |
   .-------------------------------------------------------------------. |
    \___________________________________________________________________\'
```

## Installation

`cartridge` is available as a Maven artifact from
[Clojars](http://clojars.org/sonian/cartridge):

```clojure
[sonian/cartridge "1.0.0"]
```

Cartridge is compatible with clojure 1.4+

## Usage

The main cartridge functionality is provided by the `cartridge.core`
namespace.  Require it in the REPL:

```clojure
(require '[cartridge.core :as cartridge])
```

Require it in your application:

```
(ns my-app.core
  (:require [cartidge.core :as cartridge]))
```
### General Process

1. A person with access to the HTTP API would run the tests and record
   the responses with Cartridge into a known location inside the
   project directory.
2. They would then check the Cartridge file into source control so
   that other developers have access to it.
3. Subsequent test runs by that or other users will not need to access
   the HTTP API until new API calls are added and need to be recorded.

### Recording

There are two primary points of entry for cartridge, `with-cartridge`
and `cartridge-playback-fixture`.

If you'd like to record responses for a group of unit tests and don't
require any custom behavior, just use `cartridge-playback-fixture` as
a :once fixture (in clojure.test parlance) and pass in a path:

```clojure
(use-fixtures :once
  (cartridge-playback-fixture (file "path-for-recorded-responses")))
```

This will record your responses to the specified file. On subsequent
requests, if a recorded response exists for the given request it will
be used instead of reaching out to the network.

The second interface for cartridge is the `with-cartridge` macro.
Unlike `cartridge-playback-fixture`, `with-cartridge` doesn't actually
write to disk, it simply records all your responses into an atom. If
you needed to write your own fixture function for example, you could
use `with-cartridge`.

```clojure
(def recordings (atom {}))

(with-cartridge [recordings]
  (comment "body full of clj-http calls here..."))
```

The `recordings` atom should now be full of your recorded responses.
If you wish to save the file to disk, `save-responses-to-disk` is
available to do so.

### Customization

By default, saved responses are stored in a map where the key is based
on the HTTP request.  The information that is used from the request is
`:url`, `:query-params`, `:method`, `:body`, and `:headers`. This is
handled by the `saved-request-key` function and can be overridden.

Should you need custom behavior for the request keys (e.g., removing
presence of something like a timestamp or an auth token header),
you're able to pass a `key-fn` to cartridge. In the
`cartridge-playback-fixture` scenario, this would look something like
the following:

```clojure
(defn my-key-fn [request]
  (-> (:raw-request req)
      (dissoc-in [:headers :auth-token])
      (cartridge/saved-request-key request)))

(use-fixtures :once
  (cartridge-playback-fixture (file "recording-path") my-key-fn))
```

`with-cartridge` also accepts and optional `key-fn`, just like
`cartridge-playback-fixture`.
  
```clojure
(with-cartridge [recordings my-key-fn]
  (comment "body full of clj-http calls here..."))
```
You can return whatever you'd like, so long as it provides a unique
value to associate a request with a response.

## License

Copyright © 2013 Sonian, Inc.

Cartridge is released under the
[Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html).
