(ns cljsjs.boot-cljsjs.packaging
  {:boot/export-tasks true}
  (:require [boot.core           :as c]
            [boot.pod            :as pod]
            [boot.util           :as util]
            [boot.task.built-in  :as tasks]
            [clojure.edn :as edn]
            [clojure.java.io     :as io]
            [clojure.pprint      :as pprint]
            [clojure.string      :as string])
  (:import [java.security DigestInputStream MessageDigest]
           [javax.xml.bind DatatypeConverter]
           [java.util.zip ZipFile]))

(defn- realize-input-stream! [s]
  (loop [c (.read s)]
    (if-not (neg? c)
      (recur (.read s)))))

(defn- message-digest->str [^MessageDigest message-digest]
  (-> message-digest
      (.digest)
      (DatatypeConverter/printHexBinary)))

(def checksum-deprecated-message (atom false))

(c/deftask checksum
  [s sum FILENAME=CHECKSUM {str str} "Check the md5 checksum of file against md5"]
  (c/with-pre-wrap fileset
    (swap! checksum-deprecated-message
           (fn [x]
             (when-not x
               (util/warn (str "Download :checksum option is deprecated. Instead use validate-checksums task as the "
                               "last task in the package pipeline.\n")))
             true))
    (doseq [f (c/ls fileset)
            :let [path (c/tmp-path f)]]
      (when-let [checksum (some-> (get sum path) string/upper-case)]
        (with-open [is  (io/input-stream (c/tmp-file f))
                    dis (DigestInputStream. is (MessageDigest/getInstance "MD5"))]
          (realize-input-stream! dis)
          (let [real (message-digest->str (.getMessageDigest dis))]
            (if (not= checksum real)
              (throw (IllegalStateException. (format "Checksum of file %s in not %s but %s" path checksum real))))))))
    fileset))

