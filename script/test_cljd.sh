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
         :sha "095e266b14457bb024e7cfce4cb1eb19a10b12df"}
        datascript/datascript {:local/root "../../"}}
 :cljd/opts {:main acme.unused
             :kind :dart}}
EOF

cd tmp/cljdtests
clojure -M -m cljd.build init
dart pub add -d test || true
clojure -A:cljd-dev -M -m cljd.build compile datascript.test.db datascript.test.conn datascript.test.index datascript.test.query datascript.test.transact datascript.test.entity datascript.test.filter datascript.test.ident datascript.test.tuples datascript.test.components datascript.test.components datascript.test.pull-api datascript.test.explode datascript.test.lookup-refs datascript.test.lru datascript.test.parser datascript.test.parser-find datascript.test.parser-rules datascript.test.parser-return-map datascript.test.parser-where datascript.test.pull-parser datascript.test.query-aggregates datascript.test.query-find-specs datascript.test.query-fns datascript.test.query-not datascript.test.query-or datascript.test.query-pull datascript.test.query-return-map datascript.test.query-rules datascript.test.validation datascript.test.issues datascript.test.listen 
dart test -p vm
