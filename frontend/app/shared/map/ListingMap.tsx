import { useEffect, useRef, useState } from 'react';
import * as maplibregl from 'maplibre-gl';
import type { GeoJSONSource, Map as MlMap } from 'maplibre-gl';
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';
import { formatPriceVnd, type Listing } from '@/entities/listing/model/types';
import { listingPath } from '@/entities/listing/model/seo';
import type { GeocodePlace } from '@/shared/api/geocodingApi';

export type MapBounds = { minLat: number; maxLat: number; minLng: number; maxLng: number };
/** A place to fly to; `key` changes on every new request so re-selecting the same place still moves the map. */
export type MapFocus = GeocodePlace & { key: number };

const toBounds = (instance: MlMap): MapBounds => {
  const b = instance.getBounds();
  return { minLat: b.getSouth(), maxLat: b.getNorth(), minLng: b.getWest(), maxLng: b.getEast() };
};

const ZOOM_BY_TYPE: Record<string, number> = {
  house: 15,
  street: 15,
  locality: 14,
  district: 13.5,
  county: 12.5,
  city: 11.5,
  state: 10,
};

function applyFocus(
  instance: MlMap,
  focus: MapFocus,
  marker: { current?: maplibregl.Marker },
  onArrive: (bounds: MapBounds) => void,
) {
  marker.current?.remove();
  const popup = new maplibregl.Popup({ offset: 28, closeButton: false }).setText(focus.label);
  marker.current = new maplibregl.Marker({ color: '#dc2626' })
    .setLngLat([focus.lon, focus.lat])
    .setPopup(popup)
    .addTo(instance);
  instance.once('moveend', () => {
    onArrive(toBounds(instance));
    marker.current?.togglePopup();
  });
  const box = focus.bbox;
  const spansArea =
    box && Math.abs(box[1] - box[0]) > 0.002 && Math.abs(box[3] - box[2]) > 0.002 && Math.abs(box[1] - box[0]) < 3;
  if (box && spansArea)
    instance.fitBounds(
      [
        [box[2], box[0]],
        [box[3], box[1]],
      ],
      { padding: 60, maxZoom: 16, duration: 900 },
    );
  else instance.flyTo({ center: [focus.lon, focus.lat], zoom: ZOOM_BY_TYPE[focus.type] ?? 14, duration: 900 });
}
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
  title.style.cssText =
    'display:-webkit-box;overflow:hidden;-webkit-line-clamp:2;-webkit-box-orient:vertical;color:#10253f;font-size:14px;font-weight:700;line-height:1.4;text-decoration:none';
  title.setAttribute('aria-label', `Xem chi tiết ${listing.title}`);
  const address = document.createElement('p');
  address.textContent = listing.addressSummary;
  address.style.cssText =
    'overflow:hidden;margin:6px 0 10px;color:#4a5d73;font-size:12px;line-height:1.35;text-overflow:ellipsis;white-space:nowrap';
  const detail = document.createElement('a');
  detail.href = listingPath(listing);
  detail.textContent = 'Xem chi tiết';
  detail.style.cssText =
    'display:flex;min-height:38px;align-items:center;justify-content:center;border-radius:8px;background:#004b7a;color:#fff;font-size:13px;font-weight:700;text-decoration:none';
  detail.setAttribute('aria-label', `Xem chi tiết ${listing.title}`);
  body.append(price, title, address, detail);
  content.append(image, body);
  return content;
}

