import { useState } from "react";
import { Building2, ChevronLeft, ChevronRight, Images } from "lucide-react";
import { Dialog } from "@/shared/ui/Dialog";
export function ListingGallery({
  images,
  title,
}: {
  images: string[];
  title: string;
}) {
  const [open, setOpen] = useState(false),
    [selected, setSelected] = useState(0);
  const [failed, setFailed] = useState<Set<string>>(new Set());
  const show = (index: number) => {
    setSelected(index);
    setOpen(true);
  };
  const move = (amount: number) =>
    setSelected((index) => (index + amount + images.length) % images.length);
  const picture = (index: number, hero = false) =>
    failed.has(images[index]) ? (
      <span className="grid h-full w-full place-items-center p-4 text-sm text-on-surface-variant">
        Không tải được ảnh {index + 1}
      </span>
    ) : (
      <img
        src={images[index]}
        alt={`${title} — ảnh ${index + 1}`}
        className="h-full w-full object-cover"
        loading={hero ? "eager" : "lazy"}
        {...{ fetchpriority: hero ? "high" : "auto" }}
        decoding="async"
        onError={() =>
          setFailed((previous) => new Set(previous).add(images[index]))
        }
      />
    );
  if (!images.length)
    return (
      <div
        className="grid aspect-[2/1] place-items-center rounded-2xl bg-surface-container text-on-surface-variant"
        role="img"
        aria-label="Tin đăng chưa có ảnh"
      >
        <Building2 className="h-16 w-16" aria-hidden="true" />
      </div>
    );
  return (
    <section aria-label="Thư viện ảnh bất động sản">
      <div className="relative grid gap-2 overflow-hidden rounded-2xl sm:grid-cols-4 sm:grid-rows-2">
        <button
          type="button"
          aria-label="Mở ảnh 1"
          className={`aspect-[4/3] overflow-hidden bg-surface-container sm:row-span-2 ${images.length > 1 ? "sm:col-span-2" : "sm:col-span-4 sm:aspect-[21/9]"}`}
          onClick={() => show(0)}
        >
          {picture(0, true)}
        </button>
        {images.slice(1, 5).map((url, index) => (
          <button
            type="button"
            key={`${url}-${index}`}
            aria-label={`Mở ảnh ${index + 2}`}
            onClick={() => show(index + 1)}
            className="hidden min-h-0 overflow-hidden bg-surface-container sm:block"
          >
            {picture(index + 1)}
          </button>
        ))}
        <button
          type="button"
          className="absolute bottom-4 right-4 inline-flex min-h-11 items-center gap-2 rounded-lg border bg-white px-4 text-sm font-semibold shadow-sm"
          onClick={() => show(0)}
        >
          <Images className="h-4 w-4" aria-hidden="true" />
          Xem {images.length} ảnh
        </button>
      </div>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Ảnh bất động sản"
        className="ndc-gallery-dialog"
      >
        <div
          className="p-3 sm:p-5"
          onKeyDown={(event) => {
            if (event.key === "ArrowRight") {
              event.preventDefault();
              move(1);
            }
            if (event.key === "ArrowLeft") {
              event.preventDefault();
              move(-1);
            }
          }}
        >
          <div className="relative h-[45dvh] overflow-hidden rounded-xl bg-surface-container sm:h-[55dvh]">
            {failed.has(images[selected]) ? (
              <div className="grid h-full place-items-center text-sm">
                Không tải được ảnh này. Hãy chọn ảnh khác.
              </div>
            ) : (
              <img
                src={images[selected]}
                alt={`${title} — ảnh ${selected + 1}`}
                className="h-full w-full object-contain"
                onError={() =>
                  setFailed((previous) =>
                    new Set(previous).add(images[selected]),
                  )
                }
              />
            )}
          </div>
          <div className="my-3 flex items-center justify-center gap-4">
            <button
              type="button"
              className="ndc-icon-button border"
              aria-label="Ảnh trước"
              onClick={() => move(-1)}
            >
              <ChevronLeft aria-hidden="true" />
            </button>
            <span className="min-w-24 text-center text-sm" aria-live="polite">
              Ảnh {selected + 1} / {images.length}
            </span>
            <button
              type="button"
              className="ndc-icon-button border"
              aria-label="Ảnh tiếp"
              onClick={() => move(1)}
            >
              <ChevronRight aria-hidden="true" />
            </button>
          </div>
          <div
            className="flex gap-2 overflow-x-auto pb-2"
            aria-label="Chọn ảnh"
          >
            {images.map((url, index) => (
              <button
                type="button"
                className="ndc-gallery-thumb w-20 shrink-0"
                key={`${url}-${index}`}
                aria-label={`Xem ảnh ${index + 1}`}
                aria-pressed={selected === index}
                onClick={() => setSelected(index)}
              >
                {picture(index)}
              </button>
            ))}
          </div>
        </div>
      </Dialog>
    </section>
  );
}
