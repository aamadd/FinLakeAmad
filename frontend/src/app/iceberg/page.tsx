"use client";

import { useCallback, useEffect, useState } from "react";
import {
  amadApi,
  type IcebergIngestResponse,
  type IcebergRegisterResponse,
  type IcebergSchemaEvolutionResponse,
  type IcebergSnapshot,
  type QueryResponse,
} from "@/lib/amadApi";

const EVOLUTION_TYPES = [
  "VARCHAR",
  "BIGINT",
  "INTEGER",
  "DOUBLE",
  "BOOLEAN",
  "DECIMAL(19,4)",
  "TIMESTAMP(6)",
];

export default function IcebergPage() {
  const [targetSchema, setTargetSchema] = useState("finlake_iceberg");
  const [targetTable, setTargetTable] = useState("amad_transactions");
  const [sourceSchema, setSourceSchema] = useState("finlake_curated");
  const [sourceTable, setSourceTable] = useState("amad_transactions");
  const [minRowId, setMinRowId] = useState("0");
  const [maxRowId, setMaxRowId] = useState("25000");

  const [snapshots, setSnapshots] = useState<IcebergSnapshot[]>([]);
  const [registerResult, setRegisterResult] = useState<IcebergRegisterResponse | null>(null);
  const [ingestResult, setIngestResult] = useState<IcebergIngestResponse | null>(null);
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string | null>(null);
  const [timeTravelResult, setTimeTravelResult] = useState<QueryResponse | null>(null);

  const [columnName, setColumnName] = useState("amad_audit_tag");
  const [columnType, setColumnType] = useState("VARCHAR");
  const [evolutionResult, setEvolutionResult] = useState<IcebergSchemaEvolutionResponse | null>(null);

  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadSnapshots = useCallback(async () => {
    setError(null);
    try {
      const list = await amadApi.icebergSnapshots(targetSchema, targetTable);
      setSnapshots(list);
    } catch (e) {
      setSnapshots([]);
      setError(e instanceof Error ? e.message : "Failed to load snapshots");
    }
  }, [targetSchema, targetTable]);

  useEffect(() => {
    loadSnapshots();
  }, [loadSnapshots]);

  const handleRegister = async () => {
    setBusy("register");
    setError(null);
    try {
      const res = await amadApi.registerIceberg({
        schema: targetSchema,
        table: targetTable,
        partitionedByEventDate: true,
      });
      setRegisterResult(res);
      await loadSnapshots();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Register failed");
    } finally {
      setBusy(null);
    }
  };

  const handleIngest = async () => {
    setBusy("ingest");
    setError(null);
    try {
      const res = await amadApi.ingestIceberg({
        sourceSchema,
        sourceTable,
        targetSchema,
        targetTable,
        minIngestRowId: minRowId.trim() === "" ? null : Number(minRowId),
        maxIngestRowId: maxRowId.trim() === "" ? null : Number(maxRowId),
      });
      setIngestResult(res);
      await loadSnapshots();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Ingest failed");
    } finally {
      setBusy(null);
    }
  };

  const handleTimeTravel = async (snapshotId: string) => {
    setBusy("timetravel");
    setSelectedSnapshotId(snapshotId);
    setError(null);
    try {
      const res = await amadApi.icebergTimeTravel({
        schema: targetSchema,
        table: targetTable,
        snapshotId,
        limit: 100,
      });
      setTimeTravelResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Time travel query failed");
    } finally {
      setBusy(null);
    }
  };

  const handleEvolveSchema = async () => {
    setBusy("evolve");
    setError(null);
    try {
      const res = await amadApi.evolveIcebergSchema({
        schema: targetSchema,
        table: targetTable,
        columnName,
        columnType,
      });
      setEvolutionResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Schema evolution failed");
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-neutral-900">Apache Iceberg (amad_iceberg)</h1>
          <p className="text-xs text-neutral-500 mt-1">
            Snapshots atomiques, Time Travel (<code className="font-mono">FOR VERSION AS OF</code>) et évolution de schéma sur MinIO (<code className="font-mono">s3a://amad-lake/iceberg/</code>).
          </p>
        </div>
        <button
          onClick={loadSnapshots}
          disabled={busy !== null}
          className="px-3 py-1.5 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium text-neutral-800 disabled:opacity-50"
        >
          Refresh Snapshots
        </button>
      </div>

      {error && (
        <div className="p-3 rounded-md border border-neutral-300 bg-neutral-50 text-xs text-neutral-800 font-mono">
          {error}
        </div>
      )}

      {/* Register & Ingest from Hive */}
      <section className="p-5 rounded-lg border border-neutral-300 bg-white space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-neutral-900">1. Register &amp; Ingest from Hive (finlake_curated)</h2>
          {registerResult && (
            <span className="text-[11px] font-mono text-neutral-600">
              {registerResult.catalog}.{registerResult.schema}.{registerResult.table} ({registerResult.rowCount.toLocaleString()} rows)
            </span>
          )}
        </div>

        <div className="grid grid-cols-2 md:grid-cols-6 gap-3 items-end">
          <div>
            <label className="block text-[11px] font-medium text-neutral-600 mb-1">Target Schema</label>
            <input
              value={targetSchema}
              onChange={(e) => setTargetSchema(e.target.value)}
              className="w-full border border-neutral-300 rounded-md px-2.5 py-1.5 text-xs bg-white"
            />
          </div>
          <div>
            <label className="block text-[11px] font-medium text-neutral-600 mb-1">Target Table</label>
            <input
              value={targetTable}
              onChange={(e) => setTargetTable(e.target.value)}
              className="w-full border border-neutral-300 rounded-md px-2.5 py-1.5 text-xs bg-white"
            />
          </div>
          <div>
            <label className="block text-[11px] font-medium text-neutral-600 mb-1">Source Schema</label>
            <input
              value={sourceSchema}
              onChange={(e) => setSourceSchema(e.target.value)}
              className="w-full border border-neutral-300 rounded-md px-2.5 py-1.5 text-xs bg-white"
            />
          </div>
          <div>
            <label className="block text-[11px] font-medium text-neutral-600 mb-1">Source Table</label>
            <input
              value={sourceTable}
              onChange={(e) => setSourceTable(e.target.value)}
              className="w-full border border-neutral-300 rounded-md px-2.5 py-1.5 text-xs bg-white"
            />
          </div>
          <div>
            <label className="block text-[11px] font-medium text-neutral-600 mb-1">minIngestRowId</label>
            <input
              type="number"
              value={minRowId}
              onChange={(e) => setMinRowId(e.target.value)}
              className="w-full border border-neutral-300 rounded-md px-2.5 py-1.5 text-xs bg-white"
            />
          </div>
          <div>
            <label className="block text-[11px] font-medium text-neutral-600 mb-1">maxIngestRowId</label>
            <input
              type="number"
              value={maxRowId}
              onChange={(e) => setMaxRowId(e.target.value)}
              className="w-full border border-neutral-300 rounded-md px-2.5 py-1.5 text-xs bg-white"
            />
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={handleRegister}
            disabled={busy !== null}
            className="px-3.5 py-1.5 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium text-neutral-900 disabled:opacity-50"
          >
            {busy === "register" ? "Registering..." : "Register Iceberg Table"}
          </button>
          <button
            onClick={handleIngest}
            disabled={busy !== null}
            className="px-3.5 py-1.5 rounded-md bg-[#27272a] hover:bg-neutral-800 text-xs font-medium text-white disabled:opacity-50"
          >
            {busy === "ingest" ? "Ingesting..." : "Ingest Batch"}
          </button>
          {ingestResult && (
            <span className="text-xs font-mono text-neutral-600">
              +{ingestResult.insertedRows.toLocaleString()} rows (total: {ingestResult.totalRows.toLocaleString()}, snapshot: {ingestResult.latestSnapshotId ?? "-"})
            </span>
          )}
        </div>
      </section>

      {/* Snapshots & Time Travel */}
      <section className="p-5 rounded-lg border border-neutral-300 bg-white space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-neutral-900">2. Snapshots ($snapshots) &amp; Time Travel</h2>
          <span className="text-[11px] text-neutral-500">Cliquez sur un snapshot pour exécuter FOR VERSION AS OF</span>
        </div>

        <div className="rounded-md border border-neutral-200 overflow-x-auto">
          <table className="w-full text-xs text-left">
            <thead className="bg-neutral-50 text-neutral-600 border-b border-neutral-200">
              <tr>
                <th className="p-2.5 font-medium">snapshotId</th>
                <th className="p-2.5 font-medium">committedAt</th>
                <th className="p-2.5 font-medium">operation</th>
                <th className="p-2.5 font-medium">summary</th>
                <th className="p-2.5 font-medium text-right">Time Travel</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {snapshots.length === 0 ? (
                <tr>
                  <td colSpan={5} className="p-4 text-center text-neutral-400">
                    Aucun snapshot trouvé pour {targetSchema}.{targetTable}.
                  </td>
                </tr>
              ) : (
                snapshots.map((s) => {
                  const selected = selectedSnapshotId === s.snapshotId;
                  return (
                    <tr
                      key={s.snapshotId}
                      onClick={() => handleTimeTravel(s.snapshotId)}
                      className={`cursor-pointer transition ${
                        selected ? "bg-neutral-100" : "hover:bg-neutral-50"
                      }`}
                    >
                      <td className="p-2.5 font-mono text-neutral-900">{s.snapshotId}</td>
                      <td className="p-2.5 font-mono text-neutral-600">{s.committedAt}</td>
                      <td className="p-2.5 font-medium text-neutral-800">{s.operation}</td>
                      <td className="p-2.5 font-mono text-[11px] text-neutral-500 max-w-md truncate" title={s.summary}>
                        {s.summary}
                      </td>
                      <td className="p-2.5 text-right">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleTimeTravel(s.snapshotId);
                          }}
                          className="px-2.5 py-1 rounded border border-neutral-300 bg-white hover:bg-neutral-100 text-[11px] font-medium text-neutral-800"
                        >
                          Query AS OF
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {timeTravelResult && (
          <div className="space-y-2 pt-2">
            <div className="flex items-center justify-between text-xs text-neutral-600">
              <span className="font-medium text-neutral-900">
                Time Travel — Snapshot <code className="font-mono">{selectedSnapshotId}</code>
              </span>
              <span className="font-mono">{timeTravelResult.durationMs} ms</span>
            </div>
            <div className="rounded-md border border-neutral-200 overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead className="bg-neutral-50 text-neutral-600 border-b border-neutral-200">
                  <tr>
                    {timeTravelResult.columns.map((c) => (
                      <th key={c} className="p-2.5 font-medium font-mono">{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {timeTravelResult.rows.map((row, idx) => (
                    <tr key={idx}>
                      {timeTravelResult.columns.map((c) => (
                        <td key={c} className="p-2.5 font-mono text-neutral-800">
                          {String(row[c] ?? "")}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </section>

      {/* Schema Evolution */}
      <section className="p-5 rounded-lg border border-neutral-300 bg-white space-y-4">
        <h2 className="text-sm font-semibold text-neutral-900">3. Schema Evolution (ADD COLUMN IF NOT EXISTS)</h2>
        <div className="flex flex-wrap gap-3 items-end">
          <div>
            <label className="block text-[11px] font-medium text-neutral-600 mb-1">Column Name</label>
            <input
              value={columnName}
              onChange={(e) => setColumnName(e.target.value)}
              className="w-52 border border-neutral-300 rounded-md px-2.5 py-1.5 text-xs bg-white font-mono"
            />
          </div>
          <div>
            <label className="block text-[11px] font-medium text-neutral-600 mb-1">Column Type</label>
            <select
              value={columnType}
              onChange={(e) => setColumnType(e.target.value)}
              className="border border-neutral-300 rounded-md px-2.5 py-1.5 text-xs bg-white font-mono"
            >
              {EVOLUTION_TYPES.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>
          <button
            onClick={handleEvolveSchema}
            disabled={busy !== null}
            className="px-3.5 py-1.5 rounded-md bg-[#27272a] hover:bg-neutral-800 text-xs font-medium text-white disabled:opacity-50"
          >
            {busy === "evolve" ? "Applying..." : "Add Column"}
          </button>
        </div>

        {evolutionResult && (
          <div className="p-3 rounded-md border border-neutral-200 bg-neutral-50 space-y-2">
            <div className="text-xs font-medium text-neutral-900">
              Colonne ajoutée : <code className="font-mono">{evolutionResult.addedColumn} {evolutionResult.columnType}</code>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {evolutionResult.columns.map((col) => (
                <span key={col} className="px-2 py-0.5 rounded border border-neutral-300 bg-white text-[11px] font-mono text-neutral-700">
                  {col}
                </span>
              ))}
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
