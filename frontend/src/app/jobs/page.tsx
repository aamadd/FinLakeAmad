"use client";

import { useEffect, useState } from "react";
import { amadApi, type JobRecord } from "@/lib/amadApi";

export default function JobsPage() {
  const [jobs, setJobs] = useState<JobRecord[]>([]);
  const [sourceId, setSourceId] = useState("amad-mssql");
  const [format, setFormat] = useState("PARQUET");
  const [compression, setCompression] = useState("ZSTD");
  const [maxRows, setMaxRows] = useState(20000);

  const load = () => {
    amadApi.jobs().then(setJobs).catch(() => {});
  };

  useEffect(() => {
    load();
    const id = setInterval(load, 2500);
    return () => clearInterval(id);
  }, []);

  const submit = async () => {
    await amadApi.submitJob({
      sourceId,
      schema: sourceId === "amad-mssql" ? "dbo" : "public",
      table: "amad_transactions",
      format,
      compression,
      batchSize: 10000,
      partitionBy: ["event_date"],
      destinationPrefix: "curated",
      maxRows,
    });
    load();
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-zinc-900">Storage Jobs</h1>
        <p className="text-sm text-zinc-500">Extraction asynchrone à mémoire bornée vers MinIO (`curated/` &amp; `manifests/`).</p>
      </div>

      <div className="p-5 rounded-xl border border-zinc-200 bg-white flex flex-wrap gap-3 items-end">
        <div>
          <label className="block text-xs font-medium text-zinc-600 mb-1">Source</label>
          <select value={sourceId} onChange={(e) => setSourceId(e.target.value)} className="border border-zinc-200 rounded-lg px-3 py-2 text-sm bg-white">
            <option value="amad-mssql">amad-mssql (SQL Server)</option>
            <option value="amad-postgres">amad-postgres (PostgreSQL)</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-zinc-600 mb-1">Format</label>
          <select value={format} onChange={(e) => setFormat(e.target.value)} className="border border-zinc-200 rounded-lg px-3 py-2 text-sm bg-white">
            <option value="PARQUET">PARQUET</option>
            <option value="CSV">CSV</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-zinc-600 mb-1">Compression</label>
          <select value={compression} onChange={(e) => setCompression(e.target.value)} className="border border-zinc-200 rounded-lg px-3 py-2 text-sm bg-white">
            <option value="ZSTD">ZSTD</option>
            <option value="SNAPPY">SNAPPY</option>
            <option value="NONE">NONE</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-zinc-600 mb-1">Max Rows (0 = full)</label>
          <input type="number" value={maxRows} onChange={(e) => setMaxRows(Number(e.target.value))} className="w-36 border border-zinc-200 rounded-lg px-3 py-2 text-sm" />
        </div>
        <button onClick={submit} className="px-4 py-2 rounded-lg bg-[#18181b] hover:bg-zinc-800 text-sm font-medium text-white">
          Lancer un job
        </button>
      </div>

      <div className="rounded-xl border border-zinc-200 bg-white overflow-x-auto">
        <table className="w-full text-xs text-left">
          <thead className="bg-zinc-50 text-zinc-500 border-b border-zinc-200">
            <tr>
              <th className="p-3">Job ID</th><th>Statut</th><th>Lignes</th><th>Fichiers</th><th>Octets</th><th>Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100">
            {jobs.map((j) => (
              <tr key={j.jobId}>
                <td className="p-3 font-mono text-zinc-700">{j.jobId}</td>
                <td className="font-semibold text-zinc-900">{j.status}</td>
                <td>{j.rowsProcessed.toLocaleString()}</td>
                <td>{j.filesWritten}</td>
                <td>{j.bytesWritten.toLocaleString()}</td>
                <td className="space-x-2">
                  {(j.status === "FAILED" || j.status === "SUCCEEDED" || j.status === "CANCELLED") && (
                    <button onClick={() => amadApi.retryJob(j.jobId).then(load)} className="px-2.5 py-1 rounded border border-zinc-200 hover:bg-zinc-50">Relancer</button>
                  )}
                  {(j.status === "PENDING" || j.status === "RUNNING") && (
                    <button onClick={() => amadApi.cancelJob(j.jobId).then(load)} className="px-2.5 py-1 rounded bg-rose-600 text-white hover:bg-rose-500">Annuler</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
