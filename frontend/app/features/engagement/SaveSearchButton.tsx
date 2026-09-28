import { lazy, Suspense, useState } from 'react';
import { BellPlus } from 'lucide-react';
import type { SearchFilters } from '@/features/search/filterSchema';
import { useAuth } from '@/shared/auth/AuthContext';
import { Button } from '@/shared/ui/Button';

const SaveSearchDialog = lazy(() => import('./ui/SaveSearchDialog'));

/** "Lưu tìm kiếm" on the search page; the dialog (and its API code) loads on first use. */
export function SaveSearchButton({ filters }: { filters: SearchFilters }) {
  const { user, setIsLoginModalOpen } = useAuth();
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button
        variant="outline"
        size="sm"
        leftIcon={<BellPlus className="h-4 w-4" aria-hidden="true" />}
        onClick={() => (user ? setOpen(true) : setIsLoginModalOpen(true))}
        onMouseEnter={() => void import('./ui/SaveSearchDialog')}
        onFocus={() => void import('./ui/SaveSearchDialog')}
      >
        Lưu tìm kiếm
      </Button>
      {open && (
        <Suspense fallback={null}>
          <SaveSearchDialog filters={filters} onClose={() => setOpen(false)} />
        </Suspense>
      )}
    </>
  );
}
