"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Check, ChevronDown, ChevronRight, Database, Eye, EyeOff, Info, Loader2, X, Zap } from "lucide-react";
import { amadApi, type ProbeResponse } from "@/lib/amadApi";

export default function NewSourcePage() {
  const router = useRouter();
  const [engine, setEngine] = useState<"MSSQL" | "POSTGRES">("MSSQL");
  const [id, setId] = useState("amad-custom-source");
  const [name, setName] = useState("erp-production");
  const [host, setHost] = useState("amad-mssql");
  const [port, setPort] = useState(1433);
  const [database, setDatabase] = useState("amad_source");
  const [username, setUsername] = useState("sa");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [tlsEnabled, setTlsEnabled] = useState(false);
  const [trustCert, setTrustCert] = useState(true);
  const [timeoutSec, setTimeoutSec] = useState(30);
  const [advancedOpen, setAdvancedOpen] = useState(false);

  const [testing, setTesting] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [probe, setProbe] = useState<ProbeResponse | null>(null);
  const [testedAt, setTestedAt] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveErr, setSaveErr] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!testing) return;
    const t0 = Date.now();
    const timer = setInterval(() => setElapsedSec(Number(((Date.now() - t0) / 1000).toFixed(1))), 100);
    return () => clearInterval(timer);
  }, [testing]);

  const applyPreset = (kind: "MSSQL" | "POSTGRES") => {
    setProbe(null);
    if (kind === "MSSQL") {
      setEngine("MSSQL");
      setId("amad-mssql-demo");
      setName("amad-mssql-demo");
      setHost("amad-mssql");
      setPort(1433);
      setDatabase("amad_source");
      setUsername("sa");
    } else {
      setEngine("POSTGRES");
      setId("amad-pg-demo");
      setName("amad-postgres-demo");
      setHost("amad-postgres");
      setPort(5432);
      setDatabase("amad_source");
      setUsername("amad_source");
    }
  };

  const runProbe = async () => {
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;
    setTesting(true);
    setElapsedSec(0);
    setProbe(null);
    try {
      const res = await amadApi.probeSource(
        { engine, host, port: Number(port), database, username, password, tlsEnabled },
        ctrl.signal
      );
      setProbe(res);
      setTestedAt(new Date().toLocaleString("fr-FR", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }));
    } catch (err) {
      if ((err as Error).name !== "AbortError") {
        setProbe({
          connected: false,
          engine,
          serverVersion: null,
          steps: [{ step: 1, name: "Joindre le serveur", status: "FAILED", durationMs: 0, detail: String(err) }],
          tables: [],
          errorHint: "Le serveur n'a pas pu être joint.",
        });
      }
    } finally {
      setTesting(false);
    }
  };

  const saveSource = async () => {
    setSaving(true);
    setSaveErr(null);
    try {
      await amadApi.createSource({ id, name, engine, host, port: Number(port), database, username, password, tlsEnabled });
      router.push("/sources");
    } catch (err) {
      setSaveErr(String(err));
    } finally {
      setSaving(false);
    }
  };

  const totalMs = probe?.steps.reduce((acc, s) => acc + s.durationMs, 0) ?? 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold tracking-tight text-neutral-900">
            Ajouter une connexion {engine === "MSSQL" ? "SQL Server" : "PostgreSQL"}
          </h1>
          <p className="text-xs text-neutral-500">
            Saisissez les informations dont la plateforme a besoin pour joindre votre base. Testez la connexion avant de l&apos;enregistrer pour vérifier qu&apos;elle fonctionne.
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => applyPreset("MSSQL")} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium">
            <Zap className="h-3 w-3" /> Démo SQL Server
          </button>
          <button onClick={() => applyPreset("POSTGRES")} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium">
            <Zap className="h-3 w-3" /> Démo PostgreSQL
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left: Wireframe 4-Section Card */}
        <div className="lg:col-span-8 border border-neutral-300 rounded-lg bg-white p-6 space-y-6">
          {probe && !probe.connected && (
            <div className="p-3.5 rounded-md border border-neutral-300 bg-white flex items-start gap-2.5 text-xs text-neutral-800">
              <Info className="h-4 w-4 text-neutral-700 shrink-0 mt-0.5" />
              <span>
                <strong>Le test de connexion a échoué.</strong> Vérifiez les champs signalés, puis testez à nouveau. Vos saisies sont conservées.
              </span>
            </div>
          )}

          {/* 1. Connexion */}
          <section className="space-y-3">
            <div>
              <h2 className="text-xs font-semibold text-neutral-900">Connexion</h2>
              <p className="text-[11px] text-neutral-500">La façon dont cette source apparaît dans la plateforme.</p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-[11px] font-medium text-neutral-700 mb-1">Type de base de données</label>
                <div className="relative flex items-center">
                  <Database className="h-3.5 w-3.5 text-neutral-500 absolute left-3 pointer-events-none" />
                  <select
                    disabled={testing}
                    value={engine}
                    onChange={(e) => {
                      const next = e.target.value as "MSSQL" | "POSTGRES";
                      setEngine(next);
                      setPort(next === "MSSQL" ? 1433 : 5432);
                    }}
                    className="w-full pl-8 pr-3 py-2 border border-neutral-300 rounded-md text-xs bg-white disabled:bg-neutral-100"
                  >
                    <option value="MSSQL">Microsoft SQL Server</option>
                    <option value="POSTGRES">PostgreSQL</option>
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-[11px] font-medium text-neutral-700 mb-1">Nom de la connexion</label>
                <input
                  disabled={testing}
                  value={name}
                  onChange={(e) => {
                    setName(e.target.value);
                    setId(e.target.value.toLowerCase().replace(/[^a-z0-9-_]/g, "-"));
                  }}
                  className="w-full px-3 py-2 border border-neutral-300 rounded-md text-xs disabled:bg-neutral-100"
                />
                <p className="text-[10px] text-neutral-400 mt-1">Nom unique, affiché dans les listes et les jobs.</p>
              </div>
            </div>
          </section>

          {/* 2. Serveur */}
          <section className="space-y-3 pt-5 border-t border-neutral-200">
            <div>
              <h2 className="text-xs font-semibold text-neutral-900">Serveur</h2>
              <p className="text-[11px] text-neutral-500">L&apos;emplacement où la base est hébergée.</p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
              <div className="sm:col-span-3">
                <label className="block text-[11px] font-medium text-neutral-700 mb-1">Serveur / Hôte</label>
                <input
                  disabled={testing}
                  value={host}
                  onChange={(e) => setHost(e.target.value)}
                  className="w-full px-3 py-2 border border-neutral-300 rounded-md text-xs font-mono disabled:bg-neutral-100"
                />
                <p className="text-[10px] text-neutral-400 mt-1">Nom d&apos;hôte ou adresse IP.</p>
              </div>
              <div>
                <label className="block text-[11px] font-medium text-neutral-700 mb-1">Port</label>
                <input
                  disabled={testing}
                  type="number"
                  value={port}
                  onChange={(e) => setPort(Number(e.target.value))}
                  className="w-full px-3 py-2 border border-neutral-300 rounded-md text-xs font-mono disabled:bg-neutral-100"
                />
                <p className="text-[10px] text-neutral-400 mt-1">{engine === "MSSQL" ? "1433" : "5432"} par défaut.</p>
              </div>
            </div>
            <div>
              <label className="block text-[11px] font-medium text-neutral-700 mb-1">Nom de la base de données</label>
              <input
                disabled={testing}
                value={database}
                onChange={(e) => setDatabase(e.target.value)}
                className="w-full px-3 py-2 border border-neutral-300 rounded-md text-xs font-mono disabled:bg-neutral-100"
              />
            </div>
          </section>

          {/* 3. Authentification */}
          <section className="space-y-3 pt-5 border-t border-neutral-200">
            <div>
              <h2 className="text-xs font-semibold text-neutral-900">Authentification</h2>
              <p className="text-[11px] text-neutral-500">Le compte utilisé par la plateforme pour lire la base.</p>
            </div>
            <div>
              <label className="block text-[11px] font-medium text-neutral-700 mb-1">Méthode d&apos;authentification</label>
              <select disabled={testing} className="w-full px-3 py-2 border border-neutral-300 rounded-md text-xs bg-white disabled:bg-neutral-100">
                <option>{engine === "MSSQL" ? "Authentification SQL Server" : "Authentification PostgreSQL"}</option>
              </select>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-[11px] font-medium text-neutral-700 mb-1">Nom d&apos;utilisateur</label>
                <input
                  disabled={testing}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className={`w-full px-3 py-2 border rounded-md text-xs font-mono disabled:bg-neutral-100 ${
                    probe && !probe.connected ? "border-neutral-700" : "border-neutral-300"
                  }`}
                />
                {probe && !probe.connected && (
                  <p className="text-[10px] text-neutral-600 mt-1 flex items-center gap-1"><Info className="h-3 w-3" /> Vérifiez le nom d&apos;utilisateur.</p>
                )}
              </div>
              <div>
                <label className="block text-[11px] font-medium text-neutral-700 mb-1">Mot de passe</label>
                <div className="relative">
                  <input
                    disabled={testing}
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••••••"
                    className={`w-full pl-3 pr-9 py-2 border rounded-md text-xs disabled:bg-neutral-100 ${
                      probe && !probe.connected ? "border-neutral-700" : "border-neutral-300"
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-700"
                  >
                    {showPassword ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                  </button>
                </div>
                {probe && !probe.connected && (
                  <p className="text-[10px] text-neutral-600 mt-1 flex items-center gap-1"><Info className="h-3 w-3" /> Vérifiez le mot de passe.</p>
                )}
              </div>
            </div>
          </section>

          {/* 4. Paramètres avancés (collapsible) */}
          <section className="pt-5 border-t border-neutral-200 space-y-3">
            <button
              type="button"
              onClick={() => setAdvancedOpen((v) => !v)}
              className="w-full flex items-center justify-between text-left"
            >
              <div>
                <div className="text-xs font-semibold text-neutral-900">
                  Paramètres avancés <span className="font-normal text-neutral-400">(facultatif)</span>
                </div>
                <div className="text-[11px] text-neutral-500">Chiffrement et délai de connexion. Les valeurs par défaut conviennent à la plupart des serveurs.</div>
              </div>
              {advancedOpen ? <ChevronDown className="h-4 w-4 text-neutral-500" /> : <ChevronRight className="h-4 w-4 text-neutral-500" />}
            </button>

            {advancedOpen && (
              <div className="space-y-3 pt-2">
                <label className="flex items-start gap-2.5 text-xs">
                  <input type="checkbox" checked={tlsEnabled} onChange={(e) => setTlsEnabled(e.target.checked)} className="mt-0.5 accent-neutral-800" />
                  <span>
                    <span className="font-medium text-neutral-900 block">Chiffrer la connexion</span>
                    <span className="text-[11px] text-neutral-500">Chiffre les données échangées entre la plateforme et le serveur.</span>
                  </span>
                </label>
                <label className="flex items-start gap-2.5 text-xs">
                  <input type="checkbox" checked={trustCert} onChange={(e) => setTrustCert(e.target.checked)} className="mt-0.5 accent-neutral-800" />
                  <span>
                    <span className="font-medium text-neutral-900 block">Faire confiance au certificat du serveur</span>
                    <span className="text-[11px] text-neutral-500">Ignore la validation du certificat. À réserver aux serveurs dotés d&apos;un certificat auto-signé.</span>
                  </span>
                </label>
                <div>
                  <label className="block text-[11px] font-medium text-neutral-700 mb-1">Délai de connexion (secondes)</label>
                  <input type="number" value={timeoutSec} onChange={(e) => setTimeoutSec(Number(e.target.value))} className="w-40 px-3 py-1.5 border border-neutral-300 rounded-md text-xs font-mono" />
                </div>
              </div>
            )}
          </section>

          {saveErr && <div className="p-3 rounded-md border border-neutral-400 bg-neutral-50 text-xs text-neutral-800">{saveErr}</div>}

          <div className="pt-5 border-t border-neutral-200 flex flex-wrap items-center justify-between gap-3">
            <Link href="/sources" className="text-xs font-medium text-neutral-600 hover:text-neutral-900">Annuler</Link>
            <div className="flex items-center gap-3">
              <span className="text-[11px] text-neutral-500">
                {testing
                  ? "Les champs sont verrouillés pendant le test"
                  : !probe
                  ? "Pas encore testée"
                  : probe.connected
                  ? "Testée avec succès à l'instant"
                  : "Le dernier test a échoué"}
              </span>
              <button
                onClick={runProbe}
                disabled={testing}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium text-neutral-800 disabled:opacity-50"
              >
                {testing ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Zap className="h-3.5 w-3.5" />}
                {testing ? "Test en cours..." : "Tester la connexion"}
              </button>
              <button
                onClick={saveSource}
                disabled={saving || testing}
                className="px-4 py-2 rounded-md bg-[#27272a] hover:bg-neutral-900 text-white text-xs font-medium disabled:opacity-50"
              >
                {saving ? "Enregistrement..." : "Enregistrer la connexion"}
              </button>
            </div>
          </div>
        </div>

        {/* Right: Wireframe Testeur Card (Pages 2-5) */}
        <div className="lg:col-span-4 border border-neutral-300 rounded-lg bg-white p-5 space-y-4">
          <div className="text-xs font-semibold text-neutral-900">Test de connexion</div>

          {!testing && !probe && (
            <>
              <div className="border border-dashed border-neutral-300 rounded-md py-8 px-4 text-center space-y-2 bg-neutral-50/40">
                <Zap className="h-4 w-4 text-neutral-500 mx-auto" />
                <div className="text-xs font-semibold text-neutral-900">Pas encore testée</div>
                <p className="text-[11px] text-neutral-500 leading-relaxed">
                  Lancez un test pour vérifier que la plateforme peut joindre ce serveur et s&apos;y authentifier avec ces identifiants.
                </p>
              </div>
              <p className="text-[11px] text-neutral-500 leading-relaxed">
                Le test se déroule en quatre étapes : joindre le serveur, sécuriser la connexion, s&apos;authentifier, puis ouvrir la base. Rien n&apos;est enregistré tant que vous n&apos;avez pas choisi Enregistrer la connexion.
              </p>
            </>
          )}

          {testing && (
            <div className="space-y-4">
              <div className="p-3 rounded-md bg-neutral-100 flex items-center gap-2.5 text-xs">
                <Loader2 className="h-4 w-4 animate-spin text-neutral-700 shrink-0" />
                <div>
                  <div className="font-semibold text-neutral-900">Test de la connexion en cours...</div>
                  <div className="text-[11px] text-neutral-500">{elapsedSec.toFixed(1)} s écoulées</div>
                </div>
              </div>
              <button
                onClick={() => abortRef.current?.abort()}
                className="px-3 py-1.5 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium text-neutral-800"
              >
                Annuler le test
              </button>
            </div>
          )}

          {probe && (
            <div className="space-y-4">
              {probe.connected ? (
                <div className="p-3 rounded-md bg-neutral-100 flex items-start gap-2.5 text-xs">
                  <span className="h-5 w-5 rounded-full bg-[#27272a] text-white flex items-center justify-center shrink-0 mt-0.5">
                    <Check className="h-3 w-3" />
                  </span>
                  <div>
                    <div className="font-semibold text-neutral-900">Connexion réussie</div>
                    <div className="text-[11px] text-neutral-600">La plateforme a joint le serveur, s&apos;est authentifiée et a ouvert {database}.</div>
                  </div>
                </div>
              ) : (
                <div className="p-3 rounded-md bg-neutral-100 flex items-start gap-2.5 text-xs">
                  <span className="h-5 w-5 rounded-full bg-[#27272a] text-white flex items-center justify-center shrink-0 mt-0.5">
                    <X className="h-3 w-3" />
                  </span>
                  <div>
                    <div className="font-semibold text-neutral-900">Échec de la connexion</div>
                    <div className="text-[11px] text-neutral-600">{probe.errorHint}</div>
                  </div>
                </div>
              )}

              <div className="divide-y divide-neutral-200 border-t border-b border-neutral-200">
                {probe.steps.map((st) => (
                  <div key={st.step} className="py-2.5 flex items-start justify-between gap-2 text-xs">
                    <div className="flex items-start gap-2">
                      {st.status === "PASSED" ? (
                        <Check className="h-3.5 w-3.5 text-neutral-700 mt-0.5 shrink-0" />
                      ) : (
                        <X className="h-3.5 w-3.5 text-neutral-900 mt-0.5 shrink-0" />
                      )}
                      <div>
                        <div className="font-medium text-neutral-900">{st.name}</div>
                        <div className="text-[11px] text-neutral-500">{st.detail}</div>
                      </div>
                    </div>
                    <span className="text-[11px] font-medium text-neutral-700 shrink-0">
                      {st.status === "PASSED" ? "Réussi" : "Échec"}
                    </span>
                  </div>
                ))}
              </div>

              {probe.connected ? (
                <div className="space-y-2 text-xs">
                  <div className="flex justify-between"><span className="text-neutral-500">Version du serveur</span><span className="font-semibold text-neutral-900 text-right">{probe.serverVersion}</span></div>
                  <div className="flex justify-between"><span className="text-neutral-500">Temps de réponse</span><span className="font-semibold text-neutral-900">{totalMs} ms</span></div>
                  {testedAt && <div className="flex justify-between"><span className="text-neutral-500">Testée le</span><span className="font-semibold text-neutral-900">{testedAt}</span></div>}
                  {probe.tables.length > 0 && (
                    <div className="pt-2 border-t border-neutral-200 space-y-1">
                      <div className="text-[11px] font-semibold text-neutral-700">Tables détectées</div>
                      {probe.tables.map((t) => (
                        <div key={`${t.schema}.${t.name}`} className="text-[11px] font-mono text-neutral-600 flex justify-between">
                          <span>{t.schema}.{t.name}</span>
                          <span>{t.estimatedRows.toLocaleString()}</span>
                        </div>
                      ))}
                    </div>
                  )}
                  <p className="text-[11px] text-neutral-500 pt-1">Enregistrez la connexion pour l&apos;ajouter à Data Sources.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="text-xs font-semibold text-neutral-900">À vérifier</div>
                  <ul className="list-disc pl-4 space-y-1 text-[11px] text-neutral-600">
                    <li>Le nom d&apos;utilisateur et le mot de passe sont saisis correctement.</li>
                    <li>Le compte est actif et n&apos;est pas verrouillé sur le serveur.</li>
                    <li>Le conteneur cible est démarré sur le réseau Docker.</li>
                  </ul>
                  {probe.errorHint && (
                    <div className="p-2.5 rounded bg-neutral-100 font-mono text-[11px] text-neutral-700">
                      {probe.errorHint}
                    </div>
                  )}
                  <button
                    onClick={runProbe}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-neutral-300 bg-white hover:bg-neutral-50 text-xs font-medium text-neutral-800"
                  >
                    <Zap className="h-3 w-3" /> Tester à nouveau
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
