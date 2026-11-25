"use client";

import { useEffect, useState } from "react";
import { amadApi, type HealthResponse } from "@/lib/amadApi";

export default function HomePage() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = () => {
    setErr(null);
    amadApi.health().then(setHealth).catch((e) => setErr(String(e)));
  };

  useEffect(load, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-zinc-900">FinLakeAmad Control Plane</h1>
          <p className="text-sm text-zinc-500">
            SQL Server &amp; PostgreSQL → Keyset Ingestion → Parquet/CSV (MinIO) → Hive Metastore → Trino
          </p>
        </div>
        <button onClick={load} className="px-3 py-1.5 rounded-lg border border-zinc-200 bg-white hover:bg-zinc-50 text-sm font-medium text-zinc-700">
          Actualiser
        </button>
      </div>

      {err && <div className="p-4 rounded-lg border border-rose-200 bg-rose-50 text-rose-700 text-sm">{err}</div>}

      {health && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {Object.entries(health.components).map(([name, state]) => (
            <div key={name} className="p-5 rounded-xl border border-zinc-200 bg-white">
              <div className="text-xs font-semibold uppercase tracking-wider text-zinc-400">{name}</div>
              <div className={`mt-2 text-lg font-bold ${state === "UP" ? "text-emerald-600" : "text-rose-600"}`}>
                {state}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
