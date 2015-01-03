## 0.3.1 (x.x.201x)

- Main namespace is now `cljsjs.boot-cljsjs`
- Removed `:package` flag
  - It's responsibility of other tasks to add files to resource set if needed.
  - E.g. boot-cljs should add .inc.js files to resource set when doing `:optimizations :none` build.