export function ListingMap({
  listings,
  focus,
  onSearchArea,
}: {
  listings: Listing[];
  focus?: MapFocus | null;
  onSearchArea: (bounds: MapBounds) => void;
}) {
  const host = useRef<HTMLDivElement>(null);
  const map = useRef<MlMap>();
  const loaded = useRef(false);
  const marker = useRef<maplibregl.Marker>();
  const listingsRef = useRef(listings);
  listingsRef.current = listings;
  const focusRef = useRef(focus);
  focusRef.current = focus;
  const onSearchAreaRef = useRef(onSearchArea);
  onSearchAreaRef.current = onSearchArea;
  const [bounds, setBounds] = useState<MapBounds>();

  useEffect(() => {
    if (!host.current || map.current) return;
    const initial = focusRef.current;
    const instance = new maplibregl.Map({
      container: host.current,
      center: initial ? [initial.lon, initial.lat] : [105.82, 21.03],
      zoom: 10,
      style: 'https://tiles.openfreemap.org/styles/positron',
    });
    map.current = instance;
    instance.addControl(new maplibregl.NavigationControl(), 'top-right');
    instance.on('load', () => {
      loaded.current = true;
      instance.addSource('listings', {
        type: 'geojson',
        data: listingCollection(listingsRef.current),
        cluster: true,
        clusterMaxZoom: 14,
        clusterRadius: 50,
      });
      instance.addLayer({
        id: 'clusters',
        type: 'circle',
        source: 'listings',
        filter: ['has', 'point_count'],
        paint: {
          'circle-color': '#047857',
          'circle-radius': ['step', ['get', 'point_count'], 20, 10, 28, 50, 36],
          'circle-stroke-color': '#fff',
          'circle-stroke-width': 2,
        },
      });
      instance.addLayer({
        id: 'cluster-count',
        type: 'symbol',
        source: 'listings',
        filter: ['has', 'point_count'],
        layout: { 'text-field': ['get', 'point_count_abbreviated'], 'text-size': 12 },
        paint: { 'text-color': '#fff' },
      });
      instance.addLayer({
        id: 'points',
        type: 'circle',
        source: 'listings',
        filter: ['!', ['has', 'point_count']],
        paint: {
          'circle-color': '#f59e0b',
          'circle-radius': 9,
          'circle-stroke-color': '#fff',
          'circle-stroke-width': 3,
        },
      });
      instance.on('click', 'clusters', async (event: MapMouseEvent) => {
        const feature = instance.queryRenderedFeatures(event.point, { layers: ['clusters'] })[0];
        const zoom = await (instance.getSource('listings') as GeoJSONSource).getClusterExpansionZoom(
          Number(feature.properties?.cluster_id),
        );
        instance.easeTo({ center: (feature.geometry as GeoJSON.Point).coordinates as [number, number], zoom });
      });
      instance.on('click', 'points', (event: MapMouseEvent) => {
        const feature = event.features?.[0];
        const listing = listingsRef.current.find((item) => item.id === String(feature?.properties?.id ?? ''));
        if (feature && listing)
          new maplibregl.Popup({ maxWidth: '296px', offset: 14 })
            .setLngLat((feature.geometry as GeoJSON.Point).coordinates as [number, number])
            .setDOMContent(listingPopupContent(listing))
            .addTo(instance);
      });
      if (focusRef.current) applyFocus(instance, focusRef.current, marker, (next) => onSearchAreaRef.current(next));
    });
    instance.on('moveend', () => setBounds(toBounds(instance)));
    return () => {
      instance.remove();
      map.current = undefined;
      loaded.current = false;
    };
  }, []);

  useEffect(() => {
    if (!focus || !map.current || !loaded.current) return;
    applyFocus(map.current, focus, marker, (next) => onSearchAreaRef.current(next));
    // A focus request is identified by its key, so re-renders with an equal focus object do not move the map.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focus?.key]);

  useEffect(() => {
    const source = map.current?.getSource('listings') as GeoJSONSource | undefined;
    if (source) source.setData(listingCollection(listings));
  }, [listings]);

  return (
    <div className="absolute inset-0 z-30">
      <div ref={host} className="h-full w-full" aria-label="Bản đồ tin đăng tương tác" />
      <button
        disabled={!bounds}
        onClick={() => bounds && onSearchArea(bounds)}
        className="absolute top-4 left-1/2 -translate-x-1/2 min-h-11 px-5 rounded-full bg-white text-slate-900 font-bold shadow-xl border border-slate-200 disabled:opacity-60"
      >
        Tìm trong khu vực này
      </button>
    </div>
  );
}
