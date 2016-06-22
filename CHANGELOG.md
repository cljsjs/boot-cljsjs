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
