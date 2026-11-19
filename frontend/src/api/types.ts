export type EnrichmentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: number;
  userId: string;
  email: string;
  role: 'CUSTOMER' | 'ADMIN';
}

export interface SavedProduct {
  id: string;
  retailer?: 'AMAZON' | 'FLIPKART' | 'MYNTRA' | 'MEESHO' | 'AJIO' | 'NYKAA_FASHION' | 'NYKAA' | 'CROMA' | 'UNSUPPORTED';
  retailerProductId?: string;
  variantKey?: string | null;
  variantLabel?: string | null;
  asin: string | null;
  originalUrl: string;
  canonicalUrl: string;
  parentAsin: string | null;
  title: string | null;
  brand: string | null;
  imageUrl: string | null;
  description?: string | null;
  imageUrls?: unknown;
  features?: unknown;
  categories?: unknown;
  specifications?: unknown;
  manufacturer?: string | null;
  metadataSource?: 'URL_ONLY' | 'EXTENSION' | 'SCRAPEDO' | 'AXESSO';
  initialPrice: number | null;
  finalPrice: number | null;
  currency: string | null;
  discountPercentage: number | null;
  rating: number | null;
  reviewsCount: number | null;
  availability: string | null;
  enrichmentStatus: EnrichmentStatus;
  enrichmentErrorCategory: string | null;
  metadataFetchedAt: string | null;
  providerFetchedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiError {
  status: number;
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
}
