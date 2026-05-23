/** Single envelope used by every Slice 1 endpoint (spec section 5). */
export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  meta: Record<string, unknown> | null;
}

export interface PageEnvelope<T> extends ApiResponse<T[]> {
  meta: {
    nextCursor: string;
    limit: number;
  } & Record<string, unknown>;
}

export interface ValidationErrorEnvelope extends ApiResponse<null> {
  meta: Record<string, string> & { code?: string };
}
