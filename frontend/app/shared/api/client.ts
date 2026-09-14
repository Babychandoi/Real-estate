import { ApiProblemException, type ProblemDetails } from '../types/problem-details';

const BASE_API_URL = import.meta.env.VITE_PUBLIC_API_BASE_URL || '/api/v1';
const TOKEN_KEY = 'bds_access_token';

export const setAccessToken = (token: string) => sessionStorage.setItem(TOKEN_KEY, token);
export const clearAccessToken = () => sessionStorage.removeItem(TOKEN_KEY);

export async function apiFetch(endpoint: string, options: RequestInit = {}): Promise<Response> {
  const url = endpoint.startsWith('http') || endpoint.startsWith('/api/') ? endpoint : `${BASE_API_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
  const token = sessionStorage.getItem(TOKEN_KEY);
  const headers = new Headers(options.headers);
  if (!headers.has('Content-Type') && options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (!headers.has('Accept')) headers.set('Accept', 'application/json, application/problem+json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return fetch(url, { ...options, headers });
}

export async function apiClient<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const response = await apiFetch(endpoint, options);

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

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
