#!/usr/bin/env node
// Generates the API types of the frontend from the backend's OpenAPI snapshot (audit F22.4).
//
//   npm run gen:api      rewrite app/shared/api/generated/openapi.ts
//   npm run check:api    fail when the committed file differs from what the snapshot generates (CI)
//
// The snapshot is backend/src/test/resources/openapi/openapi.json, kept equal to the served /v3/api-docs by the
// backend test OpenApiSnapshotTests. A contract change therefore needs both files regenerated in the same commit.
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';
import * as prettier from 'prettier';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const snapshot = resolve(root, '../backend/src/test/resources/openapi/openapi.json');
const target = resolve(root, 'app/shared/api/generated/openapi.ts');
const check = process.argv.includes('--check');

const header = `/**
 * GENERATED FILE — do not edit. Source: backend/src/test/resources/openapi/openapi.json (npm run gen:api).
 * Use the aliases in app/shared/api/schema.ts rather than importing this file directly.
 */
`;

const ast = await openapiTS(pathToFileURL(snapshot), { alphabetize: true, exportType: true });
const options = { ...(await prettier.resolveConfig(target)), filepath: target };
const generated = await prettier.format(header + astToString(ast), options);

if (check) {
  let current = '';
  try {
    current = await readFile(target, 'utf8');
  } catch {
    // missing file is drift too
  }
  if (current !== generated) {
    console.error(
      'app/shared/api/generated/openapi.ts is out of date with the OpenAPI snapshot. Run `npm run gen:api` and commit.',
    );
    process.exit(1);
  }
  console.log('API types match the OpenAPI snapshot.');
} else {
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, generated);
  console.log(`Wrote ${target}`);
}
