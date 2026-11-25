export const AMAD_API_URL =
  process.env.NEXT_PUBLIC_AMAD_API_URL ?? "http://localhost:8000";

export interface HealthResponse {
  status: string;
  service: string;
  timestamp: string;
  components: Record<string, string>;
}

export interface SourceSummary {
  id: string;
  name: string;
  engine: "MSSQL" | "POSTGRES";
  host: string;
  port: number;
  database: string;
  status: string;
  serverVersion: string | null;
  lastTestedAt: string | null;
  lastError: string | null;
}

export interface ProbeStep {
  step: number;
  name: string;
  status: string;
  durationMs: number;
  detail: string;
}

export interface ProbeResponse {
  connected: boolean;
  engine: string;
  serverVersion: string | null;
  steps: ProbeStep[];
  tables: { schema: string; name: string; type: string; estimatedRows: number }[];
  errorHint: string | null;
}

export interface ColumnSchema {
  ordinal: number;
  name: string;
  dataType: string;
  nullable: boolean;
}

export interface JobRecord {
  jobId: string;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  rowsProcessed: number;
  filesWritten: number;
  bytesWritten: number;
  destination: string;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface QueryResponse {
  columns: string[];
  rows: Record<string, unknown>[];
  rowCount: number;
  durationMs: number;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${AMAD_API_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
    cache: "no-store",
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export const amadApi = {
  health: () => request<HealthResponse>("/health"),
  sources: () => request<SourceSummary[]>("/sources"),
  testSource: (id: string) => request<ProbeResponse>(`/sources/${id}/test`, { method: "POST" }),
  tableSchema: (id: string, schema: string, table: string) =>
    request<{ columns: ColumnSchema[] }>(`/sources/${id}/tables/${table}/schema?schema=${encodeURIComponent(schema)}`),
  jobs: () => request<JobRecord[]>("/storage/jobs?limit=30"),
  submitJob: (body: Record<string, unknown>) =>
    request<JobRecord>("/storage/jobs", { method: "POST", body: JSON.stringify(body) }),
  retryJob: (id: string) => request<JobRecord>(`/storage/jobs/${id}/retry`, { method: "POST" }),
  cancelJob: (id: string) => request<JobRecord>(`/storage/jobs/${id}/cancel`, { method: "POST" }),
  catalogTables: (schema = "finlake_curated") =>
    request<string[]>(`/catalog/tables?schema=${encodeURIComponent(schema)}`),
  registerTable: (body: Record<string, unknown>) =>
    request<Record<string, unknown>>("/catalog/register", { method: "POST", body: JSON.stringify(body) }),
  queryTrino: (sql: string, limit = 100) =>
    request<QueryResponse>("/catalog/query", { method: "POST", body: JSON.stringify({ sql, limit }) }),
};