(c/deftask unzip
  [p paths PATH #{str} "Paths in fileset to unzip"]
  (let [tmp (c/tmp-dir!)]
    (c/with-pre-wrap fileset
      (let [archives (filter (comp paths c/tmp-path) (c/ls fileset))]
        (doseq [archive archives
                :let [zipfile (ZipFile. (c/tmp-file archive))
                      entries (->> (.entries zipfile)
                                   enumeration-seq
                                   (remove #(.isDirectory %)))]]
          (util/info "Extracting %d files\n" (count entries))
          (doseq [entry entries
                  :let [target (io/file tmp (.getName entry))]]
            (io/make-parents target)
            (with-open [is (.getInputStream zipfile entry) ]
              (io/copy is target))))
        (-> fileset (c/rm archives) (c/add-resource tmp) c/commit!)))))

(def decompress-deps '[[org.apache.commons/commons-compress "1.14"]])

(c/deftask decompress
  [p paths PATH #{str} "Paths in fileset to untar"
   f compression-format FORMAT str "Compression format"
   F archive-format FORMAT str "Archive format"]
  (let [tmp (c/tmp-dir!)
        pod (future (pod/make-pod (-> (c/get-env) (update-in [:dependencies] into decompress-deps))))]
    (c/with-pre-wrap fileset
      (let [archives (filter (comp paths c/tmp-path) (c/ls fileset))]
        (doseq [archive archives]
          (pod/with-call-in @pod
            (cljsjs.impl.decompress/decompress-file ~(.getPath (c/tmp-file archive)) ~(.getPath tmp)
                                                    {:compression-format ~compression-format
                                                     :archive-format ~archive-format})))
        (-> fileset (c/rm archives) (c/add-resource tmp) c/commit!)))))

(def download-deps '[[clj-http "3.7.0"]])

(c/deftask download
  [u url      URL      str     "The url to download"
   n name     NAME     str     "Optional name for target file"
   c checksum CHECKSUM str     "Optional MD5 checksum of downloaded file"
   x unzip             bool    "Unzip the downloaded file"
   X decompress        bool    "Decompress the archive (tar, zip, gzip, bzip...)"
   f compression-format FORMAT str "Manually set format for decompression (e.g. lzma can't be autodetected)."
   F archive-format     FORMAT str "Manually set format for archive"
   t target   PATH     str     "Move the downloaded file to this path"]
  (let [tmp (c/tmp-dir!)
        pod (future (pod/make-pod (-> (c/get-env) (update-in [:dependencies] into download-deps))))
        fname (or name (last (string/split url #"/")))]
    (cond->
      (c/with-pre-wrap fileset
        (util/info "Downloading %s\n" fname)
        (pod/with-call-in @pod
          (cljsjs.impl.download/download ~url ~(.getPath tmp) ~fname))
        (-> fileset (c/add-resource tmp) c/commit!))
      checksum (comp (cljsjs.boot-cljsjs.packaging/checksum :sum {fname checksum}))
      unzip    (comp (cljsjs.boot-cljsjs.packaging/unzip :paths #{fname}))
      decompress (comp (cljsjs.boot-cljsjs.packaging/decompress :paths #{fname} :compression-format compression-format :archive-format archive-format))
      target (comp (tasks/sift :move {(re-pattern fname) target})))))

(defn- build-legacy-deps-cljs [in-files name provides requires global-exports no-externs]
  (let [regular  (first (c/by-ext [".inc.js"] (c/not-by-ext [".min.inc.js"] in-files)))
        minified (first (c/by-ext [".min.inc.js"] in-files))
        externs  (c/by-ext [".ext.js"] in-files)]

    (assert (or (seq provides) name) "Either list of provides or a name has to be provided.")
    (assert regular "No .inc.js file found!")

    (if-not no-externs
      (assert (first externs) "No .ext.js file(s) found!"))

    (let [base-lib {:file (c/tmp-path regular)
                    :provides (or provides [name])}
          lib      (cond-> base-lib
                     requires (assoc :requires requires)
                     minified (assoc :file-min (c/tmp-path minified))
                     global-exports (assoc :global-exports global-exports))]
      (merge {:foreign-libs [lib]}
             (if (seq externs)
               {:externs (mapv c/tmp-path externs)})))))

(defn- update-provides [provides matches]
  (into (empty provides)
        (map (fn [p]
               (apply format p matches))
             provides)))

(defn- update-global-exports [global-exports matches]
  (into (empty global-exports)
        (map (fn [[k v]]
               (let [k2 (apply format (name k) matches)
                     k2 (if (symbol? k)
                          (symbol k2)
                          k2)
                     v2 (apply format (name v) matches)
                     v2 (if (symbol? v)
                          (symbol v2)
                          v2)]
                 [k2 v2]))
             global-exports)))

(comment
  (update-provides ["cljsjs.hello.%s"] ["foo"])
  (update-provides ["%s" "cljsjs.hello.%2$s"] ["foo" "bar"])
  ;; Keep the type
  ;; Strings needed for cases with multiple /
  (update-global-exports {"hljs/languages/%1$s" 'hljs.%1$s} ["fi"]))

(defn- build-deps-cljs [in-files foreign-libs externs]
  (let [foreign-libs (mapcat (fn [{:keys [file file-min] :as lib}]
                               (let [files (if file (c/by-re [file] in-files))
                                     files-min (if file-min (c/by-re [file-min] in-files))]
                                 (assert (or (= (count files) (count files-min))
                                             (not file) (not file-min))
                                         "If both :file and :file-min are provided, they have to match the same number of files.")
                                 (map (fn [matched-file matched-file-min]
                                        (let [[_ & matches] (if matched-file
                                                              (re-find file (c/tmp-path matched-file))
                                                              (re-find file-min (c/tmp-path matched-file-min)))]
                                          (cond-> lib
                                            (seq matches) (update :provides update-provides matches)
                                            (seq matches) (update :global-exports update-global-exports matches)
                                            matched-file (assoc :file (c/tmp-path matched-file))
                                            matched-file-min (assoc :file-min (c/tmp-path matched-file-min)))))
                                      (if file files (repeat nil))
                                      (if file-min files-min (repeat nil)))))
                             foreign-libs)
        externs (mapcat (fn [re]
                          (c/by-re [re] in-files))
                        externs)]
    (merge {:foreign-libs (vec foreign-libs)}
           (if (seq externs)
             {:externs (mapv c/tmp-path externs)}))))

(c/deftask deps-cljs
  "Creates deps.cljs file based on \"template\" given, i.e. list of foreign-lib
  maps and extern paths. When :file, :file-min or :externs paths are regex,
  the pattern is used to match files in the fileset.
  If :file and :file-min match groups are used to format :provides names, and generate
  multiple entries if pattern matches many files.
  All files matched by :externs patterns are included.
  Check e.g. cljsjs/highlight for example.

  Note that you should take system directory separator char into account in
  `foreign-libs` `:file` and `:file-min` regex, i.e. use `[/\\]` instead of just `/`.

  Legacy version: single foreign lib can be declared using given name,
  provides, requires, global-exports and no-externs options.
  The first .inc.js file is passed as :file, similarily .min.inc.js
  is passed as :file-min. Files ending in .ext.js are passed as :externs."
  [f foreign-libs FOREIGN-LIBS edn "Template for foreign-lib entries"
   e externs EXTERNS edn "Extern paths"

   ;; Legacy options
   n name NAME str "Name for provided foreign lib"

   p provides PROV [str] "Modules provided by this lib"
   R requires REQ [str] "Modules required by this lib"
   g global-exports GLOBAL {sym sym} ""
   E no-externs bool "No externs are provided"]
  (let [tmp              (c/tmp-dir!)
        deps-file        (io/file tmp "deps.cljs")
        legacy-opts? (and (nil? foreign-libs) (nil? externs))]

    (assert (or (and (nil? provides) (nil? requires) (nil? global-exports) (nil? no-externs))
                (and (nil? foreign-libs) (nil? externs)))
            "Use only foreign-libs and externs options, or the legacy options, not both.")

    (c/with-pre-wrap fileset
      (let [in-files (c/input-files fileset)
            data (if legacy-opts?
                   (build-legacy-deps-cljs in-files name provides requires global-exports no-externs)
                   (build-deps-cljs in-files foreign-libs externs))
            s (with-out-str (pprint/pprint data))]
        (util/info (str "deps.cljs:\n" s))
        (spit deps-file s)
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(defn minifier-pod []
  (pod/make-pod (assoc-in (c/get-env) [:dependencies] '[[asset-minifier "0.2.6"]])))

(c/deftask minify
  "Minifies .js and .css files based on their file extension

   Note that you should take system directory separator char into account in
  `in` regex, i.e. use `[/\\]` instead of just `/`.

   NOTE: potentially slow when called with watch or multiple times"
  [i in  INPUT  str "Path to file to be compressed"
   o out OUTPUT str "Path to where compressed file should be saved"
   l lang-in LANGUAGE_IN kw "Language of the input javascript file. Default value is ecmascript6"
   L lang-out LANGUAGE_OUT kw "Language of the input javascript file. Default value is ecmascript5"]
  (assert in "Path to input file required")
  (assert out "Path to output file required")
  (let [tmp      (c/tmp-dir!)
        out-file (io/file tmp out)
        min-pod  (minifier-pod)]
    (c/with-pre-wrap fileset
      (let [in-files (c/input-files fileset)
            in-file  (c/tmp-file (first (c/by-re [(re-pattern in)] in-files)))
            in-path  (.getPath in-file)
            out-path (.getPath out-file)]
        (util/info "Minifying %s\n" (.getName in-file))
        (io/make-parents out-file)
        (cond
          (. in-path (endsWith "js"))
          (pod/with-eval-in min-pod
            (require 'asset-minifier.core)
            (asset-minifier.core/minify-js ~in-path ~out-path (if ~lang
                                                                {:language-in ~lang-in
                                                                 :language-out (or ~lang-out ~lang-in)}
                                                                {})))
          (. in-path (endsWith "css"))
          (pod/with-eval-in min-pod
            (require 'asset-minifier.core)
            (asset-minifier.core/minify-css ~in-path ~out-path)))
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(c/deftask replace-content
  "Replaces portion of a file matching some pattern with some value.

   Note that you should take system directory separator char into account in
  `in` regex, i.e. use `[/\\]` instead of just `/`."
  [i in INPUT str "Path to file to be modified"
   m match MATCH regex "Pattern to match"
   v value VALUE str "Value to replace with"
   o out OUTPUT str "Path to where modified file should be saved"]
  (assert in "Path to input file required")
  (let [tmp      (c/tmp-dir!)
        out-file (io/file tmp (or out in))]
    (c/with-pre-wrap fileset
      (let [in-files (c/input-files fileset)
            in-file  (c/tmp-file (first (c/by-re [(re-pattern in)] in-files)))
            in-path  (.getPath in-file)]
        (util/info "Replacing content of %s\n" (.getName in-file))
        (io/make-parents out-file)
        (spit out-file (string/replace (slurp in-file) match value))
        (-> fileset
            (c/add-resource tmp)
            c/commit!)))))

(def checksum-re #"^cljsjs[/\\].*\.inc\.js$")

(comment
  (re-matches checksum-re "cljsjs/foo/common/foo.inc.js")
  (re-matches checksum-re "cljsjs/foo/common/modules/foo.inc.js")
  (re-matches checksum-re "cljsjs/foo/common/foo.ext.js")
  (re-matches checksum-re "cljsjs/common/foo.inc.js")
  (re-matches checksum-re "cljsjs\\common\\foo.inc.js")
  )

(c/deftask validate-checksums
  "Checks files (by default Cljsjs JS files)
  against `boot-cljsjs-checksums.edn` files in
  working directory, if it exists. If there are differences,
  asks the user to validate changes, or in CI, throw error.
  New checksum are written to the file.

  Default pattern to check is \"^cljsjs[/\\].*\\.inc\\.js$\".

  Note that you should take system directory separator char into account in
  `patterns` regex, i.e. use `[/\\]` instead of just `/`.

  The checksum file should be commited to git."
  [_ patterns PATTERN [regex] "File patterns to check the checksums for"]
  (let [patterns (if (seq patterns)
                  patterns
                  [checksum-re])]
    (fn [next-handler]
      (fn [fileset]
        (let [files (->> fileset
                         c/input-files
                         (c/by-re patterns))
              checksums-file (io/file "boot-cljsjs-checksums.edn")
              current-checksums (if (.exists checksums-file)
                                  (edn/read-string (slurp checksums-file)))
              new-checksums (reduce (fn [m f]
                                      (let [checksum (with-open [is  (io/input-stream (c/tmp-file f))
                                                                 dis (DigestInputStream. is (MessageDigest/getInstance "MD5"))]
                                                       (realize-input-stream! dis)
                                                       (message-digest->str (.getMessageDigest dis)))]
                                        (assoc m (c/tmp-path f) checksum)))
                                    (sorted-map)
                                    files)
              ci? (= "true" (System/getenv "CIRCLECI"))]
          (if (and current-checksums (not= current-checksums new-checksums))
            (do
              (util/info (str "\nCurrent checksums:\n" (with-out-str (pprint/pprint current-checksums) "\n")))
              (util/info (str "\nNew checksums:\n" (with-out-str (pprint/pprint new-checksums)) "\n"))
              (if-not ci?
                (util/warn "Checksums have changed, update? [yn] "))
              (let [answer (and (not ci?) (.readLine (System/console)))]
                (if (not= "y" answer)
                  (throw (ex-info "Checksums do not match" {})))))
            (util/info "Checksums match\n"))
          (if (not= current-checksums new-checksums)
            (util/warn "Checksum file boot-cljsjs-checksums.edn updated, please commit this file to Git.\n"))
          (spit checksums-file (with-out-str (pprint/pprint new-checksums)))
          (next-handler fileset))))))

(defn cljs-pod []
  (pod/make-pod (-> (c/get-env)
                    (update-in [:dependencies] into '[[org.clojure/clojurescript "1.9.946"]])
                    (assoc :resource-paths #{}
                           :directories #{}))))

(c/deftask validate-libs
  []
  (let [pod (cljs-pod)]
    (fn [next-handler]
      (fn [fileset]
        (util/info "Running externs and foreign-libs through Closure to validate them...\n")

        ;; React and other multi package builds probably have conflicting deps.cljs files,
        ;; conflicts can be avoided by building classpath manually, no directories,
        ;; just the built jars in addition to dependencies to the classpath.
        (let [jars (->> fileset
                        (c/output-files)
                        (c/by-ext [".jar"]))]
          (assert (seq jars) "Validate-libs needs to be run after the jar has been built.")
          (doseq [jar jars]
            (pod/with-call-in pod
              (boot.pod/add-classpath ~(.getPath (c/tmp-file jar))))))

        (pod/with-call-in pod
          (cljsjs.impl.closure/validate-externs!))

        (next-handler fileset)))))

(c/deftask validate
  []
  (comp
    (validate-libs)
    (validate-checksums)))

;; TODO: Should eventually be included in boot.core
(defn with-files
  "Runs middleware with filtered fileset and merges the result back into complete fileset."
  [p middleware]
  (fn [next-handler]
    (fn [fileset]
      (let [merge-fileset-handler (fn [fileset']
                                    (next-handler (c/commit! (assoc fileset :tree (merge (:tree fileset) (:tree fileset'))))))
            handler (middleware merge-fileset-handler)
            fileset (assoc fileset :tree (reduce-kv
                                          (fn [tree path x]
                                            (if (p x)
                                              (assoc tree path x)
                                              tree))
                                          (empty (:tree fileset))
                                          (:tree fileset)))]
        (handler fileset)))))

(c/deftask run-commands
  "Runs given commands with fileset checked out as working directory, and commits the working directory to fileset
  after running commands."
  [c commands COMMAND edn "Commands"]
  (let [tmp (c/tmp-dir!)]
    (c/with-pre-wrap fileset
      (doseq [f (->> fileset c/input-files)
              :let [target  (io/file tmp (c/tmp-path f))]]
        (io/make-parents target)
        (io/copy (c/tmp-file f) target))
      (binding [util/*sh-dir* (str tmp)]
        (doseq [command commands]
          ((apply util/sh command))))
      (-> fileset (c/add-resource tmp) c/commit!))))
