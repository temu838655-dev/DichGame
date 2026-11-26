# Importing localization data

The importer reads local files only. It does not scrape websites, bypass access
controls, or assume that a public repository grants reuse rights.

Supported input formats:

- Excel (`.xlsx`): all worksheets are read. The `ID` column is preserved as `external_id`, and `Nhóm` is retained as context. Requires `python -m pip install -r knowledge_base/requirements.txt`.
- CSV: header row with source/target aliases such as \`source_text\` and
  \`translated_text\`, or \`english\` and \`vietnamese\`.
- JSON: an array of entry objects, or an object containing \`translations\`,
  \`entries\`, or \`data\`.
- PO: singular \`msgid\` / \`msgstr\` entries. Plural entries are skipped rather
  than guessing a plural form.
- XLIFF / XLF: XLIFF 1.x \`trans-unit\` and XLIFF 2.x \`unit\` elements.
- TMX: translation units with English and Vietnamese language variants.

## Example invocation

\`\`\`bash
python -m knowledge_base.import_cli \\
  --db ./translations.db \\
  --game-id your-existing-game-id \\
  --file ./authorized-translations.csv \\
  --source-name "Name supplied by the rights holder" \\
  --license-id "CC-BY-4.0" \\
  --source-url "https://source.example/project" \\
  --author "Author or team" \\
  --license-url "https://creativecommons.org/licenses/by/4.0/" \\
  --attribution "Required attribution text" \\
  --confirm-rights
\`\`\`

The example identifiers and URLs above are placeholders, not a real translation
source. Supply the actual license terms and attribution required by the source.

A non-empty license identifier and explicit \`--confirm-rights\` are mandatory.
The source is recorded as \`PENDING\`; this records the submitter's declaration,
not an independent legal verification. A reviewer must inspect the license,
source URL and attribution before changing its verification status. Unknown,
unlicensed, and all-rights-reserved sources are blocked.

Repeated imports skip already-present game/source-text/translation triples. If an external string ID is supplied, the importer preserves it and rejects conflicting content for the same game/ID instead of silently overwriting it.
The importer records SHA-256 of the input file and per-import counts in
\`import_jobs\`. It never marks imported community translations as VERIFIED.
