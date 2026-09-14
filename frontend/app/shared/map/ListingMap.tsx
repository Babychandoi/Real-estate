import { useEffect, useRef, useState } from 'react';
import * as maplibregl from 'maplibre-gl';
import type { GeoJSONSource, Map as MlMap } from 'maplibre-gl';
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';
import { formatPriceVnd, type Listing } from '@/entities/listing/model/types';
import { listingPath } from '@/entities/listing/model/seo';

export type MapBounds = { minLat: number; maxLat: number; minLng: number; maxLng: number };
type MapMouseEvent = maplibregl.MapMouseEvent & { features?: GeoJSON.Feature[] };

maplibregl.setWorkerUrl(maplibreWorkerUrl);

const listingCollection = (items: Listing[]): GeoJSON.FeatureCollection => ({
  type: 'FeatureCollection',
  features: items
    .filter((item) => item.publicLatitude != null && item.publicLongitude != null)
    .map((item) => ({
      type: 'Feature' as const,
      properties: { id: item.id },
      geometry: { type: 'Point' as const, coordinates: [item.publicLongitude!, item.publicLatitude!] },
    })),
});

function listingPopupContent(listing: Listing): HTMLDivElement {
  const content = document.createElement('div');
  content.style.cssText = 'width:min(272px,calc(100vw - 48px));overflow:hidden;font-family:inherit;color:#0f2742';
  const image = document.createElement('img');
  image.src = listing.primaryImageUrl;
  image.alt = `Ảnh ${listing.title}`;
  image.loading = 'lazy';
  image.style.cssText = 'display:block;width:100%;height:112px;object-fit:cover;background:#e8eef7';
  image.onerror = () => image.remove();
  const body = document.createElement('div');
  body.style.cssText = 'padding:12px';
  const price = document.createElement('p');
  price.textContent = formatPriceVnd(listing.priceVnd);
  price.style.cssText = 'margin:0 0 4px;font-size:18px;font-weight:800;line-height:1.25;color:#004b7a';
  const title = document.createElement('a');
  title.href = listingPath(listing);
  title.textContent = listing.title;
  title.style.cssText = 'display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical;color:#10253f;font-size:14px;font-weight:700;line-height:1.4;text-decoration:none';
  title.setAttribute('aria-label', `Xem chi tiết ${listing.title}`);
  const address = document.createElement('p');
  address.textContent = listing.addressSummary;
  address.style.cssText = 'overflow:hidden;margin:6px 0 10px;color:#4a5d73;font-size:12px;line-height:1.35;text-overflow:ellipsis;white-space:nowrap';
  const detail = document.createElement('a');
  detail.href = listingPath(listing);
  detail.textContent = 'Xem chi tiết';
  detail.style.cssText = 'display:flex;min-height:38px;align-items:center;justify-content:center;border-radius:8px;background:#004b7a;color:#fff;font-size:13px;font-weight:700;text-decoration:none';
  detail.setAttribute('aria-label', `Xem chi tiết ${listing.title}`);
  body.append(price, title, address, detail);
  content.append(image, body);
  return content;
}

export function ListingMap({ listings, selectedId, onSearchArea }: { listings: Listing[]; selectedId?: string | null; onSearchArea: (bounds: MapBounds) => void }) {
  const host = useRef<HTMLDivElement>(null);
  const map = useRef<MlMap>();
  const listingsRef = useRef(listings);
  listingsRef.current = listings;
  const [bounds, setBounds] = useState<MapBounds>();

  useEffect(() => {
    if (!host.current || map.current) return;
    const instance = new maplibregl.Map({ container: host.current, center: [105.82, 21.03], zoom: 10, style: 'https://tiles.openfreemap.org/styles/positron' });
    map.current = instance;
    instance.addControl(new maplibregl.NavigationControl(), 'top-right');
    instance.on('load', () => {
      instance.addSource('listings', { type: 'geojson', data: listingCollection(listingsRef.current), cluster: true, clusterMaxZoom: 14, clusterRadius: 50 });
      instance.addLayer({ id: 'clusters', type: 'circle', source: 'listings', filter: ['has', 'point_count'], paint: { 'circle-color': '#047857', 'circle-radius': ['step', ['get', 'point_count'], 20, 10, 28, 50, 36], 'circle-stroke-color': '#fff', 'circle-stroke-width': 2 } });
      instance.addLayer({ id: 'cluster-count', type: 'symbol', source: 'listings', filter: ['has', 'point_count'], layout: { 'text-field': ['get', 'point_count_abbreviated'], 'text-size': 12 }, paint: { 'text-color': '#fff' } });
      instance.addLayer({ id: 'points', type: 'circle', source: 'listings', filter: ['!', ['has', 'point_count']], paint: { 'circle-color': '#f59e0b', 'circle-radius': 9, 'circle-stroke-color': '#fff', 'circle-stroke-width': 3 } });
      instance.on('click', 'clusters', async (event: MapMouseEvent) => {
        const feature = instance.queryRenderedFeatures(event.point, { layers: ['clusters'] })[0];
        const zoom = await (instance.getSource('listings') as GeoJSONSource).getClusterExpansionZoom(Number(feature.properties?.cluster_id));
        instance.easeTo({ center: (feature.geometry as GeoJSON.Point).coordinates as [number, number], zoom });
      });
      instance.on('click', 'points', (event: MapMouseEvent) => {
        const feature = event.features?.[0];
        const listing = listingsRef.current.find((item) => item.id === String(feature?.properties?.id ?? ''));
        if (feature && listing) new maplibregl.Popup({ maxWidth: '296px', offset: 14 }).setLngLat((feature.geometry as GeoJSON.Point).coordinates as [number, number]).setDOMContent(listingPopupContent(listing)).addTo(instance);
      });
    });
    instance.on('moveend', () => {
      const currentBounds = instance.getBounds();
      setBounds({ minLat: currentBounds.getSouth(), maxLat: currentBounds.getNorth(), minLng: currentBounds.getWest(), maxLng: currentBounds.getEast() });
    });
    return () => { instance.remove(); map.current = undefined; };
  }, []);

  useEffect(() => {
    const source = map.current?.getSource('listings') as GeoJSONSource | undefined;
    if (source) source.setData(listingCollection(listings));
  }, [listings]);

  useEffect(() => {
    if (!selectedId || !map.current) return;
    const listing = listings.find((item) => item.id === selectedId);
    if (listing?.publicLatitude != null && listing.publicLongitude != null) map.current.easeTo({ center: [listing.publicLongitude, listing.publicLatitude], duration: 350 });
  }, [selectedId, listings]);

  return <div className="absolute inset-0 z-30"><div ref={host} className="h-full w-full" aria-label="Bản đồ tin đăng tương tác"/><button disabled={!bounds} onClick={() => bounds && onSearchArea(bounds)} className="absolute top-4 left-1/2 -translate-x-1/2 min-h-11 px-5 rounded-full bg-white text-slate-900 font-bold shadow-xl border border-slate-200 disabled:opacity-60">Tìm trong khu vực này</button></div>;
}
