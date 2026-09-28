import { useEffect, useRef } from 'react';
import * as maplibregl from 'maplibre-gl';
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';

maplibregl.setWorkerUrl(maplibreWorkerUrl);

interface LocationPickerProps {
  latitude: number | null;
  longitude: number | null;
  onChange: (latitude: number, longitude: number) => void;
}

/** Map for choosing the public (approximate) point. Loaded only on the location step (dynamic import, F15.2 budget). */
export default function LocationPicker({ latitude, longitude, onChange }: LocationPickerProps) {
  const host = useRef<HTMLDivElement>(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    if (!host.current) return;
    const hasPoint = latitude != null && longitude != null;
    const instance = new maplibregl.Map({
      container: host.current,
      style: 'https://tiles.openfreemap.org/styles/positron',
      center: hasPoint ? [longitude, latitude] : [105.8542, 21.0285],
      zoom: hasPoint ? 14 : 11,
    });
    instance.addControl(new maplibregl.NavigationControl(), 'top-right');
    let marker: maplibregl.Marker | null = null;
    const setPoint = (lng: number, lat: number, notify: boolean) => {
      marker ??= new maplibregl.Marker({ color: '#0f172a' });
      marker.setLngLat([lng, lat]).addTo(instance);
      if (notify) onChangeRef.current(Number(lat.toFixed(6)), Number(lng.toFixed(6)));
    };
    if (hasPoint) setPoint(longitude, latitude, false);
    instance.on('click', (event) => setPoint(event.lngLat.lng, event.lngLat.lat, true));
    return () => {
      marker?.remove();
      instance.remove();
    };
    // Mount-only: later points come from clicks on the same map instance.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      ref={host}
      className="mt-3 h-72 overflow-hidden rounded-lg border border-outline-variant"
      role="application"
      aria-label="Bản đồ chọn vị trí gần đúng. Bấm lên bản đồ để đặt điểm."
    />
  );
}
