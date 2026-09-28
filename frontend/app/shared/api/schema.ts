/**
 * Entry point to the API types generated from the backend's OpenAPI snapshot (audit F22.4; `npm run gen:api`).
 *
 * The backend documents most response members as optional (springdoc cannot see which record components are never
 * null), so generated response types are used through {@link Present} or checked against the hand-written view types
 * in `contract.ts`; request bodies carry their Bean Validation `required` members and are used as generated.
 */
import type { components, paths } from './generated/openapi';

export type Schemas = components['schemas'];
export type Schema<Name extends keyof Schemas> = Schemas[Name];
export type ApiPaths = paths;

/** A generated response type whose members the backend always sends. */
export type Present<T> = { [Key in keyof T]-?: NonNullable<T[Key]> };
