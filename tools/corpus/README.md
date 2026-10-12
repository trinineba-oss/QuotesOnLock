# Building the quote collection

`app/src/main/res/raw/quotes.txt` is generated, not typed by hand, so that every quote is wording that really
appears in a public-domain source (or is a clearly labelled translation).

* `picks/*.txt` list, per source book, which sentences or paragraphs were chosen (numbers refer to the
  sentence/paragraph numbering produced by `cands.py` / `paras.py` / `stanzas.py`).
* `build.py`, `build_paras.py`, `build_stanzas.py` (and `build_lines.py`, for picks typed out as whole passages and checked against the source) cut the chosen text out of the source file verbatim and apply
  light cleanup (dashes, emphasis markers, OCR repairs for *Sand and Foam*).
* `picks/malte_tr.tsv` and `rilke_translations_poems.txt` hold the English translations of Rilke's German
  (marked "tr. Claude" in the app). Each is checked against the German original it was made from.
* `assemble.py` merges everything with `base_quotes.txt` (the first, hand-checked set), removes duplicates and writes
  the final file.

The source texts are not stored in this repository. They come from Project Gutenberg (ebook numbers in
[SOURCES.md](../../SOURCES.md)) and the Internet Archive; download them into `src/` as `pg<number>.txt` to rerun.
