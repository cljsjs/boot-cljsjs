## 0.3.1 (x.x.201x)

- Added `:package` flag to `from-cljsjs` task.
  - `:package` flag can be used with package tasks where you don't want to
  include the files in result fileset (e.g. externs and preamble are only used
  by other tasks).
