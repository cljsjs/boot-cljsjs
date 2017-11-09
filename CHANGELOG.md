## 0.8.2 (2017-11-09)

- Improve `validate-checksum` default patterns, and sort
checksum map to keep the content deterministic

## 0.8.1 (2017-09-28)

- Fix `decompress` with `tgz` files

## 0.8.0 (2017-09-28)

- Add new `validate-checksums` task
- Deprecate `package` `:checksum` option

## 0.7.3 (2017-09-28)

- Pretty print deps.cljs
- Print deps.cljs content to console

## 0.7.2 (2017-09-14)

- Add `:target` option to download task

## 0.7.1 (2017-09-06)

- Fix

## 0.7.0 (2017-09-03)

**[compare](https://github.com/cljsjs/boot-cljsjs/compare/0.6.0...0.7.0)**

- Adds support for providing `:provides` and `:global-exports` for `deps.cljs`

## 0.6.0 (1.2.2017)

**[compare](https://github.com/cljsjs/boot-cljsjs/compare/0.5.2...0.6.0)**

- Support single file archives in `decompress` ([#44](https://github.com/cljsjs/boot-cljsjs/issues/44))
- Remove `cljsjs.boot-cljsjs` namespace, including `from-cljsjs`, `from-jars` and
`from-webjars` tasks. Alternatives:
    - [Using non JS Clsjs assets](https://github.com/cljsjs/packages/wiki/Non-JS-Assets)
    - [ring-webjars](https://github.com/weavejester/ring-webjars)
    - [ring-cljsjs](https://github.com/Deraen/ring-cljsjs)
    - Boot `sift` task

## 0.5.2 (22.6.2016)

- Updated Clj-http to 2.2.0

## 0.5.1 (18.1.2016)

- added `lang` option to `minify` task to allow users to set input language for to-be-minified JS

## 0.4.8 (12.5.2015)

- Added general decompression task
  - Uses apache commons compress and should support all the most used
  compression and archive formats

## 0.4.7 (20.3.2015)

- Use `clj-http` to download files (knows how to handle HTTP redirects)

## 0.4.0 (6.1.2015)

- Removed deprecated js-import task
- Added packaging-ns which provides download-task to be used when creating
  new cljsjs libraries.

## 0.3.1 (5.1.2015)

- Main namespace is now `cljsjs.boot-cljsjs`
- Removed `:package` flag
  - It's responsibility of other tasks to add files to resource set if needed.
  - E.g. boot-cljs should add .inc.js files to resource set when doing `:optimizations :none` build.
