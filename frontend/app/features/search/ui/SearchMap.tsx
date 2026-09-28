import { useEffect, useRef, useState } from 'react';
import * as maplibregl from 'maplibre-gl';
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';
import { listingV2Api } from '@/entities/listing/api/listingV2Api';
import type { MapCluster, MapPoint, MapResponseV2 } from '@/entities/listing/model/v2';
import { propertyTypeLabel } from '@/entities/listing/model/v2';
import { formatMoney } from '@/shared/format/money';
import { canonicalBbox, MAX_BBOX_SPAN, toApiParams, type BBox, type SearchFilters } from '../filterSchema';

maplibregl.setWorkerUrl(maplibreWorkerUrl);

/** Hà Nội when neither a place nor a previous viewport is known. */
const DEFAULT_CENTER: [number, number] = [105.8342, 21.0278];

export interface SearchMapProps {
  filters: SearchFilters;
  selectedId: string | null;
  /** The visitor panned/zoomed: the new viewport becomes the `bbox` filter (history replace, F03.4). */
  onViewportChange: (bbox: BBox) => void;
  onSelectPoint: (point: MapPoint) => void;
  className?: string;
}

const round5 = (value: number) => Math.round(value * 100_000) / 100_000;

function viewportOf(map: maplibregl.Map): BBox {
  const bounds = map.getBounds();
  return [round5(bounds.getWest()), round5(bounds.getSouth()), round5(bounds.getEast()), round5(bounds.getNorth())];
}

const tooWide = (box: BBox) => box[2] - box[0] > MAX_BBOX_SPAN || box[3] - box[1] > MAX_BBOX_SPAN;

/**
 * Result map (F02.3, F15.1): loaded with a dynamic import only when the map is shown. Asks the map endpoint for the
 * current viewport and zoom with the same filters as the list — points when zoomed in, server grid clusters
 * otherwise — so it never downloads every listing.
 */
