"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Activity, BarChart3, Box, Database, ExternalLink, Layers } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { amadApi } from "@/lib/amadApi";

const PRIMARY_NAV = [
  { name: "Data Sources", href: "/sources", icon: Database },
  { name: "Storage Jobs", href: "/jobs", icon: Activity },
  { name: "Trino Analytics", href: "/analytics", icon: BarChart3 },
  { name: "Platform Health", href: "/", icon: Layers },
];

export function AmadShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [status, setStatus] = useState<"UP" | "DEGRADED" | "CHECKING">("CHECKING");

  useEffect(() => {
    amadApi
      .health()
      .then((h) => setStatus(h.status === "UP" ? "UP" : "DEGRADED"))
      .catch(() => setStatus("DEGRADED"));
  }, [pathname]);

  const breadcrumb = () => {
    if (pathname === "/sources/new") {
      return (
        <div className="flex items-center gap-2 text-sm text-zinc-600">
          <Link href="/sources" className="hover:text-zinc-900">Data Sources</Link>
          <span className="text-zinc-400">›</span>
          <span className="font-medium text-zinc-900">Ajouter une connexion</span>
        </div>
      );
    }
    const match = PRIMARY_NAV.find((n) => n.href === pathname);
    return <span className="text-sm font-medium text-zinc-900">{match?.name ?? "Data Platform"}</span>;
  };

  return (
    <div className="min-h-screen bg-[#f8f9fa] flex font-sans text-zinc-900">
      <aside className="w-60 bg-white border-r border-zinc-200 flex flex-col fixed inset-y-0 left-0 z-30">
        <div className="h-16 flex items-center gap-3 px-6 border-b border-zinc-100">
          <div className="h-8 w-8 bg-[#18181b] rounded-lg flex items-center justify-center text-white">
            <Layers className="h-4 w-4" />
          </div>
          <div>
            <div className="font-bold text-sm tracking-tight text-zinc-900">Data Platform</div>
            <div className="text-[11px] text-zinc-400">FinLakeAmad</div>
          </div>
        </div>

        <nav className="flex-1 py-4 px-3 space-y-1 overflow-y-auto">
          {PRIMARY_NAV.map(({ name, href, icon: Icon }) => {
            const active = pathname === href || (href === "/sources" && pathname.startsWith("/sources"));
            return (
              <Link
                key={href}
                href={href}
                className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-all ${
                  active
                    ? "bg-white border border-zinc-200 text-zinc-900 shadow-sm font-semibold"
                    : "text-zinc-600 hover:text-zinc-900 hover:bg-zinc-100/80 font-medium"
                }`}
              >
                <Icon className={`h-4 w-4 ${active ? "text-zinc-900" : "text-zinc-500"}`} />
                <span>{name}</span>
              </Link>
            );
          })}

          <div className="pt-6 pb-2 px-3">
            <p className="text-[11px] font-semibold text-zinc-400 uppercase tracking-wider">Infrastructure</p>
          </div>
          <a
            href="http://localhost:9101"
            target="_blank"
            rel="noreferrer"
            className="flex items-center justify-between px-3 py-2 rounded-lg text-sm font-medium text-zinc-600 hover:text-zinc-900 hover:bg-zinc-100/80"
          >
            <span className="flex items-center gap-3"><Box className="h-4 w-4 text-zinc-500" />MinIO Storage</span>
            <ExternalLink className="h-3 w-3 text-zinc-400" />
          </a>
        </nav>
      </aside>

      <div className="flex-1 ml-60 flex flex-col min-w-0">
        <header className="h-16 bg-white border-b border-zinc-200 px-8 flex items-center justify-between sticky top-0 z-20">
          {breadcrumb()}
          <div className="flex items-center gap-2 px-3 py-1 rounded-full text-xs font-medium border border-zinc-200 bg-zinc-50">
            <span
              className={`h-2 w-2 rounded-full ${
                status === "UP" ? "bg-emerald-500" : status === "CHECKING" ? "bg-amber-500" : "bg-rose-500"
              }`}
            />
            <span className="text-zinc-700">{status === "UP" ? "Services Online" : status === "CHECKING" ? "Checking..." : "Degraded"}</span>
          </div>
        </header>
        <main className="flex-1 p-8 max-w-6xl w-full mx-auto">{children}</main>
      </div>
    </div>
  );
}
