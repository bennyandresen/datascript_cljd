#!/bin/bash
set -o errexit -o nounset -o pipefail
cd "`dirname $0`/.."
PATH="$PWD/.fvm/flutter_sdk/bin:$PATH"

rm -rf tmp/cljdtests
mkdir -p tmp/cljdtests/src/cljd
cat > tmp/cljdtests/deps.edn <<EOF
{:paths ["src" "../../test"] ; where your cljd files are
 :deps {org.clojure/clojure {:mvn/version "1.11.1"}
        tensegritics/clojuredart
        {:git/url "https://github.com/tensegritics/ClojureDart.git"
         :sha "64a773790eaa85f7ac56546ccd15eb9a73c4ffe8"}
        datascript/datascript {:local/root "../../"}}
 :cljd/opts {:main acme.unused
             :kind :dart}}
EOF

cd tmp/cljdtests
clojure -M -m cljd.build init
dart pub add -d test || true
clojure -M -m cljd.build compile datascript.test.db
dart test -p vm

