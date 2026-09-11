import { ApiProblemException, type ProblemDetails } from '../types/problem-details';

const BASE_API_URL = import.meta.env.VITE_PUBLIC_API_BASE_URL || '/api/v1';

export async function apiClient<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${BASE_API_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

  const defaultHeaders: HeadersInit = {
    'Content-Type': 'application/json',
    Accept: 'application/json, application/problem+json',
  };

  const response = await fetch(url, {
    ...options,
    headers: {
      ...defaultHeaders,
      ...options.headers,
    },
  });

  if (!response.ok) {
    let errorBody: ProblemDetails;
    try {
      errorBody = await response.json();
    } catch {
      errorBody = {
        title: response.statusText || 'Lỗi mạng',
        status: response.status,
        detail: 'Không thể kết nối hoặc phân giải dữ liệu từ máy chủ.',
      };
    }
    throw new ApiProblemException(errorBody);
  }

  return (await response.json()) as T;
}