export default function SearchMap({ filters, selectedId, onViewportChange, onSelectPoint, className }: SearchMapProps) {
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markers = useRef<maplibregl.Marker[]>([]);
  const [viewport, setViewport] = useState<{ bbox: BBox; zoom: number } | null>(null);
  const [data, setData] = useState<MapResponseV2 | null>(null);
  const [status, setStatus] = useState<'idle' | 'loading' | 'error' | 'too-wide'>('idle');
  const programmatic = useRef(false);
  const onViewportChangeRef = useRef(onViewportChange);
  onViewportChangeRef.current = onViewportChange;
  const onSelectRef = useRef(onSelectPoint);
  onSelectRef.current = onSelectPoint;

  // Create the map once.
  useEffect(() => {
    if (!container.current) return;
    const initial = filters.bbox;
    const map = new maplibregl.Map({
      container: container.current,
      style: 'https://tiles.openfreemap.org/styles/positron',
      center: DEFAULT_CENTER,
      zoom: 11,
      attributionControl: { compact: true },
      ...(initial
        ? {
            bounds: [
              [initial[0], initial[1]],
              [initial[2], initial[3]],
            ] as [[number, number], [number, number]],
          }
        : {}),
    });
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
    mapRef.current = map;
    let timer = 0;
    const update = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        const bbox = viewportOf(map);
        setViewport({ bbox, zoom: Math.round(map.getZoom()) });
        if (!programmatic.current && !tooWide(bbox)) onViewportChangeRef.current(bbox);
        programmatic.current = false;
      }, 350);
    };
    map.on('load', update);
    map.on('moveend', update);
    return () => {
      window.clearTimeout(timer);
      markers.current.forEach((marker) => marker.remove());
      map.remove();
      mapRef.current = null;
    };
    // The map is created once; later bbox changes are applied by the effect below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // A place chosen outside the map (search box, URL): move there without echoing it back as a user move.
  const bboxKey = filters.bbox ? canonicalBbox(filters.bbox) : '';
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !filters.bbox) return;
    const current = viewportOf(map);
    if (canonicalBbox(current) === bboxKey) return;
    programmatic.current = true;
    map.fitBounds(
      [
        [filters.bbox[0], filters.bbox[1]],
        [filters.bbox[2], filters.bbox[3]],
      ],
      { duration: 600, padding: 24 },
    );
    // bboxKey is the canonical text of filters.bbox.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bboxKey]);

  // Load points/clusters for the viewport with the list filters.
  const filterKey = toApiParams({ ...filters, bbox: undefined }).toString();
  useEffect(() => {
    if (!viewport) return;
    if (tooWide(viewport.bbox)) {
      setStatus('too-wide');
      setData(null);
      return;
    }
    const abort = new AbortController();
    setStatus('loading');
    const params = toApiParams({ ...filters, bbox: viewport.bbox });
    params.set('zoom', String(Math.max(3, Math.min(20, viewport.zoom))));
    listingV2Api
      .map(params, abort.signal)
      .then((response) => {
        setData(response);
        setStatus('idle');
      })
      .catch(() => {
        if (!abort.signal.aborted) setStatus('error');
      });
    return () => abort.abort();
    // filterKey captures the filters that matter (bbox comes from the viewport).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterKey, viewport]);

  // Draw markers.
  useEffect(() => {
    const map = mapRef.current;
    markers.current.forEach((marker) => marker.remove());
    markers.current = [];
    if (!map || !data) return;
    if (data.mode === 'points') {
      data.points.forEach((point) =>
        markers.current.push(pointMarker(map, point, point.id === selectedId, onSelectRef)),
      );
    } else {
      data.clusters.forEach((cluster) => markers.current.push(clusterMarker(map, cluster, programmatic)));
    }
  }, [data, selectedId]);

  return (
    <div className={className ?? 'relative h-full min-h-80 w-full'}>
      <div ref={container} className="h-full w-full" role="region" aria-label="Bản đồ kết quả tìm kiếm" />
      <p
        role="status"
        className="pointer-events-none absolute left-3 top-3 max-w-[70%] rounded-pill bg-surface-container-lowest/95 px-3 py-1.5 text-label text-on-surface shadow-sm"
      >
        {status === 'too-wide'
          ? 'Phóng to bản đồ để xem tin trong khu vực'
          : status === 'loading'
            ? 'Đang tải bản đồ…'
            : status === 'error'
              ? 'Không tải được dữ liệu bản đồ'
              : data?.total
                ? `${data.total.relation === 'gte' ? 'Hơn ' : ''}${data.total.value.toLocaleString('vi-VN')} tin trong vùng này`
                : ''}
      </p>
    </div>
  );
}

function pointMarker(
  map: maplibregl.Map,
  point: MapPoint,
  selected: boolean,
  onSelect: React.MutableRefObject<(point: MapPoint) => void>,
) {
  const button = document.createElement('button');
  button.type = 'button';
  const price = formatMoney(point.price);
  button.textContent = price || propertyTypeLabel(point.propertyType);
  button.setAttribute('aria-label', `${propertyTypeLabel(point.propertyType)}, giá ${price || 'chưa có'}`);
  button.setAttribute('aria-pressed', String(selected));
  button.dataset.listingId = point.id;
  button.className = selected
    ? 'min-h-8 rounded-pill border-2 border-surface-container-lowest bg-primary px-2 text-label font-bold text-primary-on shadow-elevated'
    : 'min-h-8 rounded-pill border border-primary bg-surface-container-lowest px-2 text-label font-bold text-primary shadow-card';
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    onSelect.current(point);
  });
  return new maplibregl.Marker({ element: button }).setLngLat([point.lng, point.lat]).addTo(map);
}

function clusterMarker(map: maplibregl.Map, cluster: MapCluster, programmatic: React.MutableRefObject<boolean>) {
  const button = document.createElement('button');
  button.type = 'button';
  button.textContent = cluster.count.toLocaleString('vi-VN');
  button.setAttribute('aria-label', `${cluster.count} tin trong cụm; phóng to để xem`);
  const size = Math.min(64, 32 + Math.round(Math.log10(cluster.count + 1) * 12));
  button.style.width = `${size}px`;
  button.style.height = `${size}px`;
  button.className =
    'grid place-items-center rounded-pill border-2 border-surface-container-lowest bg-primary/90 text-label font-bold text-primary-on shadow-elevated';
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    programmatic.current = false;
    const [minLng, minLat, maxLng, maxLat] = cluster.bbox;
    if (maxLng - minLng < 1e-4 && maxLat - minLat < 1e-4) map.flyTo({ center: [cluster.lng, cluster.lat], zoom: 16 });
    else
      map.fitBounds(
        [
          [minLng, minLat],
          [maxLng, maxLat],
        ],
        { padding: 48, maxZoom: 16, duration: 600 },
      );
  });
  return new maplibregl.Marker({ element: button }).setLngLat([cluster.lng, cluster.lat]).addTo(map);
}
