import { apiClient } from './client';

export type GeocodePlace = {
  label: string;
  lat: number;
  lon: number;
  type: string;
  /** [minLat, maxLat, minLon, maxLon] when the provider knows the area extent. */
  bbox?: [number, number, number, number];
};

type RawPlace = { display_name: string; lat: string; lon: string; type?: string; boundingbox?: string[] };

const cache = new Map<string, GeocodePlace[]>();

export async function geocodePlaces(query: string, signal?: AbortSignal): Promise<GeocodePlace[]> {
  const key = query.trim().toLowerCase();
  if (key.length < 3) return [];
  const hit = cache.get(key);
  if (hit) return hit;
  const raw = await apiClient<RawPlace[]>(`/public/geocoding?q=${encodeURIComponent(query.trim())}`, { signal });
  const places = (raw ?? [])
    .map((item) => {
      const box = item.boundingbox?.map(Number);
      return {
        label: item.display_name.replace(/, (Việt Nam|Vietnam)$/, ''),
        lat: Number(item.lat),
        lon: Number(item.lon),
        type: item.type ?? 'place',
        bbox:
          box && box.length === 4 && box.every(Number.isFinite) ? (box as [number, number, number, number]) : undefined,
      };
    })
    .filter((place) => Number.isFinite(place.lat) && Number.isFinite(place.lon));
  cache.set(key, places);
  return places;
}
