// Type declarations for the synthetic UI-preview fixture server (fixtures.mjs).
export const USER_ID: string;
export const SELLER_ID: string;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const listings: Array<Record<string, any>>;
export function photo(index: number): string;
export type FixtureResponse = {
  status: number;
  body?: unknown;
  text?: string;
  contentType?: string;
};
export function createFixtureApi(role?: string): (rawUrl: string, method?: string, body?: unknown) => FixtureResponse;
