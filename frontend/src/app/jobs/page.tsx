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
      <h1 className="text-2xl font-bold text-white">Storage Ingestion Jobs</h1>

      <div className="p-4 rounded-lg border border-slate-800 bg-slate-900/70 flex flex-wrap gap-3 items-end">
        <div>
          <label className="block text-xs text-slate-400 mb-1">Source</label>
          <select value={sourceId} onChange={(e) => setSourceId(e.target.value)} className="bg-slate-950 border border-slate-700 rounded px-2.5 py-1.5 text-sm">
            <option value="amad-mssql">amad-mssql (SQL Server)</option>
            <option value="amad-postgres">amad-postgres (PostgreSQL)</option>
          </select>
        </div>
        <div>
          <label className="block text-xs text-slate-400 mb-1">Format</label>
          <select value={format} onChange={(e) => setFormat(e.target.value)} className="bg-slate-950 border border-slate-700 rounded px-2.5 py-1.5 text-sm">
            <option value="PARQUET">PARQUET</option>
            <option value="CSV">CSV</option>
          </select>
        </div>
        <div>
          <label className="block text-xs text-slate-400 mb-1">Compression</label>
          <select value={compression} onChange={(e) => setCompression(e.target.value)} className="bg-slate-950 border border-slate-700 rounded px-2.5 py-1.5 text-sm">
            <option value="ZSTD">ZSTD</option>
            <option value="SNAPPY">SNAPPY</option>
            <option value="NONE">NONE</option>
          </select>
        </div>
        <div>
          <label className="block text-xs text-slate-400 mb-1">Max Rows (0 = full)</label>
          <input type="number" value={maxRows} onChange={(e) => setMaxRows(Number(e.target.value))} className="w-32 bg-slate-950 border border-slate-700 rounded px-2.5 py-1.5 text-sm" />
        </div>
        <button onClick={submit} className="px-4 py-1.5 rounded bg-emerald-600 hover:bg-emerald-500 text-sm font-medium text-white">
          Launch Job
        </button>
      </div>

      <div className="rounded-lg border border-slate-800 bg-slate-900/70 overflow-x-auto">
        <table className="w-full text-xs text-left">
          <thead className="text-slate-400 border-b border-slate-800">
            <tr>
              <th className="p-3">Job ID</th><th>Status</th><th>Rows</th><th>Files</th><th>Bytes</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((j) => (
              <tr key={j.jobId} className="border-b border-slate-800/60">
                <td className="p-3 font-mono text-slate-300">{j.jobId}</td>
                <td className="font-semibold">{j.status}</td>
                <td>{j.rowsProcessed.toLocaleString()}</td>
                <td>{j.filesWritten}</td>
                <td>{j.bytesWritten.toLocaleString()}</td>
                <td className="space-x-2">
                  {(j.status === "FAILED" || j.status === "SUCCEEDED" || j.status === "CANCELLED") && (
                    <button onClick={() => amadApi.retryJob(j.jobId).then(load)} className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700">Retry</button>
                  )}
                  {(j.status === "PENDING" || j.status === "RUNNING") && (
                    <button onClick={() => amadApi.cancelJob(j.jobId).then(load)} className="px-2 py-0.5 rounded bg-rose-800 hover:bg-rose-700">Cancel</button>
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
